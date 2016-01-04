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

package org.wso2.carbon.ml.mediator.predict.util;

import org.apache.synapse.MessageContext;
import org.apache.synapse.config.xml.SynapsePath;
import org.jaxen.JaxenException;
import org.wso2.carbon.ml.commons.constants.MLConstants;
import org.wso2.carbon.ml.commons.domain.Feature;
import org.wso2.carbon.ml.commons.domain.MLModel;
import org.wso2.carbon.ml.core.exceptions.MLInputAdapterException;
import org.wso2.carbon.ml.core.exceptions.MLModelBuilderException;
import org.wso2.carbon.ml.core.exceptions.MLModelHandlerException;
import org.wso2.carbon.ml.core.factories.DatasetType;
import org.wso2.carbon.ml.core.impl.MLIOFactory;
import org.wso2.carbon.ml.core.impl.Predictor;
import org.wso2.carbon.ml.core.interfaces.MLInputAdapter;
import org.wso2.carbon.ml.core.utils.MLCoreServiceValueHolder;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URISyntaxException;
import java.util.*;

public class ModelHandler {

    public static final String REGISTRY_STORAGE_PREFIX = "registry";
    public static final String FILE_STORAGE_PREFIX = "file";
    public static final String PATH_TO_GOVERNANCE_REGISTRY = "/_system/governance";

    private static ModelHandler instance;

    private long modelId;
    private Map<SynapsePath, Integer> featureIndexMap;
    private MLModel mlModel;

    private ModelHandler(String modelStorageLocation, Map<String, SynapsePath> featureMappings)
            throws IOException, ClassNotFoundException, URISyntaxException, MLInputAdapterException {
        initializeModel(modelStorageLocation, featureMappings);
    }

    /**
     * Get the ModelHandler instance
     * @param storageLocation   storage location of the ML-model
     * @param featureMappings   Map containing pairs <feature-name, synapse-path>
     * @return ModelHandler instance
     */
    public static ModelHandler getInstance(String storageLocation, Map<String, SynapsePath> featureMappings, boolean isUpdated)
            throws ClassNotFoundException, IOException, URISyntaxException, MLInputAdapterException {
        if(instance == null || isUpdated) {
            instance = new ModelHandler(storageLocation, featureMappings);
        }
        return instance;
    }

    /**
     * Deserialize the ML model and map the feature indices with the xpath/json-path expressions
     * @param modelStorageLocation path to MLModel
     * @param inputVariables Map containing the key- value pairs <feature-name, xpath/json-path-expression-to-extract-feature-value>
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws URISyntaxException
     * @throws MLInputAdapterException
     */
    private void initializeModel(String modelStorageLocation, Map<String, SynapsePath> inputVariables)
            throws IOException, ClassNotFoundException, URISyntaxException, MLInputAdapterException {

        mlModel = retrieveModel(modelStorageLocation);

        featureIndexMap = new HashMap<SynapsePath, Integer>();
        List<Feature> features = mlModel.getFeatures();
        List<Integer> newToOldIndicesList = mlModel.getNewToOldIndicesList();
        for(Feature feature : features) {
            if(inputVariables.get(feature.getName()) != null) {
                int newFeatureIndex = newToOldIndicesList.indexOf(feature.getIndex());
                featureIndexMap.put(inputVariables.get(feature.getName()), newFeatureIndex);
            }
        }
   }

    /**
     * Get the MLModel from its storage location
     * @return MLModel object
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private MLModel retrieveModel(String modelStorageLocation)
            throws IOException, ClassNotFoundException, URISyntaxException, MLInputAdapterException {

        String[] modelStorage = modelStorageLocation.split(":");
        String storageType = modelStorage[0];
        if(storageType.equals(REGISTRY_STORAGE_PREFIX)) {
            if(modelStorage[1].startsWith(PATH_TO_GOVERNANCE_REGISTRY)) {
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
        ObjectInputStream ois = new ObjectInputStream(in);
        MLModel mlModel = (MLModel) ois.readObject();
        ois.close();
        return mlModel;
    }

    /**
     * Get the predicted value for the given input features using the ML-Model
     * @param messageContext    the incoming message context
     * @return                  the predicted value as String
     */
    public String getPrediction(MessageContext messageContext) throws MLModelBuilderException, JaxenException,
            MLModelHandlerException {

        String data[] = new String[featureIndexMap.size()];
        for(Map.Entry<SynapsePath, Integer> entry : featureIndexMap.entrySet()) {

            SynapsePath synapsePath = entry.getKey();
            // Extract the feature value from the message
            String variableValue = synapsePath.stringValueOf(messageContext);
            if(variableValue != null) {
                // Get the mapping feature index of the ML-model
                int featureIndex = entry.getValue();
                data[featureIndex] = variableValue;
            }
        }
        return predict(data);
    }

    /**
     * Get the predicted value for the given input features using the ML-Model
     * @param messageContext the incoming message context
     * @param percentile percentile value
     * @return the predicted value as String
     */
    public String getPrediction(MessageContext messageContext, String percentile)
            throws MLModelBuilderException, JaxenException, MLModelHandlerException {

        String data[] = new String[featureIndexMap.size()];
        for (Map.Entry<SynapsePath, Integer> entry : featureIndexMap.entrySet()) {

            SynapsePath synapsePath = entry.getKey();
            // Extract the feature value from the message
            String variableValue = synapsePath.stringValueOf(messageContext);
            if (variableValue != null) {
                // Get the mapping feature index of the ML-model
                int featureIndex = entry.getValue();
                data[featureIndex] = variableValue;
            }
        }
        double percentileValue = Double.parseDouble(percentile);
        return predict(data, percentileValue);
    }

    /**
     * Predict the value using the feature values
     * @param data  feature values array
     * @return      predicted value as String
     * @throws MLModelHandlerException 
     */
    private String predict(String[] data) throws MLModelHandlerException {

        List<String[]> list = new ArrayList<String[]>();
        list.add(data);
        Predictor predictor = new Predictor(modelId, mlModel, list);
        List<?> predictions = predictor.predict();
        return predictions.get(0).toString();
    }

    /**
     * Predict the value using the feature values (anomaly detection models)
     * @param data feature values array
     * @param percentile percentile value
     * @return predicted value as String
     * @throws MLModelHandlerException
     */
    private String predict(String[] data, double percentile) throws MLModelHandlerException {

        List<String[]> list = new ArrayList<String[]>();
        list.add(data);
        Predictor predictor = new Predictor(modelId, mlModel, list, percentile, false);
        List<?> predictions = predictor.predict();
        return predictions.get(0).toString();
    }
}
