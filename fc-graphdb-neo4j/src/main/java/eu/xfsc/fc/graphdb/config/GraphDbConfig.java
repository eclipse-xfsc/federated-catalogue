package eu.xfsc.fc.graphdb.config;

/*-
 * ---license-start
 * fc-graphdb-neo4j
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

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Logging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConditionalOnExpression("'${federated-catalogue.scope}'.equals('runtime')")
public class GraphDbConfig {

  @Value("${graphstore.neo4j.uri:${graphstore.uri}}")
    private String uri;
    @Value("${graphstore.user}")
    private String user;
    @Value("${graphstore.password}")
    private String password;

  // @Lazy + no eager session.run() — the Neo4j driver does not open a TCP connection at
  // construction; it only connects on first session(). n10s schema bootstrap was moved
  // out of this factory into Neo4jGraphStore.ensureInitialized() so that an unreachable
  // Neo4j container at boot does not crash the JVM in routing mode.
    @Bean(destroyMethod = "close")
    @Lazy
    public Driver driver() {
        Config config = Config.builder().withLogging(Logging.slf4j()).build();
      return GraphDatabase.driver(uri, AuthTokens.basic(user, password), config);
    }

}
