/*
 * Copyright (c) 2013-2014 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vtn.javaapi.resources.openstack;

import java.sql.Connection;
import java.sql.SQLException;

import com.google.gson.JsonObject;
import org.opendaylight.vtn.core.util.Logger;
import org.opendaylight.vtn.javaapi.RestResource;
import org.opendaylight.vtn.javaapi.annotation.UNCField;
import org.opendaylight.vtn.javaapi.annotation.UNCVtnService;
import org.opendaylight.vtn.javaapi.constants.VtnServiceConsts;
import org.opendaylight.vtn.javaapi.constants.VtnServiceJsonConsts;
import org.opendaylight.vtn.javaapi.init.VtnServiceInitManager;
import org.opendaylight.vtn.javaapi.ipc.enums.UncCommonEnum;
import org.opendaylight.vtn.javaapi.ipc.enums.UncCommonEnum.UncResultCode;
import org.opendaylight.vtn.javaapi.openstack.beans.FreeCounterBean;
import org.opendaylight.vtn.javaapi.openstack.beans.VBridgeBean;
import org.opendaylight.vtn.javaapi.openstack.beans.VtnBean;
import org.opendaylight.vtn.javaapi.openstack.constants.VtnServiceOpenStackConsts;
import org.opendaylight.vtn.javaapi.openstack.convertor.VbrResourcesGenerator;
import org.opendaylight.vtn.javaapi.openstack.dao.DestinationControllerDao;
import org.opendaylight.vtn.javaapi.openstack.dao.VBridgeDao;
import org.opendaylight.vtn.javaapi.openstack.dao.VtnDao;
import org.opendaylight.vtn.javaapi.openstack.dbmanager.ResourceIdManager;
import org.opendaylight.vtn.javaapi.openstack.validation.NetworkResourceValidator;
import org.opendaylight.vtn.javaapi.resources.AbstractResource;

/**
 * Resource class for handling POST requests for Network
 */
@UNCVtnService(path = VtnServiceOpenStackConsts.NETWORKS_PATH)
public class NetworksResource extends AbstractResource {

	/* Logger instance */
	private static final Logger LOG = Logger.getLogger(NetworksResource.class
			.getName());

	@UNCField(VtnServiceOpenStackConsts.TENANT_ID)
	private String tenantId;

	/**
	 * Constructor that initialize the validation instance for current resource
	 * instance
	 */
	public NetworksResource() {
		setValidator(new NetworkResourceValidator(this));
	}

	/**
	 * Getter of tenantId
	 * 
	 * @return
	 */
	public String getTenantId() {
		return tenantId;
	}

	/**
	 * Handler method for POST operation of Network
	 * 
	 * @see org.opendaylight.vtn.javaapi.resources.AbstractResource#post(com
	 *      .google.gson.JsonObject)
	 */
	@Override
	public int post(JsonObject requestBody) {
		LOG.trace("Start NetworksResource#post()");

		int errorCode = UncResultCode.UNC_SERVER_ERROR.getValue();

		boolean isCommitRequired = false;
		String generatedVbrName = null;
		Connection connection = null;

		try {
			connection = VtnServiceInitManager.getDbConnectionPoolMap()
					.getConnection();

			/*
			 * Check for instances that they exists or not, if not then return
			 * 404 error
			 */
			if (checkForNotFoundResources(connection)) {
				final ResourceIdManager resourceIdManager = new ResourceIdManager();
				int counter = -1;
				/*
				 * auto-generation process, based on the availability of "id"
				 * parameter in request body
				 */
				if (!requestBody.has(VtnServiceOpenStackConsts.ID)) {
					LOG.info("Resource id auto-generation is required.");

					final FreeCounterBean freeCounterBean = new FreeCounterBean();
					freeCounterBean
							.setResourceId(VtnServiceOpenStackConsts.NETWORK_RES_ID);
					freeCounterBean.setVtnName(getTenantId());

					counter = resourceIdManager.getResourceCounter(connection,
							freeCounterBean);
					if (counter != -1) {
						LOG.debug("Resource id auto-generation is successfull : "
								+ counter);
						// if id is generated successfully
						generatedVbrName = VtnServiceOpenStackConsts.VBR_PREFIX
								+ counter;
						requestBody.addProperty(VtnServiceOpenStackConsts.ID,
								generatedVbrName);
					} else {
						LOG.error("Resource id auto-generation is failed.");
					}
				} else {
					LOG.info("Resource id auto-generation is not required.");
					counter = 0;
					generatedVbrName = requestBody.get(
							VtnServiceOpenStackConsts.ID).getAsString();
				}

				LOG.debug("Counter : " + counter);
				LOG.debug("vbr_name : " + generatedVbrName);

				if (counter >= 0 && setControllerId(connection, requestBody)) {
					/*
					 * resource insertion in database, if is is successful then
					 * continue to execute operations at UNC. Otherwise return
					 * HTTP 409
					 */
					final VBridgeBean vBridgeBean = new VBridgeBean();
					vBridgeBean.setVbrId(counter);
					vBridgeBean.setVtnName(getTenantId());
					vBridgeBean.setVbrName(generatedVbrName);

					final VBridgeDao vBridgeDao = new VBridgeDao();
					final int status = vBridgeDao.insert(connection,
							vBridgeBean);

					if (status == 1) {
						LOG.info("Resource insertion successful at database operation.");

						final RestResource restResource = new RestResource();

						errorCode = createVBridge(requestBody, restResource);

						if (errorCode == UncCommonEnum.UncResultCode.UNC_SUCCESS
								.getValue()) {
							LOG.info("vBridge creation is successful at UNC.");
							isCommitRequired = true;
							if (counter != 0) {
								final JsonObject response = new JsonObject();
								response.addProperty(
										VtnServiceOpenStackConsts.ID,
										generatedVbrName);
								setInfo(response);
							}
						} else {
							LOG.error("vBridge creation is failed at UNC.");
						}
						checkForSpecificErrors(restResource.getInfo());
					} else {
						LOG.error("Resource insertion failed at database operation.");
					}
				} else {
					LOG.error("Error occurred while generation of id or setting controller_id");
				}
			} else {
				LOG.error("Resource not found error.");
			}

			/*
			 * If all processing are OK, the commit all the database transaction
			 * made for current connection. Otherwise do the roll-back
			 */
			if (isCommitRequired) {
				// connection.commit();
				setOpenStackConnection(connection);
				LOG.info("Resource insertion successful in database.");
			} else {
				connection.rollback();
				LOG.info("Resource insertion is roll-backed.");
			}

			/*
			 * set response, if it is not set during processing for create
			 * tenant
			 */
			if (errorCode != UncResultCode.UNC_SUCCESS.getValue()) {
				if (getInfo() == null) {
					createErrorInfo(UncResultCode.UNC_INTERNAL_SERVER_ERROR
							.getValue());
				}
			}
		} catch (final SQLException exception) {
			LOG.error(exception, "Internal server error : " + exception);
			errorCode = UncResultCode.UNC_SERVER_ERROR.getValue();
			if (exception.getSQLState().equalsIgnoreCase(
					VtnServiceOpenStackConsts.CONFLICT_SQL_STATE)) {
				LOG.error("Conflict found during creation of resource");
				createErrorInfo(
						UncResultCode.UNC_CONFLICT_FOUND.getValue(),
						getCutomErrorMessage(
								UncResultCode.UNC_CONFLICT_FOUND.getMessage(),
								VtnServiceOpenStackConsts.NET_ID,
								generatedVbrName));
			} else {
				createErrorInfo(UncResultCode.UNC_INTERNAL_SERVER_ERROR
						.getValue());
			}
		} finally {
			if (connection != null && !isCommitRequired) {
				try {
					connection.rollback();
					LOG.info("roll-back successful.");
				} catch (final SQLException e) {
					LOG.error(e, "Rollback error : " + e);
				}
				LOG.info("Free connection...");
				VtnServiceInitManager.getDbConnectionPoolMap().freeConnection(
						connection);
			}
		}
		LOG.trace("Complete NetworksResource#post()");
		return errorCode;
	}

