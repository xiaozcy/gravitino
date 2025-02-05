/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.gravitino.iceberg.service.rest;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.gravitino.credential.Credential;
import org.apache.gravitino.credential.CredentialConstants;
import org.apache.gravitino.credential.CredentialPropertyUtils;
import org.apache.gravitino.credential.CredentialProvider;
import org.apache.gravitino.credential.CredentialUtils;
import org.apache.gravitino.iceberg.service.IcebergCatalogWrapperManager;
import org.apache.gravitino.iceberg.service.IcebergObjectMapper;
import org.apache.gravitino.iceberg.service.IcebergRestUtils;
import org.apache.gravitino.iceberg.service.dispatcher.IcebergTableOperationDispatcher;
import org.apache.gravitino.iceberg.service.metrics.IcebergMetricsManager;
import org.apache.gravitino.metrics.MetricNames;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.exceptions.ServiceUnavailableException;
import org.apache.iceberg.rest.RESTUtil;
import org.apache.iceberg.rest.requests.CreateTableRequest;
import org.apache.iceberg.rest.requests.ReportMetricsRequest;
import org.apache.iceberg.rest.requests.UpdateTableRequest;
import org.apache.iceberg.rest.responses.LoadTableResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/v1/{prefix:([^/]*/)?}namespaces/{namespace}/tables")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class IcebergTableOperations {

  private static final Logger LOG = LoggerFactory.getLogger(IcebergTableOperations.class);

  @VisibleForTesting
  public static final String X_ICEBERG_ACCESS_DELEGATION = "X-Iceberg-Access-Delegation";

  private IcebergCatalogWrapperManager icebergCatalogWrapperManager;
  private IcebergMetricsManager icebergMetricsManager;

  private ObjectMapper icebergObjectMapper;
  private IcebergTableOperationDispatcher tableOperationDispatcher;

  @SuppressWarnings("UnusedVariable")
  @Context
  private HttpServletRequest httpRequest;

  @Inject
  public IcebergTableOperations(
      IcebergCatalogWrapperManager icebergCatalogWrapperManager,
      IcebergMetricsManager icebergMetricsManager,
      IcebergTableOperationDispatcher tableOperationDispatcher) {
    this.icebergCatalogWrapperManager = icebergCatalogWrapperManager;
    this.icebergMetricsManager = icebergMetricsManager;
    this.tableOperationDispatcher = tableOperationDispatcher;
    this.icebergObjectMapper = IcebergObjectMapper.getInstance();
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Timed(name = "list-table." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "list-table", absolute = true)
  public Response listTable(
      @PathParam("prefix") String prefix, @PathParam("namespace") String namespace) {
    return IcebergRestUtils.ok(
        icebergCatalogWrapperManager.getOps(prefix).listTable(RESTUtil.decodeNamespace(namespace)));
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Timed(name = "create-table." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "create-table", absolute = true)
  public Response createTable(
      @PathParam("prefix") String prefix,
      @PathParam("namespace") String namespace,
      CreateTableRequest createTableRequest,
      @HeaderParam(X_ICEBERG_ACCESS_DELEGATION) String accessDelegation) {
    boolean isCredentialVending = isCredentialVending(accessDelegation);
    String catalogName = IcebergRestUtils.getCatalogName(prefix);
    Namespace icebergNS = RESTUtil.decodeNamespace(namespace);
    LOG.info(
        "Create Iceberg table, catalog: {}, namespace: {}, create table request: {}, "
            + "accessDelegation: {}, isCredentialVending: {}",
        catalogName,
        icebergNS,
        createTableRequest,
        accessDelegation,
        isCredentialVending);
    LoadTableResponse loadTableResponse =
        tableOperationDispatcher.createTable(catalogName, icebergNS, createTableRequest);
    if (isCredentialVending) {
      return IcebergRestUtils.ok(injectCredentialConfig(prefix, loadTableResponse));
    } else {
      return IcebergRestUtils.ok(loadTableResponse);
    }
  }

  @POST
  @Path("{table}")
  @Produces(MediaType.APPLICATION_JSON)
  @Timed(name = "update-table." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "update-table", absolute = true)
  public Response updateTable(
      @PathParam("prefix") String prefix,
      @PathParam("namespace") String namespace,
      @PathParam("table") String table,
      UpdateTableRequest updateTableRequest) {
    if (LOG.isInfoEnabled()) {
      LOG.info(
          "Update Iceberg table, namespace: {}, table: {}, updateTableRequest: {}",
          namespace,
          table,
          SerializeUpdateTableRequest(updateTableRequest));
    }
    TableIdentifier tableIdentifier =
        TableIdentifier.of(RESTUtil.decodeNamespace(namespace), table);
    return IcebergRestUtils.ok(
        icebergCatalogWrapperManager
            .getOps(prefix)
            .updateTable(tableIdentifier, updateTableRequest));
  }

  @DELETE
  @Path("{table}")
  @Produces(MediaType.APPLICATION_JSON)
  @Timed(name = "drop-table." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "drop-table", absolute = true)
  public Response dropTable(
      @PathParam("prefix") String prefix,
      @PathParam("namespace") String namespace,
      @PathParam("table") String table,
      @DefaultValue("false") @QueryParam("purgeRequested") boolean purgeRequested) {
    LOG.info(
        "Drop Iceberg table, namespace: {}, table: {}, purgeRequested: {}",
        namespace,
        table,
        purgeRequested);
    TableIdentifier tableIdentifier =
        TableIdentifier.of(RESTUtil.decodeNamespace(namespace), table);
    if (purgeRequested) {
      icebergCatalogWrapperManager.getOps(prefix).purgeTable(tableIdentifier);
    } else {
      icebergCatalogWrapperManager.getOps(prefix).dropTable(tableIdentifier);
    }
    return IcebergRestUtils.noContent();
  }

  @GET
  @Path("{table}")
  @Produces(MediaType.APPLICATION_JSON)
  @Timed(name = "load-table." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "load-table", absolute = true)
  public Response loadTable(
      @PathParam("prefix") String prefix,
      @PathParam("namespace") String namespace,
      @PathParam("table") String table,
      @DefaultValue("all") @QueryParam("snapshots") String snapshots,
      @HeaderParam(X_ICEBERG_ACCESS_DELEGATION) String accessDelegation) {
    boolean isCredentialVending = isCredentialVending(accessDelegation);
    LOG.info(
        "Load iceberg table, namespace: {}, table: {}, access delegation: {}, "
            + "credential vending: {}",
        namespace,
        table,
        accessDelegation,
        isCredentialVending);
    // todo support snapshots
    TableIdentifier tableIdentifier =
        TableIdentifier.of(RESTUtil.decodeNamespace(namespace), table);
    LoadTableResponse loadTableResponse =
        icebergCatalogWrapperManager.getOps(prefix).loadTable(tableIdentifier);
    if (isCredentialVending) {
      return IcebergRestUtils.ok(injectCredentialConfig(prefix, loadTableResponse));
    } else {
      return IcebergRestUtils.ok(loadTableResponse);
    }
  }

  @HEAD
  @Path("{table}")
  @Produces(MediaType.APPLICATION_JSON)
  @Timed(name = "table-exists." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "table-exits", absolute = true)
  public Response tableExists(
      @PathParam("prefix") String prefix,
      @PathParam("namespace") String namespace,
      @PathParam("table") String table) {
    TableIdentifier tableIdentifier =
        TableIdentifier.of(RESTUtil.decodeNamespace(namespace), table);
    if (icebergCatalogWrapperManager.getOps(prefix).tableExists(tableIdentifier)) {
      return IcebergRestUtils.okWithoutContent();
    } else {
      return IcebergRestUtils.notExists();
    }
  }

  @POST
  @Path("{table}/metrics")
  @Produces(MediaType.APPLICATION_JSON)
  @Timed(name = "report-table-metrics." + MetricNames.HTTP_PROCESS_DURATION, absolute = true)
  @ResponseMetered(name = "report-table-metrics", absolute = true)
  public Response reportTableMetrics(
      @PathParam("prefix") String prefix,
      @PathParam("namespace") String namespace,
      @PathParam("table") String table,
      ReportMetricsRequest request) {
    icebergMetricsManager.recordMetric(request.report());
    return IcebergRestUtils.noContent();
  }

  private String SerializeUpdateTableRequest(UpdateTableRequest updateTableRequest) {
    try {
      return icebergObjectMapper.writeValueAsString(updateTableRequest);
    } catch (JsonProcessingException e) {
      LOG.warn("Serialize update table request failed", e);
      return updateTableRequest.toString();
    }
  }

  private LoadTableResponse injectCredentialConfig(
      String prefix, LoadTableResponse loadTableResponse) {
    CredentialProvider credentialProvider =
        icebergCatalogWrapperManager.getCredentialProvider(prefix);
    if (credentialProvider == null) {
      throw new NotSupportedException(
          "Doesn't support credential vending, please add "
              + CredentialConstants.CREDENTIAL_PROVIDER_TYPE
              + " to the catalog configurations");
    }
    Credential credential =
        CredentialUtils.vendCredential(
            credentialProvider, loadTableResponse.tableMetadata().location());
    if (credential == null) {
      throw new ServiceUnavailableException(
          "Couldn't generate credential for %s", credentialProvider.credentialType());
    }
    Map<String, String> credentialConfig = CredentialPropertyUtils.toIcebergProperties(credential);
    return LoadTableResponse.builder()
        .withTableMetadata(loadTableResponse.tableMetadata())
        .addAllConfig(loadTableResponse.config())
        .addAllConfig(credentialConfig)
        .build();
  }

  private boolean isCredentialVending(String accessDelegation) {
    if (StringUtils.isBlank(accessDelegation)) {
      return false;
    }
    if ("vended-credentials".equalsIgnoreCase(accessDelegation)) {
      return true;
    }
    if ("remote-signing".equalsIgnoreCase(accessDelegation)) {
      throw new UnsupportedOperationException(
          "Gravitino IcebergRESTServer doesn't support remote signing");
    } else {
      throw new IllegalArgumentException(
          X_ICEBERG_ACCESS_DELEGATION
              + ": "
              + accessDelegation
              + " is illegal, Iceberg REST spec supports:[vended-credentials,remote-signing], "
              + "Gravitino Iceberg REST server supports: vended-credentials");
    }
  }
}
