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

import java.util.List;
import java.util.Map;

import org.springframework.web.reactive.function.client.WebClient;

public class RoleClient extends ServiceClient {

    public RoleClient(String baseUrl, String jwt) {
        super(baseUrl, jwt);
    }

    public RoleClient(String baseUrl, WebClient client) {
        super(baseUrl, client);
    }

    public List<String> getAllRoles(int offset, int limit) {
        Map<String, Object> queryParams = buildPagingParams(offset, limit);
        return doGet("/roles", Map.of(), queryParams, List.class);
    }
}
