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

package org.wso2.carbon.ml.mediator.predict.xml;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.Mediator;
import org.apache.synapse.config.xml.AbstractMediatorFactory;
import org.apache.synapse.config.xml.SynapsePath;
import org.apache.synapse.config.xml.SynapsePathFactory;
import org.jaxen.JaxenException;
import org.wso2.carbon.ml.mediator.predict.PredictMediator;
import org.wso2.carbon.ml.mediator.predict.PredictMediatorConstants;

import javax.xml.namespace.QName;
import java.util.Iterator;
import java.util.Properties;

public class PredictMediatorFactory extends AbstractMediatorFactory {

    private static final Log log = LogFactory.getLog(PredictMediatorFactory.class);

    @Override
    protected Mediator createSpecificMediator(OMElement omElement, Properties properties) {

        PredictMediator predictMediator = new PredictMediator();
        configureMediator(omElement, predictMediator);
        return predictMediator;
    }

    /**
     * Configure the Predict mediator
     * @param omElement  mediator xml configuration
     * @param predictMediator PredictMediator to be configured
     */
    private void configureMediator(OMElement omElement, PredictMediator predictMediator) {

        // Configure model
        OMElement modelElement = omElement.getFirstChildWithName(PredictMediatorConstants.MODEL_QNAME);
        if(modelElement != null) {
            String modelName = getAttributeValue(modelElement, PredictMediatorConstants.STORAGE_LOCATION_ATT);
            predictMediator.setModelStorageLocation(modelName);
        } else {
            handleException("Model element not defined.");

        }

        // Configure percentile
        OMElement percentileElement = omElement.getFirstChildWithName(PredictMediatorConstants.PERCENTILE_QNAME);
        if (percentileElement != null) {
            String percentileValue = getAttributeValue(percentileElement, PredictMediatorConstants.VALUE_ATT);
            if (percentileValue != null) {
                predictMediator.setPercentile(percentileValue);
                predictMediator.setAnomalyDetection(true);
            } else {
                predictMediator.setPercentile("95.0");
                predictMediator.setAnomalyDetection(true);
            }
        }

        // Configure features
        OMElement featuresElement = omElement.getFirstChildWithName(PredictMediatorConstants.FEATURES_QNAME);
        if(featuresElement != null) {
            Iterator featuresItr = featuresElement.getChildrenWithName(PredictMediatorConstants.FEATURE_QNAME);

            while (featuresItr.hasNext()) {
                OMElement featureElement = (OMElement) featuresItr.next();
                String expression = getAttributeValue(featureElement, PredictMediatorConstants.EXPRESSION_ATT);
                String featureName = getAttributeValue(featureElement, PredictMediatorConstants.NAME_ATT);

                if (expression != null || featureName != null) {
                    SynapsePath synapsePath = null;
                    if (expression != null) {
                        try {
                            synapsePath = SynapsePathFactory.getSynapsePath(featureElement, PredictMediatorConstants.EXPRESSION_ATT);
                        } catch (JaxenException e) {
                            handleException("Invalid Synapse Path specified for feature expression attribute : " +
                                    expression, e);
                        }
                    }
                    predictMediator.addFeatureMapping(featureName, synapsePath);
                }
            }
        } else {
            handleException("Features element not defined.");
        }

        // Configure prediction
        OMElement predictionElement = omElement.getFirstChildWithName(PredictMediatorConstants.PREDICTION_OUTPUT_QNAME);
        if(predictionElement != null) {
            String predictionPropertyName = getAttributeValue(predictionElement, PredictMediatorConstants.PROPERTY_ATT);
            if (predictionPropertyName != null) {
                predictMediator.setResultPropertyName(predictionPropertyName);
            } else {
                handleException("PredictionOutput property element not defined.");
            }
        } else {
            handleException("PredictionOutput element not defined.");
        }

    }

    /**
     * Get the attribute value from the OMAttribute
     * @param element OMElement containing the OMAttribute
     * @param qName Qname of the Attribute
     * @return the attribute value corresponding to the qName
     */
    private String getAttributeValue(OMElement element, QName qName) {
        OMAttribute a = element.getAttribute(qName);
        if (a != null) {
            return a.getAttributeValue();
        }
        return null;
    }

    @Override
    public QName getTagQName() {
        return PredictMediatorConstants.PREDICT_QNAME;
    }
}
