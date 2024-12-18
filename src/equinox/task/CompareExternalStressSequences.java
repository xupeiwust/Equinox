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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

import equinox.Equinox;
import equinox.controller.StatisticsViewPanel;
import equinox.controller.ViewPanel;
import equinox.data.Pair;
import equinox.data.StatisticsPlotAttributes;
import equinox.data.fileType.ExternalStressSequence;
import equinox.data.input.ExternalStressSequenceComparisonInput;
import equinox.data.input.ExternalStressSequenceComparisonInput.ExternalComparisonCriteria;
import equinox.serverUtilities.Permission;
import equinox.task.InternalEquinoxTask.ShortRunningTask;
import equinox.task.automation.MultipleInputTask;
import equinox.task.automation.AutomaticTask;
import equinox.task.automation.AutomaticTaskOwner;

/**
 * Class for compare external stress sequences task.
 *
 * @author Murat Artim
 * @date Apr 7, 2016
 * @time 10:30:04 AM
 */
public class CompareExternalStressSequences extends InternalEquinoxTask<CategoryDataset> implements ShortRunningTask, MultipleInputTask<ExternalStressSequence>, AutomaticTaskOwner<Pair<CategoryDataset, StatisticsPlotAttributes>> {

	/** Comparison input. */
	private final ExternalStressSequenceComparisonInput input_;

	/** Chart labels. */
	private String xAxisLabel_, yAxisLabel_;

	/** Automatic inputs. */
	private final List<ExternalStressSequence> stressSequences_;

	/** Input threshold. Once the threshold is reached, this task will be executed. */
	private volatile int inputThreshold_ = 0;

	/** Automatic tasks. */
	private HashMap<String, AutomaticTask<Pair<CategoryDataset, StatisticsPlotAttributes>>> automaticTasks_ = null;

	/** Automatic task execution mode. */
	private boolean executeAutomaticTasksInParallel_ = true;

	/**
	 * Creates compare stress sequences task.
	 *
	 * @param input
	 *            Comparison input.
	 */
	public CompareExternalStressSequences(ExternalStressSequenceComparisonInput input) {
		input_ = input;
		stressSequences_ = Collections.synchronizedList(new ArrayList<>());
	}

	/**
	 * Adds stress sequence.
	 *
	 * @param stressSequence
	 *            Stress sequence to add.
	 */
	public void addStressSequence(ExternalStressSequence stressSequence) {
		stressSequences_.add(stressSequence);
	}

	@Override
	public void setAutomaticTaskExecutionMode(boolean isParallel) {
		executeAutomaticTasksInParallel_ = isParallel;
	}

	@Override
	public void addAutomaticTask(String taskID, AutomaticTask<Pair<CategoryDataset, StatisticsPlotAttributes>> task) {
		if (automaticTasks_ == null) {
			automaticTasks_ = new HashMap<>();
		}
		automaticTasks_.put(taskID, task);
	}

	@Override
	public HashMap<String, AutomaticTask<Pair<CategoryDataset, StatisticsPlotAttributes>>> getAutomaticTasks() {
		return automaticTasks_;
	}

	@Override
	synchronized public void setInputThreshold(int inputThreshold) {
		inputThreshold_ = inputThreshold;
	}

	@Override
	synchronized public void addAutomaticInput(AutomaticTaskOwner<ExternalStressSequence> task, ExternalStressSequence input, boolean executeInParallel) {
		automaticInputAdded(task, input, executeInParallel, stressSequences_, inputThreshold_);
	}

	@Override
	synchronized public void inputFailed(AutomaticTaskOwner<ExternalStressSequence> task, boolean executeInParallel) {
		inputThreshold_ = automaticInputFailed(task, executeInParallel, stressSequences_, inputThreshold_);
	}

	@Override
	public String getTaskTitle() {
		return "Compare external stress sequences";
	}

	@Override
	public boolean canBeCancelled() {
		return false;
	}

	@Override
	protected CategoryDataset call() throws Exception {

		// check permission
		checkPermission(Permission.PLOT_STRESS_SEQUENCE_COMPARISON);

		// update progress info
		updateTitle("Comparing stress sequences...");

		// create data set
		DefaultCategoryDataset dataset = new DefaultCategoryDataset();

		// get database connection
		try (Connection connection = Equinox.DBC_POOL.getConnection()) {

			// create statement
			try (Statement statement = connection.createStatement()) {

				// get criteria
				ExternalComparisonCriteria criteria = input_.getCriteria();

				// number of flight types
				if (criteria.equals(ExternalComparisonCriteria.NUM_FLIGHT_TYPES)) {
					numberOfFlightTypes(connection, statement, dataset);
				}
				else if (criteria.equals(ExternalComparisonCriteria.NUM_PEAKS_WITH_OCCURRENCE) || criteria.equals(ExternalComparisonCriteria.NUM_PEAKS_WITHOUT_OCCURRENCE)) {
					numberOfPeaks(connection, dataset, criteria.equals(ExternalComparisonCriteria.NUM_PEAKS_WITH_OCCURRENCE));
				}
				else if (criteria.equals(ExternalComparisonCriteria.VALIDITY)) {
					validities(connection, dataset);
				}
				else if (criteria.equals(ExternalComparisonCriteria.MAX_PEAK)) {
					getHighestLowest(connection, dataset, "max_val", true);
				}
				else if (criteria.equals(ExternalComparisonCriteria.MIN_PEAK)) {
					getHighestLowest(connection, dataset, "min_val", false);
				}
			}
		}

		// return dataset
		return dataset;
	}

