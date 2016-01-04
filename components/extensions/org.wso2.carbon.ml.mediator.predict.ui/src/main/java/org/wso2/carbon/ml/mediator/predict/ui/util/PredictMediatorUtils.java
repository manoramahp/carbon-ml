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

package org.wso2.carbon.ml.mediator.predict.ui.util;

import org.wso2.carbon.ml.commons.constants.MLConstants;
import org.wso2.carbon.ml.commons.domain.Feature;
import org.wso2.carbon.ml.commons.domain.MLModel;
import org.wso2.carbon.ml.core.exceptions.MLAnalysisHandlerException;
import org.wso2.carbon.ml.core.exceptions.MLInputAdapterException;
import org.wso2.carbon.ml.core.exceptions.MLModelHandlerException;
import org.wso2.carbon.ml.core.factories.DatasetType;
import org.wso2.carbon.ml.core.impl.MLIOFactory;
import org.wso2.carbon.ml.core.interfaces.MLInputAdapter;
import org.wso2.carbon.ml.core.utils.MLCoreServiceValueHolder;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URISyntaxException;
import java.util.List;

public class PredictMediatorUtils {

    public static final String REGISTRY_STORAGE_PREFIX = "registry";
    public static final String FILE_STORAGE_PREFIX = "file";
    public static final String SEPARATOR = ":";
    public static final String PATH_TO_GOVERNANCE_REGISTRY = "/_system/governance";

    /**
     * Retrieve the ML-Model from the Registry
     * @param modelStorageLocation
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private static MLModel retrieveModel(String modelStorageLocation) throws ClassNotFoundException,
            URISyntaxException, MLInputAdapterException, IOException {

        ObjectInputStream ois = null;
        try {
            String[] modelStorage = modelStorageLocation.split(SEPARATOR);
            String storageType = modelStorage[0];
            if (storageType.equals(REGISTRY_STORAGE_PREFIX)) {
                if (modelStorage[1].startsWith(PATH_TO_GOVERNANCE_REGISTRY)) {
                    modelStorageLocation = modelStorage[1].substring(PATH_TO_GOVERNANCE_REGISTRY.length());
                } else {
                    modelStorageLocation = modelStorage[1];
                }
            } else {
                storageType = DatasetType.FILE.getValue();
            }
            MLIOFactory ioFactory = new MLIOFactory(MLCoreServiceValueHolder.getInstance().getMlProperties());
            MLInputAdapter inputAdapter = ioFactory.getInputAdapter(storageType + MLConstants.IN_SUFFIX);
            InputStream in = inputAdapter.read(modelStorageLocation);
            ois = new ObjectInputStream(in);
            MLModel mlModel = (MLModel) ois.readObject();
            return mlModel;
        } finally {
            if (ois != null) {
                ois.close();
            }
        }

    }

    /**
     * Get the Features list of the model
     * @param modelStorageLocation ML model storage location
     * @return the list of Features of the MLModel
     */
    public static List<Feature> getFeaturesOfModel(String modelStorageLocation) throws IOException,
            ClassNotFoundException, URISyntaxException, MLInputAdapterException {

        MLModel mlModel = retrieveModel(modelStorageLocation);
        List<Feature> features = mlModel.getFeatures();
        return features;
    }

    /**
     * Get the response variable of the model
     * @param modelStorageLocation model storage location
     * @return the response variable name of the MLModel
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws URISyntaxException
     * @throws MLInputAdapterException
     */
    public static String getResponseVariable(String modelStorageLocation) throws IOException,
            ClassNotFoundException, URISyntaxException, MLInputAdapterException {

        MLModel mlModel = retrieveModel(modelStorageLocation);
        return mlModel.getResponseVariable();
    }

    /**
     * Get the algorithm class of the model
     * @param modelStorageLocation model storage location
     * @return algorithm class
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws URISyntaxException
     * @throws MLInputAdapterException
     */
    public static String getAlgorithmClass(String modelStorageLocation)
            throws IOException, ClassNotFoundException, URISyntaxException, MLInputAdapterException {

        MLModel mlModel = retrieveModel(modelStorageLocation);
        return mlModel.getAlgorithmClass();
    }
}
