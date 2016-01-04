/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.ml.rest.api;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.apache.hadoop.fs.InvalidRequestException;
import org.apache.http.HttpHeaders;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.ml.commons.constants.MLConstants;
import org.wso2.carbon.ml.commons.domain.MLModel;
import org.wso2.carbon.ml.commons.domain.MLModelData;
import org.wso2.carbon.ml.commons.domain.MLStorage;
import org.wso2.carbon.ml.commons.domain.ModelSummary;
import org.wso2.carbon.ml.core.exceptions.MLModelBuilderException;
import org.wso2.carbon.ml.core.exceptions.MLModelHandlerException;
import org.wso2.carbon.ml.core.exceptions.MLModelPublisherException;
import org.wso2.carbon.ml.core.exceptions.MLPmmlExportException;
import org.wso2.carbon.ml.core.impl.MLModelHandler;
import org.wso2.carbon.ml.core.utils.MLUtils;
import org.wso2.carbon.ml.rest.api.model.MLErrorBean;
import org.wso2.carbon.ml.rest.api.model.MLResponseBean;

/**
 * This class is to handle REST verbs GET , POST and DELETE.
 */
@Path("/models")
public class ModelApiV10 extends MLRestAPI {

    private static final Log logger = LogFactory.getLog(ModelApiV10.class);
    private MLModelHandler mlModelHandler;

    public ModelApiV10() {
        mlModelHandler = new MLModelHandler();
    }

    @OPTIONS
    public Response options() {
        return Response.ok().header(HttpHeaders.ALLOW, "GET POST DELETE").build();
    }

