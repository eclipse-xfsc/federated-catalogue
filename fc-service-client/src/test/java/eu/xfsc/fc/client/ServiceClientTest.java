package eu.xfsc.fc.client;

/*-
 * ---license-start
 * fc-service-client
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServiceClientTest {
    private ServiceClient serviceClient;

    @BeforeEach
    public void setup() {
        serviceClient = new ServiceClient("http://localhost", (String) null) {};
    }

    @Test
    public void testBuildUriWithPathAndQueryParams() {
        String path = "/participants/{participantId}/users";
        Map<String, Object> pathParams = new HashMap<>();
        pathParams.put("participantId", "123");

        Map<String, Object> queryParams = new TreeMap<>();
        queryParams.put("age", 25);
        queryParams.put("status", "active");
        queryParams.put("role", "admin");

        UriBuilder uriBuilder = new DefaultUriBuilderFactory().builder();
        URI result = serviceClient.buildUri(uriBuilder, path, pathParams, queryParams);

        assertEquals("http://localhost/participants/123/users?age=25&role=admin&status=active", 
                     serviceClient.getUrl() + result.toString());
    }

    @Test
    public void testBuildUriWithEmptyQueryParams() {
        String path = "/participants/{participantId}/users";
        Map<String, Object> pathParams = new HashMap<>();
        pathParams.put("participantId", "123");

        Map<String, Object> queryParams = new TreeMap<>();
        queryParams.put("age", 25);
        queryParams.put("status", "active");
        queryParams.put("role", "");

        UriBuilder uriBuilder = new DefaultUriBuilderFactory().builder();
        URI result = serviceClient.buildUri(uriBuilder, path, pathParams, queryParams);

        assertEquals("http://localhost/participants/123/users?age=25&role=&status=active", 
                     serviceClient.getUrl() + result.toString());
    }

    @Test
    public void testBuildUriWithNoQueryParams() {
        String path = "/participants/{participantId}/users";
        Map<String, Object> pathParams = new HashMap<>();
        pathParams.put("participantId", "123");

        Map<String, Object> queryParams = new HashMap<>();

        UriBuilder uriBuilder = new DefaultUriBuilderFactory().builder();
        URI result = serviceClient.buildUri(uriBuilder, path, pathParams, queryParams);

        assertEquals("http://localhost/participants/123/users", serviceClient.getUrl() + result.toString());
    }
}
