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
package equinox.utility.exception;

import equinox.analysisServer.remote.message.AnalysisServerStatisticsRequestFailed;

/**
 * Class for analysis server statistics request failed exception.
 *
 * @author Murat Artim
 * @date 18 Jul 2018
 * @time 00:39:23
 */
public class AnalysisServerStatisticsRequestFailedException extends Exception {

	/** Serial ID. */
	private static final long serialVersionUID = 1L;

	/** Server message. */
	private final AnalysisServerStatisticsRequestFailed serverMessage;

	/**
	 * Creates server database query failed exception.
	 *
	 * @param serverMessage
	 *            Server failure message.
	 */
	public AnalysisServerStatisticsRequestFailedException(AnalysisServerStatisticsRequestFailed serverMessage) {
		this.serverMessage = serverMessage;
	}

	@Override
	public String getMessage() {
		return serverMessage.getExceptionMessage();
	}

	/**
	 * Returns the listener hash code.
	 *
	 * @return Listener hash code.
	 */
	public int getListenerHashCode() {
		return serverMessage.getListenerHashCode();
	}
}