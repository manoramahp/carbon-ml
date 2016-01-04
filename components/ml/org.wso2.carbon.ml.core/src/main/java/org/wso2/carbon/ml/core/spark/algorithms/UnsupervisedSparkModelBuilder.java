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

package org.wso2.carbon.ml.core.spark.algorithms;

import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.mllib.clustering.KMeansModel;
import org.apache.spark.mllib.linalg.Vector;
import org.wso2.carbon.ml.commons.constants.MLConstants;
import org.wso2.carbon.ml.commons.constants.MLConstants.UNSUPERVISED_ALGORITHM;
import org.wso2.carbon.ml.commons.domain.ClusterPoint;
import org.wso2.carbon.ml.commons.domain.MLModel;
import org.wso2.carbon.ml.commons.domain.ModelSummary;
import org.wso2.carbon.ml.commons.domain.Workflow;
import org.wso2.carbon.ml.core.exceptions.AlgorithmNameException;
import org.wso2.carbon.ml.core.exceptions.MLModelBuilderException;
import org.wso2.carbon.ml.core.interfaces.MLModelBuilder;
import org.wso2.carbon.ml.core.internal.MLModelConfigurationContext;
import org.wso2.carbon.ml.core.spark.models.MLKMeansModel;
import org.wso2.carbon.ml.core.spark.summary.ClusterModelSummary;
import org.wso2.carbon.ml.core.spark.transformations.BasicEncoder;
import org.wso2.carbon.ml.core.spark.transformations.DiscardedRowsFilter;
import org.wso2.carbon.ml.core.spark.transformations.DoubleArrayToVector;
import org.wso2.carbon.ml.core.spark.transformations.HeaderFilter;
import org.wso2.carbon.ml.core.spark.transformations.LineToTokens;
import org.wso2.carbon.ml.core.spark.transformations.MeanImputation;
import org.wso2.carbon.ml.core.spark.transformations.RemoveDiscardedFeatures;
import org.wso2.carbon.ml.core.spark.transformations.StringArrayToDoubleArray;
import org.wso2.carbon.ml.core.utils.MLCoreServiceValueHolder;
import org.wso2.carbon.ml.core.utils.MLUtils;
import org.wso2.carbon.ml.database.DatabaseService;
import org.wso2.carbon.ml.database.exceptions.DatabaseHandlerException;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Building unsupervised models supported by Spark.
 */
public class UnsupervisedSparkModelBuilder extends MLModelBuilder {

    public UnsupervisedSparkModelBuilder(MLModelConfigurationContext context) {
        super(context);
    }
    
    private JavaRDD<Vector> preProcess() throws MLModelBuilderException {
        JavaRDD<String> lines = null;
        try {
            MLModelConfigurationContext context = getContext();
            HeaderFilter headerFilter = new HeaderFilter.Builder().init(context).build();
            LineToTokens lineToTokens = new LineToTokens.Builder().init(context).build();
            DiscardedRowsFilter discardedRowsFilter = new DiscardedRowsFilter.Builder().init(context).build();
            RemoveDiscardedFeatures removeDiscardedFeatures = new RemoveDiscardedFeatures.Builder().init(context)
                    .build();
            BasicEncoder basicEncoder = new BasicEncoder.Builder().init(context).build();
            MeanImputation meanImputation = new MeanImputation.Builder().init(context).build();
            StringArrayToDoubleArray stringArrayToDoubleArray = new StringArrayToDoubleArray.Builder().build();
            DoubleArrayToVector doubleArrayToVector = new DoubleArrayToVector.Builder().build();

            lines = context.getLines().cache();
            return lines.filter(headerFilter).map(lineToTokens).filter(discardedRowsFilter)
                    .map(removeDiscardedFeatures).map(basicEncoder).map(meanImputation).map(stringArrayToDoubleArray)
                    .map(doubleArrayToVector);
        } finally {
            if (lines != null) {
                lines.unpersist();
            }
        }
    }

