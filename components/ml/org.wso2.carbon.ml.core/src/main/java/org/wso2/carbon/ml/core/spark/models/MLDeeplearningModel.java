/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.ml.core.spark.models;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.apache.spark.mllib.linalg.Vector;

import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.serial.ObjectTreeBinarySerializer;
import water.util.FileUtils;
import hex.deeplearning.DeepLearningModel;

/**
 * Wraps an H2O DeeplearningModel object
 */
public class MLDeeplearningModel implements Externalizable {

    private DeepLearningModel dlModel;
    private HashMap<Double, Double> labelToH2OEnumMap;
    // string location is required because custom deserialization doesn't work
    private String storageLocation;

    public MLDeeplearningModel() {
    }

    public MLDeeplearningModel(DeepLearningModel dlModel) {
        this.dlModel = dlModel;
    }

    public void setStorageLocation(String location) {
        storageLocation = location;
    }

    /**
     * Returns the model
     *
     * @return model
     */
    public DeepLearningModel getDeepLearningModel() {
        return this.dlModel;
    }

    /**
     * Set the model
     *
     * @param model model
     */
    public void setDeepLearningModel(DeepLearningModel model) {
        this.dlModel = model;
    }

    /**
     * Predicts the label of a given input
     * 
     * @param input input to predict as a vector
     * @return prediction
     */
    public double predict(Vector input) {
        double predVal = dlModel.score(input.toArray());
        return predVal;
    }

    public double[] predict(Frame inputs) {
        Frame predsFrame = dlModel.score(inputs);
        int numRows = (int) inputs.numRows();
        Vec predsVector = predsFrame.vec(0);
        double[] predVals = new double[numRows];
        for (int i = 0; i < numRows; i++) {
            predVals[i] = predsVector.at(i);
        }
        return predVals;
    }

    public String getURIStringForLocation(String loc) {
        return "file" + loc.substring(1).replace("\\", "/");
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(storageLocation);
        out.writeObject(labelToH2OEnumMap);
        @SuppressWarnings("rawtypes")
        List<Key> keys = new LinkedList<Key>();
        // cannot add published keys, gives nullpointer
        keys.add(dlModel._key);
        new ObjectTreeBinarySerializer().save(keys, FileUtils.getURI(storageLocation));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        storageLocation = (String) in.readObject();
        labelToH2OEnumMap = (HashMap<Double, Double>) in.readObject();
        @SuppressWarnings("rawtypes")
        List<Key> keys = new ObjectTreeBinarySerializer().load(FileUtils.getURI(storageLocation));
        this.dlModel = (DeepLearningModel) keys.get(0).get();
    }

    public DeepLearningModel getDlModel() {
        return dlModel;
    }

}
