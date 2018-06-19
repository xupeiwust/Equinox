/*
 * Copyright 2018 Murat Artim (muratartim@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package equinox.task;

import java.io.BufferedWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import equinox.Equinox;
import equinox.data.fileType.AircraftModel;
import equinox.plugin.FileType;
import equinox.task.InternalEquinoxTask.FileSharingTask;
import equinox.task.InternalEquinoxTask.LongRunningTask;
import equinox.utility.Utility;
import equinoxServer.remote.data.SharedFileInfo;
import equinoxServer.remote.utility.Permission;

/**
 * Class for share A/C model file task.
 *
 * @author Murat Artim
 * @date Jul 17, 2015
 * @time 3:16:05 PM
 */
public class ShareAircraftModelFile extends TemporaryFileCreatingTask<Void> implements LongRunningTask, FileSharingTask {

	/** File item to save. */
	private final AircraftModel model_;

	/** Type of core spectrum file to share. */
	private final FileType type_;

	/** Recipients. */
	private final ArrayList<String> recipients_;

	/**
	 * Creates share aircraft model task.
	 *
	 * @param file
	 *            A/C model to share.
	 * @param type
	 *            Type of A/C model file to share.
	 * @param recipients
	 *            Recipients.
	 */
	public ShareAircraftModelFile(AircraftModel file, FileType type, ArrayList<String> recipients) {
		model_ = file;
		type_ = type;
		recipients_ = recipients;
	}

	@Override
	public boolean canBeCancelled() {
		return false;
	}

	@Override
	public String getTaskTitle() {
		return "Share A/C model file";
	}

	@Override
	protected Void call() throws Exception {

		// check permission
		checkPermission(Permission.SHARE_FILE);

		// update progress info
		updateTitle("Sharing A/C model file...");

		// save spectrum to temporary file
		Path path = saveModel();

		// upload file to filer
		shareFile(path, recipients_, SharedFileInfo.FILE);
		return null;
	}

	/**
	 * Saves A/C model to temporary file.
	 *
	 * @return Path to temporary file.
	 * @throws Exception
	 *             If exception occurs during process.
	 */
	private Path saveModel() throws Exception {

		// update info
		updateMessage("Saving to temporary file...");

		// create output path
		Path file = null, zipFile = null;

		// get database connection
		try (Connection connection = Equinox.DBC_POOL.getConnection()) {

			// create statement
			try (Statement statement = connection.createStatement()) {

				// save model files to temporary file
				if (type_.equals(FileType.F06) || type_.equals(FileType.F07)) {
					file = extractModelFiles(statement);
				}
				else if (type_.equals(FileType.GRP)) {

					// save groups to file
					file = saveGroupsFile(statement, connection);

					// no groups found
					if (file == null)
						throw new Exception("No element groups found to share in " + model_.getName());
				}

				// zip files
				if (file != null) {
					zipFile = getWorkingDirectory().resolve(FileType.getNameWithoutExtension(file) + FileType.ZIP);
					Utility.zipFile(file, zipFile.toFile(), this);
				}
			}
		}

		// return zip file
		return zipFile;
	}

	/**
	 * Extracts model files (F06 and F07) to working directory.
	 *
	 * @param statement
	 *            Database statement.
	 * @return List containing paths to extracted model files.
	 * @throws Exception
	 *             If exception occurs during process.
	 */
	private Path extractModelFiles(Statement statement) throws Exception {

		// update progress info
		updateMessage("Extracting model file...");

		// initialize output files
		Path file = null;

		// execute query
		try (ResultSet resultSet = statement.executeQuery("select data from ac_models where model_id = " + model_.getID())) {
			if (resultSet.next()) {

				// get blob
				Blob blob = resultSet.getBlob("data");

				// extract file
				Path zipFile = getWorkingDirectory().resolve("modelFiles" + FileType.ZIP.getExtension());
				Files.copy(blob.getBinaryStream(), zipFile, StandardCopyOption.REPLACE_EXISTING);
				file = Utility.extractFileFromZIP(zipFile, this, type_, null);

				// free blob
				blob.free();
			}
		}

		// return output file
		return file;
	}