	/**
	 * Create VBridge at UNC
	 * 
	 * @param requestBody
	 *            - OpenStack request body
	 * @param restResource
	 *            - RestResource instance
	 * @return - erorrCode, 200 for Success
	 */
	private int createVBridge(JsonObject requestBody,
			final RestResource restResource) {
		/*
		 * Create request body for vBridge creation
		 */
		final JsonObject vbrRequestBody = VbrResourcesGenerator
				.getCreateVbrRequestBody(requestBody);
		/*
		 * execute create vBridge request
		 */
		StringBuilder sb = new StringBuilder();
		sb.append(VtnServiceOpenStackConsts.VTN_PATH);
		sb.append(VtnServiceOpenStackConsts.URI_CONCATENATOR);
		sb.append(getTenantId());
		sb.append(VtnServiceOpenStackConsts.VBRIDGE_PATH);

		restResource.setPath(sb.toString());
		restResource.setSessionID(getSessionID());
		restResource.setConfigID(getConfigID());

		return restResource.post(vbrRequestBody);
	}

	/**
	 * Retrieve controller_id from database and add the parameter in request
	 * body
	 * 
	 * @param connection
	 *            - DB Connection instance
	 * @param requestBody
	 *            - JSON request body
	 * @return - true, if controller_id is set
	 * @throws SQLException
	 */
	private boolean setControllerId(Connection connection,
			JsonObject requestBody) throws SQLException {
		boolean controllerAvailable = true;
		final DestinationControllerDao destControllerDao = new DestinationControllerDao();
		final String controllerId = destControllerDao
				.getDestinationController(connection);

		if (controllerId != null && !controllerId.isEmpty()) {
			LOG.info("Database retrieval is successful for Controller id : "
					+ controllerId);
			requestBody.addProperty(VtnServiceJsonConsts.CONTROLLERID,
					controllerId);
		} else {
			LOG.error("Database retrieval is failed for Controller id.");
			controllerAvailable = false;
			createErrorInfo(
					UncResultCode.UNC_CTRL_NOT_FOUND.getValue(),
					getCutomErrorMessage(
							UncResultCode.UNC_CTRL_NOT_FOUND.getMessage(),
							VtnServiceJsonConsts.CONTROLLERID,
							VtnServiceConsts.EMPTY_STRING));
		}
		return controllerAvailable;
	}

	/**
	 * Checks that specified instances in URI exists in system or not. If they
	 * are not exists then prepare error JSON for 404 Not Found
	 * 
	 * @param connection
	 *            - Database Connection instance
	 * @return - true, only if all instances exist
	 * @throws SQLException
	 */
	private boolean checkForNotFoundResources(Connection connection)
			throws SQLException {
		boolean resourceFound = false;
		VtnBean vtnBean = new VtnBean();
		vtnBean.setVtnName(getTenantId());
		if (new VtnDao().isVtnFound(connection, vtnBean)) {
			resourceFound = true;
		} else {
			createErrorInfo(
					UncResultCode.UNC_NOT_FOUND.getValue(),
					getCutomErrorMessage(
							UncResultCode.UNC_NOT_FOUND.getMessage(),
							VtnServiceOpenStackConsts.TENANT_ID, getTenantId()));
		}
		return resourceFound;
	}
}
