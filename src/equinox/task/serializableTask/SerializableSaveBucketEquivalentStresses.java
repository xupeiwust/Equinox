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
package equinox.task.serializableTask;

import java.io.File;
import java.util.ArrayList;

import org.controlsfx.control.ToggleSwitch;

import equinox.data.fileType.STFFileBucket;
import equinox.data.fileType.SpectrumItem;
import equinox.task.SaveBucketEquivalentStresses;
import equinox.task.SerializableTask;
import javafx.beans.property.BooleanProperty;
import javafx.scene.control.TreeItem;

/**
 * Class for serializable form of save bucket equivalent stresses task.
 *
 * @author Murat Artim
 * @date 2 Sep 2016
 * @time 10:17:11
 */
public class SerializableSaveBucketEquivalentStresses implements SerializableTask {

	/** Serial ID. */
	private static final long serialVersionUID = 1L;

	/** STF file buckets. */
	private final ArrayList<SerializableSpectrumItem> buckets_;

	/** Options. */
	private final boolean[] options_;

	/** Option names. */
	private final String[] optionNames_;

	/** Output file. */
	private final File output_;

	/** Equivalent stress type. */
	private final int stressType_;

	/**
	 * Creates serializable form of save bucket equivalent stresses task.
	 *
	 * @param buckets
	 *            STF file buckets.
	 * @param options
	 *            Options.
	 * @param output
	 *            Output file.
	 * @param stressType
	 *            Equivalent stress type.
	 */
	public SerializableSaveBucketEquivalentStresses(ArrayList<STFFileBucket> buckets, BooleanProperty[] options, File output, int stressType) {
		buckets_ = new ArrayList<>();
		for (STFFileBucket item : buckets)
			buckets_.add(new SerializableSpectrumItem(item));
		options_ = new boolean[options.length];
		optionNames_ = new String[options.length];
		for (int i = 0; i < options.length; i++) {
			options_[i] = options[i].get();
			optionNames_[i] = ((ToggleSwitch) options[i].getBean()).getText();
		}
		output_ = output;
		stressType_ = stressType;
	}

	@Override
	public SaveBucketEquivalentStresses getTask(TreeItem<String> fileTreeRoot) {

		// get buckets
		ArrayList<STFFileBucket> buckets = new ArrayList<>();
		for (SerializableSpectrumItem item : buckets_) {

			// get bucket
			SpectrumItem[] result = new SpectrumItem[1];
			searchSpectrumItem(item, fileTreeRoot, result);

			// bucket could not be found
			if (result[0] == null)
				return null;

			// add to buckets
			buckets.add((STFFileBucket) result[0]);
		}

		// get options
		BooleanProperty[] options = new BooleanProperty[options_.length];
		for (int i = 0; i < options.length; i++) {
			ToggleSwitch checkbox = new ToggleSwitch(optionNames_[i]);
			checkbox.setSelected(options_[i]);
			options[i] = checkbox.selectedProperty();
		}

		// create and return task
		return new SaveBucketEquivalentStresses(buckets, options, output_, stressType_);
	}
}