	@Override
	protected void succeeded() {

		// call ancestor
		super.succeeded();

		// set chart data
		try {

			// get dataset
			CategoryDataset dataset = get();
			boolean legendVisible = dataset.getRowCount() > 1;

			// user started task
			if (automaticTasks_ == null) {

				// get column plot panel
				StatisticsViewPanel panel = (StatisticsViewPanel) taskPanel_.getOwner().getOwner().getViewPanel().getSubPanel(ViewPanel.STATS_VIEW);

				// set chart data to panel
				panel.setPlotData(dataset, yAxisLabel_, null, xAxisLabel_, yAxisLabel_, legendVisible, input_.getLabelDisplay(), false);

				// show column chart plot panel
				taskPanel_.getOwner().getOwner().getViewPanel().showSubPanel(ViewPanel.STATS_VIEW);
			}

			// automatic task
			else {

				// create plot attributes
				StatisticsPlotAttributes plotAttributes = new StatisticsPlotAttributes();
				plotAttributes.setLabelsVisible(input_.getLabelDisplay());
				plotAttributes.setLayered(false);
				plotAttributes.setLegendVisible(legendVisible);
				plotAttributes.setSubTitle(null);
				plotAttributes.setTitle(yAxisLabel_);
				plotAttributes.setXAxisLabel(xAxisLabel_);
				plotAttributes.setYAxisLabel(yAxisLabel_);

				// manage automatic tasks
				automaticTaskOwnerSucceeded(new Pair<>(dataset, plotAttributes), automaticTasks_, taskPanel_, executeAutomaticTasksInParallel_);
			}
		}

		// exception occurred
		catch (InterruptedException | ExecutionException e) {
			handleResultRetrievalException(e);
		}
	}

	@Override
	protected void failed() {

		// call ancestor
		super.failed();

		// manage automatic tasks
		automaticTaskOwnerFailed(automaticTasks_, executeAutomaticTasksInParallel_);
	}

	@Override
	protected void cancelled() {

		// call ancestor
		super.cancelled();

		// manage automatic tasks
		automaticTaskOwnerFailed(automaticTasks_, executeAutomaticTasksInParallel_);
	}