    /**
     * Build an unsupervised model.
     */
    public MLModel build() throws MLModelBuilderException {
        MLModelConfigurationContext context = getContext();
        DatabaseService databaseService = MLCoreServiceValueHolder.getInstance().getDatabaseService();
        try {
            Workflow workflow = context.getFacts();
            long modelId = context.getModelId();
            ModelSummary summaryModel = null;
            SortedMap<Integer, String> includedFeatures = MLUtils
                    .getIncludedFeaturesAfterReordering(workflow, context.getNewToOldIndicesList(),
                            context.getResponseIndex());

            // gets the pre-processed dataset
            JavaRDD<Vector> data = preProcess().cache();
            JavaRDD<Vector>[] dataSplit = data.randomSplit(
                    new double[] { workflow.getTrainDataFraction(), 1 - workflow.getTrainDataFraction() },
                    MLConstants.RANDOM_SEED);
            
            data.unpersist();
            
            JavaRDD<Vector> trainingData = dataSplit[0].cache();
            JavaRDD<Vector> testingData = null;
            if (dataSplit.length > 1) {
                testingData = dataSplit[1];
            }
            // create a deployable MLModel object
            MLModel mlModel = new MLModel();
            mlModel.setAlgorithmName(workflow.getAlgorithmName());
            mlModel.setAlgorithmClass(workflow.getAlgorithmClass());
            mlModel.setFeatures(workflow.getFeatures());
            mlModel.setResponseVariable(workflow.getResponseVariable());
            mlModel.setEncodings(context.getEncodings());
            mlModel.setNewToOldIndicesList(context.getNewToOldIndicesList());
            mlModel.setResponseIndex(-1);


            // build a machine learning model according to user selected algorithm
            UNSUPERVISED_ALGORITHM unsupervised_algorithm = UNSUPERVISED_ALGORITHM.valueOf(workflow.getAlgorithmName());
            switch (unsupervised_algorithm) {
            case K_MEANS:
                summaryModel = buildKMeansModel(modelId, trainingData, testingData, workflow, mlModel,includedFeatures);
                break;
            default:
                throw new AlgorithmNameException("Incorrect algorithm name: " + workflow.getAlgorithmName()
                        + " for model id: " + modelId);
            }
            // persist model summary
            databaseService.updateModelSummary(modelId, summaryModel);
            return mlModel;
        } catch (DatabaseHandlerException e) {
            throw new MLModelBuilderException("An error occurred while building unsupervised machine learning model: "
                    + e.getMessage(), e);
        }
    }

    /**
     * This method builds a k-means model.
     *
     * @param modelID Model ID
     * @param trainingData Training data as a JavaRDD of LabeledPoints
     * @param testingData Testing data as a JavaRDD of LabeledPoints
     * @param workflow Machine learning workflow
     * @param mlModel Deployable machine learning model
     * @throws MLModelBuilderException
     */
    private ModelSummary buildKMeansModel(long modelID, JavaRDD<Vector> trainingData, JavaRDD<Vector> testingData,
            Workflow workflow, MLModel mlModel, SortedMap<Integer, String> includedFeatures) throws MLModelBuilderException {
        try {
            Map<String, String> hyperParameters = workflow.getHyperParameters();
            KMeans kMeans = new KMeans();
            KMeansModel kMeansModel = kMeans.train(trainingData,
                    Integer.parseInt(hyperParameters.get(MLConstants.NUM_CLUSTERS)),
                    Integer.parseInt(hyperParameters.get(MLConstants.MAX_ITERATIONS)));

            // add test data to cache - test data is not used as of now
//            if (testingData != null) {
//                testingData.cache();
//            }

            // generating data for summary clusters
            double sampleSize = (double) MLCoreServiceValueHolder.getInstance().getSummaryStatSettings()
                    .getSampleSize();

            double sampleFraction;
            if(trainingData.count() != 1) { //avoiding division by 0
                sampleFraction = sampleSize / (trainingData.count() - 1);
            } else{
                sampleFraction = sampleSize / (trainingData.count());
            }
            JavaRDD<Vector> sampleData = null;

            if (sampleFraction >= 1.0) {
                sampleData = trainingData;
            } else { // Use randomly selected sample fraction of rows if number of records is > sample fraction
                sampleData = trainingData.sample(false, sampleFraction);
            }

            trainingData.unpersist(); // since no more used
            sampleData.cache();

            // Populate cluster points list with predicted clusters and features
            List<Tuple2<Integer, Vector>> kMeansPredictions = kMeansModel.predict(sampleData).zip(sampleData).collect();
            List<ClusterPoint> clusterPoints = new ArrayList<ClusterPoint>();

            for (Tuple2<Integer, org.apache.spark.mllib.linalg.Vector> kMeansPrediction : kMeansPredictions) {

                ClusterPoint clusterPoint = new ClusterPoint();
                clusterPoint.setCluster(kMeansPrediction._1());
                double[] features = new double[includedFeatures.size()];

                for (int i = 0; i < includedFeatures.size(); i++) {
                    double point = (kMeansPrediction._2().toArray())[i];
                    features[i] = point;
                }
                clusterPoint.setFeatures(features);
                clusterPoints.add(clusterPoint);
            }

            ClusterModelSummary clusterModelSummary = new ClusterModelSummary();
//            double trainDataComputeCost = kMeansModel.computeCost(trainingData.rdd());
//            double testDataComputeCost = kMeansModel.computeCost(testingData.rdd());
//            clusterModelSummary.setTrainDataComputeCost(trainDataComputeCost);
//            clusterModelSummary.setTestDataComputeCost(testDataComputeCost);
            mlModel.setModel(new MLKMeansModel(kMeansModel));
            clusterModelSummary.setAlgorithm(UNSUPERVISED_ALGORITHM.K_MEANS.toString());
            clusterModelSummary.setDatasetVersion(workflow.getDatasetVersion());
            clusterModelSummary.setClusterPoints(clusterPoints);
            clusterModelSummary.setFeatures(includedFeatures.values().toArray(new String[0]));

            return clusterModelSummary;
        } catch (Exception e) {
            throw new MLModelBuilderException("An error occurred while building k-means model: " + e.getMessage(), e);
        }
    }
}

