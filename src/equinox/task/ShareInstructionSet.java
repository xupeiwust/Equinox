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

import java.nio.file.Path;
import java.util.Arrays;

import equinox.exchangeServer.remote.data.ExchangeUser;
import equinox.network.AutomationClientHandler;
import equinox.plugin.FileType;
import equinox.serverUtilities.Permission;
import equinox.serverUtilities.SharedFileInfo;
import equinox.task.InternalEquinoxTask.FileSharingTask;
import equinox.task.automation.AutomationTask;
import equinox.utility.Utility;

/**
 * Class for share instruction set task.
 *
 * @author Murat Artim
 * @date 22 Sep 2018
 * @time 17:25:09
 */
public class ShareInstructionSet extends TemporaryFileCreatingTask<Void> implements FileSharingTask, AutomationTask {

	/** File to share. */
	private Path file;

	/** Recipients. */
	private final ExchangeUser recipient;

	/** Request id. */
	private int requestId = -1;

	/** Automation client handler. */
	private AutomationClientHandler automationClientHandler = null;

	/**
	 * Creates share instruction set task.
	 *
	 * @param file
	 *            File to share.
	 * @param recipient
	 *            Recipient username.
	 */
	public ShareInstructionSet(Path file, ExchangeUser recipient) {
		this.file = file;
		this.recipient = recipient;
	}

	@Override
	public void setAutomationClientHandler(AutomationClientHandler automationClientHandler, int requestId) {
		this.automationClientHandler = automationClientHandler;
		this.requestId = requestId;
	}

	@Override
	public String getTaskTitle() {
		return "Share instruction set";
	}

	@Override
	public boolean canBeCancelled() {
		return false;
	}

	@Override
	protected Void call() throws Exception {

		// check permission
		checkPermission(Permission.SHARE_FILE);

		// update progress info
		updateMessage("Sharing instruction set...");

		// zip file
		String name = FileType.getNameWithoutExtension(file);
		Path output = getWorkingDirectory().resolve(FileType.appendExtension(name, FileType.ZIP));
		Utility.zipFile(file, output.toFile(), this);

		// upload file to filer
		shareFile(output, Arrays.asList(recipient), SharedFileInfo.INSTRUCTION_SET);
		return null;
	}

	@Override
	protected void succeeded() {

		// call ancestor
		super.succeeded();

		// notify client handler of task completion (if set)
		if (automationClientHandler != null) {
			automationClientHandler.taskCompleted(requestId);
		}
	}

	@Override
	protected void failed() {

		// call ancestor
		super.failed();

		// notify client handler of task failure (if set)
		if (automationClientHandler != null) {
			automationClientHandler.taskFailed(requestId);
		}
	}

	@Override
	protected void cancelled() {

		// call ancestor
		super.cancelled();

		// notify client handler of task failure (if set)
		if (automationClientHandler != null) {
			automationClientHandler.taskFailed(requestId);
		}
	}
}