	/**
	 * Saves element groups file to working directory.
	 *
	 * @param statement
	 *            Database statement.
	 * @param connection
	 *            Database connection.
	 * @return Path to saved element groups file, or null if there are no element groups.
	 * @throws Exception
	 *             If exception occurs during process.
	 */
	private Path saveGroupsFile(Statement statement, Connection connection) throws Exception {

		// check if any group exist
		boolean exists = false;
		DatabaseMetaData dbmtadta = connection.getMetaData();
		try (ResultSet resultSet = dbmtadta.getTables(null, "AURORA", "ELEMENT_GROUPS_" + model_.getID(), null)) {
			while (resultSet.next()) {
				exists = true;
				break;
			}
		}

		// no groups
		if (!exists)
			return null;

		// update progress info
		updateMessage("Saving element groups file...");

		// initialize output file
		Path output = getWorkingDirectory().resolve("elementGroups" + FileType.GRP.getExtension());

		// create output file writer
		try (BufferedWriter writer = Files.newBufferedWriter(output, Charset.defaultCharset())) {

			// write groups file header
			writeGroupsFileHeader(writer);

			// prepare statement EIDs of each group
			String sql = "select eid from element_groups_" + model_.getID() + " where group_id = ? order by eid";
			try (PreparedStatement getEIDs = connection.prepareStatement(sql)) {

				// create query to get element group names
				sql = "select group_id, name from element_group_names_" + model_.getID() + " order by name";
				try (ResultSet groups = statement.executeQuery(sql)) {

					// loop over groups
					while (groups.next()) {

						// get group name
						String name = groups.getString("name");

						// write group start
						writer.write("Group" + "\t" + name);
						writer.newLine();

						// get EIDs
						getEIDs.setInt(1, groups.getInt("group_id"));
						try (ResultSet eids = getEIDs.executeQuery()) {

							// loop over EIDs
							while (eids.next()) {
								writer.write(Integer.toString(eids.getInt("eid")));
								writer.newLine();
							}
						}

						// write group end
						writer.write("End");
						writer.newLine();
						writer.newLine();
					}
				}
			}
		}

		// return output file
		return output;
	}

	/**
	 * Writes out element groups file header.
	 *
	 * @param writer
	 *            File writer.
	 * @throws Exception
	 *             If exception occurs during process.
	 */
	private static void writeGroupsFileHeader(BufferedWriter writer) throws Exception {
		writer.write("# Aircraft Model Element Groups File Generated by Equinox Version " + Equinox.VERSION.toString() + ", Date: " + new SimpleDateFormat("dd/MM/yyyy").format(new Date()));
		writer.newLine();
		writer.write("# This file contains element groups for Equinox A/C models.");
		writer.newLine();
		writer.write("# Empty lines and lines starting with # are ignored. All columns are tab separated.");
		writer.newLine();
		writer.write("# If Equinox cannot find any elements for a given group, the group will not be created and a warning message will be issued at the end of process.");
		writer.newLine();
		writer.write("# There are 2 possible formats. They can coexist within the same file.");
		writer.newLine();
		writer.write("# 1) Starts with the word 'Interval':");
		writer.newLine();
		writer.write("#    Interval<Tab>GroupName<Tab>startEID<Tab>endEID");
		writer.newLine();
		writer.write("# 2) Starts with the word 'Group':");
		writer.newLine();
		writer.write("#    Group<Tab>GroupName");
		writer.newLine();
		writer.write("#    EID-1");
		writer.newLine();
		writer.write("#    EID-2");
		writer.newLine();
		writer.write("#    ...");
		writer.newLine();
		writer.write("#    End");
		writer.newLine();
		writer.newLine();
	}
}
