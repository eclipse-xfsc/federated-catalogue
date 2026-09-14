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

import java.util.Map;

import org.springframework.web.reactive.function.client.WebClient;

import eu.xfsc.fc.api.generated.model.Session;

public class SessionClient extends ServiceClient {

    public SessionClient(String baseUrl, String jwt) {
        super(baseUrl, jwt);
    }

    public SessionClient(String baseUrl, WebClient client) {
        super(baseUrl, client);
    }

    public Session getCurrentSession() {
        return doGet("/session", Map.of(), Map.of(), Session.class);
    }

    public void deleteCurrentSession() {
        doDelete("/session", Map.of(), Map.of(), Void.class);
    }
}