	/**
	 * Gets the spectrum with the highest or lowest value of the input from the database.
	 *
	 * @param connection
	 *            Database connection.
	 * @param dataset
	 *            File info table list.
	 * @param colName
	 *            Database column name.
	 * @param isDesc
	 *            True if descending order.
	 * @throws Exception
	 *             If exception occurs during process.
	 */
	private void getHighestLowest(Connection connection, DefaultCategoryDataset dataset, String colName, boolean isDesc) throws Exception {

		// set label
		String label = input_.getCriteria().toString();

		// update progress info
		updateMessage("Retrieving " + label + "...");

		// set labels
		xAxisLabel_ = "Stress Sequence";
		yAxisLabel_ = label;

		// prepare statement
		String sql = "select " + colName + " from ext_sth_flights where file_id = ? order by " + colName + (isDesc ? " desc" : " asc");
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setMaxRows(1);

			// loop over sequences
			for (ExternalStressSequence sequence : stressSequences_) {

				// get STH file ID
				int sthID = sequence.getID();

				// get mission
				String mission = sequence.getMission();
				mission = mission == null ? "Not specified" : mission;

				// get sequence name
				String name = getSequenceName(sthID, stressSequences_);

				// set STH file ID and execute query
				statement.setInt(1, sthID);
				try (ResultSet resultSet = statement.executeQuery()) {
					while (resultSet.next()) {
						dataset.addValue(resultSet.getDouble(colName), mission, name);
					}
				}
			}

			// reset statement
			statement.setMaxRows(0);
		}
	}

	/**
	 * Generates comparison for validities.
	 *
	 * @param connection
	 *            Database connection.
	 * @param dataset
	 *            Chart series.
	 * @throws Exception
	 *             If exception occurs during process.
	 */
	private void validities(Connection connection, DefaultCategoryDataset dataset) throws Exception {

		// update progress info
		updateMessage("Retrieving number of flights...");

		// set labels
		xAxisLabel_ = "Stress Sequence";
		yAxisLabel_ = "Number of Flights";

		// prepare statement
		String sql = "select sum(validity) as validities from ext_sth_flights where file_id = ?";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {

			// loop over sequences
			for (ExternalStressSequence sequence : stressSequences_) {

				// get STH file ID
				int sthID = sequence.getID();

				// get mission
				String mission = sequence.getMission();
				mission = mission == null ? "Not specified" : mission;

				// get sequence name
				String name = getSequenceName(sthID, stressSequences_);

				// set sequence ID and execute query
				statement.setInt(1, sthID);
				try (ResultSet resultSet = statement.executeQuery()) {
					while (resultSet.next()) {
						dataset.addValue(resultSet.getDouble("validities"), mission, name);
					}
				}
			}
		}
	}

	/**
	 * Generates comparison for number of peaks.
	 *
	 * @param connection
	 *            Database connection.
	 * @param dataset
	 *            Chart series.
	 * @param withOccurrence
	 *            True if with flight occurrences.
	 * @throws Exception
	 *             If exception occurs during process.
	 */
	private void numberOfPeaks(Connection connection, DefaultCategoryDataset dataset, boolean withOccurrence) throws Exception {

		// update progress info
		updateMessage("Retrieving number of peaks...");

		// set labels
		xAxisLabel_ = "Stress Sequence";
		yAxisLabel_ = "Number of peaks " + (withOccurrence ? "with occurrences" : "without occurrences");

		// prepare statement
		String sql = "select ";
		sql += withOccurrence ? "sum(num_peaks*validity)" : "sum(num_peaks)";
		sql += " as peaks from ext_sth_flights where file_id = ?";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {

			// loop over sequences
			for (ExternalStressSequence sequence : stressSequences_) {

				// get STH file ID
				int sthID = sequence.getID();

				// get mission
				String mission = sequence.getMission();
				mission = mission == null ? "Not specified" : mission;

				// get sequence name
				String name = getSequenceName(sthID, stressSequences_);

				// set STH file ID and execute query
				statement.setInt(1, sthID);
				try (ResultSet resultSet = statement.executeQuery()) {
					while (resultSet.next()) {
						dataset.addValue(resultSet.getInt("peaks"), mission, name);
					}
				}
			}
		}
	}

	/**
	 * Generates comparison for number of flight types.
	 *
	 * @param connection
	 *            Database connection.
	 * @param statement
	 *            Database statement.
	 * @param dataset
	 *            Chart series.
	 * @throws Exception
	 *             If exception occurs during process.
	 */
	private void numberOfFlightTypes(Connection connection, Statement statement, DefaultCategoryDataset dataset) throws Exception {

		// update progress info
		updateMessage("Retrieving number of flight types...");

		// set labels
		xAxisLabel_ = "Stress Sequence";
		yAxisLabel_ = "Number of flight types";

		// create query
		String sql = "select file_id, num_flights from ext_sth_files where ";
		for (int i = 0; i < stressSequences_.size(); i++) {
			sql += "file_id = " + stressSequences_.get(i).getID() + (i == stressSequences_.size() - 1 ? "" : " or ");
		}
		sql += " order by num_flights " + (input_.getOrder() ? "desc" : "asc");

		// execute query for getting number of flights
		try (ResultSet resultSet = statement.executeQuery(sql)) {

			// add data to series
			while (resultSet.next()) {

				// get STH file ID
				int sthID = resultSet.getInt("file_id");

				// get mission
				String mission = getMission(sthID, stressSequences_);
				mission = mission == null ? "Not specified" : mission;

				// get sequence name
				String name = getSequenceName(sthID, stressSequences_);

				// add chart series
				dataset.addValue(resultSet.getInt("num_flights"), mission, name);
			}
		}
	}

	/**
	 * Returns the name of sequence.
	 *
	 * @param sequenceID
	 *            Spectrum ID.
	 * @param sequences
	 *            List of compared spectra.
	 * @return The name of sequence.
	 */
	private String getSequenceName(int sequenceID, List<ExternalStressSequence> sequences) {

		// initialize name
		String name = "";

		// loop over sequences
		for (ExternalStressSequence sequence : sequences) {

			// not the sequence
			if (sequenceID != sequence.getID()) {
				continue;
			}

			// include stress sequence name
			if (input_.getIncludeSequenceName()) {
				name += sequence.getName() + "\n";
			}

			// include EID
			if (input_.getIncludeEID()) {
				name += ExternalStressSequence.getEID(sequence.getName()) + "\n";
			}

			// include A/C program
			if (input_.getIncludeProgram()) {
				name += sequence.getProgram() + "\n";
			}

			// include A/C section
			if (input_.getIncludeSection()) {
				name += sequence.getSection() + "\n";
			}

			// include fatigue mission
			if (input_.getIncludeMission()) {
				name += sequence.getMission() + "\n";
			}

			// break
			break;
		}

		// return name
		return name.substring(0, name.lastIndexOf("\n"));
	}

	/**
	 * Returns the name of mission.
	 *
	 * @param sequenceID
	 *            Stress sequence ID.
	 * @param sequences
	 *            List of compared sequences.
	 * @return The name of series.
	 */
	private static String getMission(int sequenceID, List<ExternalStressSequence> sequences) {
		for (ExternalStressSequence sequence : sequences) {
			if (sequenceID == sequence.getID())
				return sequence.getMission();
		}
		return null;
	}
}
