package eu.xfsc.fc.server.config;

/*-
 * ---license-start
 * fc-service-server
 * ---
 * Copyright (c) 2022 - 2026 Contributors to the Eclipse Foundation
 * ---
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 * ---license-end
 */

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenApi Spring config.
 */
@Configuration
@RequiredArgsConstructor
public class OpenApiConfig {
  private final Optional<BuildProperties> buildProperties;

  /**
   * The OpenApi Info bean config.
   */
  @Bean
  public OpenAPI openApiInfo() {
    String version;
    String securitySchemeName = "bearerAuth";

    if (buildProperties.isPresent()) {
      version = buildProperties.get().getVersion();
    } else {
      version = "Development Build";
    }

    return new OpenAPI().info(new Info().version(version).title("GAIA-X Federated Catalogue")
    		.description("This is the REST API of the Gaia-X catalogue.")
    		.license(new License().name("Apache 2.0").url("http://www.apache.org/licenses/LICENSE-2.0")))
    		.addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
            .components(
                    new Components()
                        .addSecuritySchemes(securitySchemeName,
                            new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                       )
            );
    }
  
}
