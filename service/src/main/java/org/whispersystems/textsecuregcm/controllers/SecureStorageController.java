/*
 * Copyright 2013 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.controllers;

import io.dropwizard.auth.Auth;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.whispersystems.textsecuregcm.auth.AuthenticatedDevice;
import org.whispersystems.textsecuregcm.auth.ExternalServiceCredentials;
import org.whispersystems.textsecuregcm.auth.ExternalServiceCredentialsGenerator;
import org.whispersystems.textsecuregcm.configuration.SecureStorageServiceConfiguration;
import org.whispersystems.websocket.auth.ReadOnly;

@Path("/v1/storage")
@Tag(name = "Secure Storage")
public class SecureStorageController {

  private final ExternalServiceCredentialsGenerator storageServiceCredentialsGenerator;

  public static ExternalServiceCredentialsGenerator credentialsGenerator(final SecureStorageServiceConfiguration cfg) {
    return ExternalServiceCredentialsGenerator
        .builder(cfg.userAuthenticationTokenSharedSecret())
        .prependUsername(true)
        .build();
  }

  public SecureStorageController(ExternalServiceCredentialsGenerator storageServiceCredentialsGenerator) {
    this.storageServiceCredentialsGenerator = storageServiceCredentialsGenerator;
  }

  @GET
  @Path("/auth")
  @Produces(MediaType.APPLICATION_JSON)
  public ExternalServiceCredentials getAuth(@ReadOnly @Auth AuthenticatedDevice auth) {
    return storageServiceCredentialsGenerator.generateForUuid(auth.getAccount().getUuid());
  }
}
