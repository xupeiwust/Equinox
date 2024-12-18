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
package equinox.controller;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import org.apache.commons.text.WordUtils;

import equinox.data.EquinoxTheme;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Class for warning notification panel controller.
 *
 * @author Murat Artim
 * @date Dec 12, 2014
 * @time 10:15:54 AM
 */
public class WarningNotificationPanel implements Initializable {

	@FXML
	private VBox root_;

	@FXML
	private Label title_, text_;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		// no implementation
	}

	/**
	 * Loads and returns shared file notification panel.
	 *
	 * @param message
	 *            Message.
	 * @param title
	 *            Notification title.
	 * @return The newly loaded shared file panel.
	 */
	public static VBox load(String message, String title) {

		try {

			// load fxml file
			FXMLLoader fxmlLoader = new FXMLLoader(EquinoxTheme.getFXMLResource("WarningNotificationPanel.fxml"));
			fxmlLoader.load();

			// get controller
			WarningNotificationPanel controller = (WarningNotificationPanel) fxmlLoader.getController();

			// set attributes
			controller.text_.setText(WordUtils.wrap(message, 120));
			controller.title_.setText(title);

			// return controller
			return controller.root_;
		}

		// exception occurred during loading
		catch (IOException e1) {
			throw new RuntimeException(e1);
		}
	}
}