    /**
     * Create a new Model.
     * @param model {@link org.wso2.carbon.ml.commons.domain.MLModelData} object
     * @return JSON of {@link org.wso2.carbon.ml.commons.domain.MLModelData} object
     */
    @POST
    @Produces("application/json")
    @Consumes("application/json")
    public Response createModel(MLModelData model) {
        if (model.getAnalysisId() == 0 || model.getVersionSetId() == 0) {
            logger.error("Required parameters missing");
            return Response.status(Response.Status.BAD_REQUEST).entity("Required parameters missing").build();
        }
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        try {
            int tenantId = carbonContext.getTenantId();
            String userName = carbonContext.getUsername();
            model.setTenantId(tenantId);
            model.setUserName(userName);
            MLModelData insertedModel = mlModelHandler.createModel(model);
            return Response.ok(insertedModel).build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg("Error occurred while creating a model : " + model, e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Create a new model storage
     * @param modelId Unique id of the model
     * @param storage {@link org.wso2.carbon.ml.commons.domain.MLStorage} object
     */
    @POST
    @Path("/{modelId}/storages")
    @Produces("application/json")
    @Consumes("application/json")
    public Response addStorage(@PathParam("modelId") long modelId, MLStorage storage) {
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            mlModelHandler.addStorage(modelId, storage);
            return Response.ok().build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while adding storage for the model [id] %s of tenant [id] %s and [user] %s .",
                    modelId, tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Build the model
     * @param modelId Unique id of the model to be built.
     */
    @POST
    @Path("/{modelId}")
    @Produces("application/json")
    @Consumes("application/json")
    public Response buildModel(@PathParam("modelId") long modelId) {
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            mlModelHandler.buildModel(tenantId, userName, modelId);
            return Response.ok().build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while building the model [id] %s of tenant [id] %s and [user] %s .", modelId,
                    tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        } catch (MLModelBuilderException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while building the model [id] %s of tenant [id] %s and [user] %s .", modelId,
                    tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Publish the model to ML registry
     * @param modelId Unique id of the model to be published
     * @return JSON of {@link org.wso2.carbon.ml.rest.api.model.MLResponseBean} containing the published location of the model
     */
    @POST
    @Path("/{modelId}/publish")
    @Produces("application/json")
    @Consumes("application/json")
    public Response publishModel(@PathParam("modelId") long modelId) {
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            String registryPath = mlModelHandler.publishModel(tenantId, userName, modelId,MLModelHandler.Format.SERIALIZED);
            return Response.ok(new MLResponseBean(registryPath)).build();
        } catch (InvalidRequestException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while publishing the model [id] %s of tenant [id] %s and [user] %s .", modelId,
                    tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.BAD_REQUEST).entity(new MLErrorBean(e.getMessage())).build();
        } catch (MLModelPublisherException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while publishing the model [id] %s of tenant [id] %s and [user] %s .", modelId,
                    tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while publishing the model [id] %s of tenant [id] %s and [user] %s .", modelId,
                    tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        } catch (MLPmmlExportException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while publishing the model [id] %s of tenant [id] %s and [user] %s .", modelId,
                    tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Predict using a file and return as a list of predicted values.
     * @param modelId Unique id of the model
     * @param dataFormat Data format of the file (CSV or TSV)
     * @param inputStream File input stream generated from the file used for predictions
     * @return JSON array of predictions
     */
    @POST
    @Path("/predict")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response predict(@Multipart("modelId") long modelId, @Multipart("dataFormat") String dataFormat,
                            @Multipart("file") InputStream inputStream) {
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            // validate input parameters
            // if it is a file upload, check whether the file is sent
            if (inputStream == null || inputStream.available() == 0) {
                String msg = String.format(
                        "Error occurred while reading the file for model [id] %s of tenant [id] %s and [user] %s .",
                        modelId, tenantId, userName);
                logger.error(msg);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(msg)).build();
            }
            List<?> predictions = mlModelHandler.predict(tenantId, userName, modelId, dataFormat, inputStream);
            return Response.ok(predictions).build();
        } catch (IOException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while reading the file for model [id] %s of tenant [id] %s and [user] %s.",
                    modelId, tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.BAD_REQUEST).entity(new MLErrorBean(e.getMessage())).build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(
                    String.format("Error occurred while predicting from model [id] %s of tenant [id] %s and [user] %s.",
                                  modelId, tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                           .build();
        }
    }

    /**
     * Predict using a file and return predictions as a CSV.
     * @param modelId Unique id of the model
     * @param dataFormat Data format of the file (CSV or TSV)
     * @param columnHeader Whether the file contains the column header as the first row (YES or NO)
     * @param inputStream Input stream generated from the file used for predictions
     * @return A file as a {@link javax.ws.rs.core.StreamingOutput}
     */
    @POST
    @Path("/predictionStreams")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response streamingPredict(@Multipart("modelId") long modelId, @Multipart("dataFormat") String dataFormat,
                                     @Multipart("columnHeader") String columnHeader, @Multipart("file") InputStream inputStream) {
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            // validate input parameters
            // if it is a file upload, check whether the file is sent
            if (inputStream == null || inputStream.available() == 0) {
                String msg = String.format(
                        "Error occurred while reading the file for model [id] %s of tenant [id] %s and [user] %s .",
                        modelId, tenantId, userName);
                logger.error(msg);
                return Response.status(Response.Status.BAD_REQUEST).entity(new MLErrorBean(msg)).build();
            }
            final String predictions = mlModelHandler.streamingPredict(tenantId, userName, modelId, dataFormat,
                    columnHeader, inputStream);
            StreamingOutput stream = new StreamingOutput() {
                @Override
                public void write(OutputStream outputStream) throws IOException {
                    Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
                    writer.write(predictions);
                    writer.flush();
                    writer.close();
                }
            };
            return Response
                    .ok(stream)
                    .header("Content-disposition",
                            "attachment; filename=Predictions_" + modelId + "_" + MLUtils.getDate() + MLConstants.CSV)
                    .build();
        } catch (IOException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while reading the file for model [id] %s of tenant [id] %s and [user] %s.",
                    modelId, tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.BAD_REQUEST).entity(new MLErrorBean(e.getMessage())).build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while predicting from model [id] %s of tenant [id] %s and [user] %s.", modelId,
                    tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Make predictions using a model
     * @param modelId Unique id of the model
     * @param data List of string arrays containing the feature values used for predictions
     * @return JSON array of predicted values
     */
    @POST
    @Path("/{modelId}/predict")
    @Produces("application/json")
    @Consumes("application/json")
    public Response predict(@PathParam("modelId") long modelId, List<String[]> data) {
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            long t1 = System.currentTimeMillis();
            List<?> predictions = mlModelHandler.predict(tenantId, userName, modelId, data);
            logger.info(String.format("Prediction from model [id] %s finished in %s seconds.", modelId,
                    (System.currentTimeMillis() - t1) / 1000.0));
            return Response.ok(predictions).build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while predicting from model [id] %s of tenant [id] %s and [user] %s.", modelId,
                    tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Get the model data
     * @param modelName Name of the model
     * @return JSON of {@link org.wso2.carbon.ml.commons.domain.MLModelData} object
     */
    @GET
    @Path("/{modelId}/getRecommendations/{userId}/{noOfProducts}")
    @Produces("application/json")
    public Response getRecommendations(@PathParam("modelId") long modelId,
                                       @PathParam("userId") int userId,
                                       @PathParam("noOfProducts") int noOfProducts) {

        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            List<?> recommendations =
                    mlModelHandler.getProductRecommendations(tenantId, userName, modelId, userId, noOfProducts);
            return Response.ok(recommendations).build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format("Error occurred while getting recommendations from model [id] %s of tenant [id] %s and [user] %s.",
                                                     modelId, tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                           .build();
        }
    }

    @GET
    @Path("/{modelName}")
    @Produces("application/json")
    public Response getModel(@PathParam("modelName") String modelName) {
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            MLModelData model = mlModelHandler.getModel(tenantId, userName, modelName);
            if (model == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(model).build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while retrieving a model [name] %s of tenant [id] %s and [user] %s .", modelName,
                    tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Get all models
     * @return JSON array of {@link org.wso2.carbon.ml.commons.domain.MLModelData} objects
     */
    @GET
    @Produces("application/json")
    public Response getAllModels() {
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            List<MLModelData> models = mlModelHandler.getAllModels(tenantId, userName);
            return Response.ok(models).build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(
                    String.format("Error occurred while retrieving all models of tenant [id] %s and [user] %s .",
                            tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Delete a model
     * @param modelId Unique id of the model
     */
    @DELETE
    @Path("/{modelId}")
    @Produces("application/json")
    public Response deleteModel(@PathParam("modelId") long modelId) {
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            mlModelHandler.deleteModel(tenantId, userName, modelId);
            auditLog.info(String.format("User [name] %s of tenant [id] %s deleted a model [id] %s ", userName,
                    tenantId, modelId));
            return Response.ok().build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while deleting a model [id] %s of tenant [id] %s and [user] %s .", modelId,
                    tenantId, userName), e);
            logger.error(msg, e);
            auditLog.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Get the model summary
     * @param modelId Unique id of the model
     * @return JSON of {@link org.wso2.carbon.ml.commons.domain.ModelSummary} object
     */
    @GET
    @Path("/{modelId}/summary")
    @Produces("application/json")
    @Consumes("application/json")
    public Response getModelSummary(@PathParam("modelId") long modelId) {
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            ModelSummary modelSummary = mlModelHandler.getModelSummary(modelId);
            return Response.ok(modelSummary).build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while retrieving summary of the model [id] %s of tenant [id] %s and [user] %s .",
                    modelId, tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Download the model
     * @param modelName Name of the model
     * @return A {@link org.wso2.carbon.ml.commons.domain.MLModel} as a {@link javax.ws.rs.core.StreamingOutput}
     */
    @GET
    @Path("/{modelName}/export")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response exportModel(@PathParam("modelName") String modelName) {

        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            MLModelData model = mlModelHandler.getModel(tenantId, userName, modelName);
            if (model != null) {
                final MLModel generatedModel = mlModelHandler.retrieveModel(model.getId());
                StreamingOutput stream = new StreamingOutput() {
                    public void write(OutputStream outputStream) throws IOException {
                        ObjectOutputStream out = new ObjectOutputStream(outputStream);
                        out.writeObject(generatedModel);
                    }
                };
                return Response.ok(stream).header("Content-disposition", "attachment; filename=" + modelName).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while retrieving model [name] %s of tenant [id] %s and [user] %s .", modelName,
                    tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }
}
