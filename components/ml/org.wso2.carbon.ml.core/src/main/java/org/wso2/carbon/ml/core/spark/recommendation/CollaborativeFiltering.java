/*
 *  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.ml.core.spark.recommendation;

import org.apache.spark.api.java.JavaDoubleRDD;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.mllib.recommendation.ALS;
import org.apache.spark.mllib.recommendation.MatrixFactorizationModel;
import org.apache.spark.mllib.recommendation.Rating;
import org.wso2.carbon.ml.core.exceptions.MLModelHandlerException;

import scala.Tuple2;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class CollaborativeFiltering implements Serializable{

	private static final long serialVersionUID = 5273514743795162923L;

	/**
	 * This method uses alternating least squares (ALS) algorithm to train a matrix factorization model given an JavaRDD
	 * of ratings given by users to some products.
	 *
	 * @param trainingDataset         Training dataset as a JavaRDD of Ratings
	 * @param rank                    Number of latent factors
	 * @param noOfIterations          Number of iterations
	 * @param regularizationParameter Regularization parameter
	 * @param noOfBlocks              Level of parallelism (auto configure = -1)
	 * @return Matrix factorization model
	 */
	public MatrixFactorizationModel trainExplicit(JavaRDD<Rating> trainingDataset, int rank, int noOfIterations,
	                                              double regularizationParameter, int noOfBlocks) {

		return ALS.train(trainingDataset.rdd(), rank, noOfIterations, regularizationParameter, noOfBlocks);
	}

	/**
	 * This method uses alternating least squares (ALS) algorithm to train a matrix factorization model given an JavaRDD
	 * of 'implicit preferences' given by users to some products.
	 *
	 * @param trainingDataset         Training dataset as a JavaRDD of Ratings
	 * @param rank                    Number of latent factors
	 * @param noOfIterations          Number of iterations
	 * @param regularizationParameter Regularization parameter
	 * @param confidenceParameter     Confidence parameter
	 * @param noOfBlocks              Level of parallelism (auto configure = -1)
	 * @return Matrix factorization model
	 */
	public MatrixFactorizationModel trainImplicit(JavaRDD<Rating> trainingDataset, int rank, int noOfIterations,
	                                              double regularizationParameter, double confidenceParameter,
	                                              int noOfBlocks) {

		return ALS.trainImplicit(trainingDataset.rdd(), rank, noOfIterations, regularizationParameter, noOfBlocks,
		                         confidenceParameter);
	}
	
	public JavaDoubleRDD test(final MatrixFactorizationModel model, JavaRDD<Rating> testData) {
		// Evaluate the model on rating data
	    JavaRDD<Tuple2<Object, Object>> userProducts = testData.map(
	      new Function<Rating, Tuple2<Object, Object>>() {
			private static final long serialVersionUID = -3264552286094314165L;

			public Tuple2<Object, Object> call(Rating r) {
	          return new Tuple2<Object, Object>(r.user(), r.product());
	        }
	      }
	    );
	    JavaPairRDD<Tuple2<Integer, Integer>, Double> predictions = JavaPairRDD.fromJavaRDD(
	      model.predict(JavaRDD.toRDD(userProducts)).toJavaRDD().map(
	        new Function<Rating, Tuple2<Tuple2<Integer, Integer>, Double>>() {
				private static final long serialVersionUID = -2499038680234103588L;

			public Tuple2<Tuple2<Integer, Integer>, Double> call(Rating r){
	            return new Tuple2<Tuple2<Integer, Integer>, Double>(
	              new Tuple2<Integer, Integer>(r.user(), r.product()), r.rating());
	          }
	        }
	    ));
	    JavaRDD<Tuple2<Double, Double>> ratesAndPreds = 
	      JavaPairRDD.fromJavaRDD(testData.map(
	        new Function<Rating, Tuple2<Tuple2<Integer, Integer>, Double>>() {
				private static final long serialVersionUID = 4113775238540885102L;

			public Tuple2<Tuple2<Integer, Integer>, Double> call(Rating r){
	            return new Tuple2<Tuple2<Integer, Integer>, Double>(
	              new Tuple2<Integer, Integer>(r.user(), r.product()), r.rating());
	          }
	        }
	    )).join(predictions).values();
	    JavaDoubleRDD ratingsOfTestData = JavaDoubleRDD.fromRDD(ratesAndPreds.map(
	      new Function<Tuple2<Double, Double>, Object>() {
			private static final long serialVersionUID = -5530552459744897905L;

			public Object call(Tuple2<Double, Double> pair) {
	          Double err = pair._1() - pair._2();
	          return err * err;
	        }
	      }
	    ).rdd());
		return ratingsOfTestData;
	}

	/**
	 * This method recommends products for a given user.
	 *
	 * @param model             Matrix factorization model
	 * @param userId            The user to recommend products to
	 * @param numberOfProducts  Number of products to return
	 * @return                  List of productIds recommended to a given user
	 * @throws MLModelHandlerException 
	 */
    public static List<Integer> recommendProducts(final MatrixFactorizationModel model, int userId, int numberOfProducts)
            throws MLModelHandlerException {
        try {
            Rating[] recommendations = model.recommendProducts(userId, numberOfProducts);
            List<Integer> productList = new ArrayList<Integer>();

            for (Rating rating : recommendations) {
                productList.add(rating.product());
            }

            return productList;
        } catch (NoSuchElementException e) {
            throw new MLModelHandlerException("Invalid user id: " + userId);
        }
    }

    /**
     * This method recommends users for a given product. (i.e. the users who are most likely to be interested in the
     * given product.
     *
     * @param model Matrix factorization model
     * @param productId The product to recommend users to
     * @param numberOfUsers Number of users to return
     * @return List of userIds recommended to a given product
     */
    public static List<Integer> recommendUsers(final MatrixFactorizationModel model, int productId, int numberOfUsers)
            throws MLModelHandlerException {
        try {
            List<Integer> userList = new ArrayList<Integer>();
            Rating[] recommendations = model.recommendUsers(productId, numberOfUsers);

            for (Rating rating : recommendations) {
                userList.add(rating.user());
            }

            return userList;
        } catch (NoSuchElementException e) {
            throw new MLModelHandlerException("Invalid product id: " + productId);
        }
    }
}
