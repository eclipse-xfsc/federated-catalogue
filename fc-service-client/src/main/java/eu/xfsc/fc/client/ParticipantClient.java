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

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.web.reactive.function.client.WebClient;

import eu.xfsc.fc.api.generated.model.Participant;
import eu.xfsc.fc.api.generated.model.Participants;
import eu.xfsc.fc.api.generated.model.UserProfile;

public class ParticipantClient extends ServiceClient {
    
    public ParticipantClient(String baseUrl, String jwt) {
        super(baseUrl, jwt);
    }

    public ParticipantClient(String baseUrl, WebClient client) {
        super(baseUrl, client);
    }

    public Participant getParticipant(String participantId) {
        Map<String, Object> pathParams = Map.of("participantId", participantId);
        return doGet("/participants/{participantId}", pathParams, Map.of(), Participant.class);
    }

    public Participant getParticipant(String participantId, OAuth2AuthorizedClient authorizedClient) {
        Map<String, Object> pathParams = Map.of("participantId", participantId);
        return doGet("/participants/{participantId}", pathParams, Map.of(), Participant.class, authorizedClient);
    }

    public List<UserProfile> getParticipantUsers(String participantId) {
        Map<String, Object> pathParams = Map.of("participantId", participantId);
        Class<List<UserProfile>> reType = (Class<List<UserProfile>>)(Class<?>) List.class;
        return doGet("/participants/{participantId}/users", pathParams, Map.of(), reType);
    }

    public List<UserProfile> getParticipantUsers(String participantId, OAuth2AuthorizedClient authorizedClient) {
        Map<String, Object> pathParams = Map.of("participantId", participantId);
        Class<List<UserProfile>> reType = (Class<List<UserProfile>>)(Class<?>) List.class;
        return doGet("/participants/{participantId}/users", pathParams, Map.of(), reType, authorizedClient);
    }

    public List<Participant> getParticipants(int offset, int limit) {
        Map<String, Object> queryParams = buildPagingParams(offset, limit);
        Class<List<Participant>> reType = (Class<List<Participant>>)(Class<?>) List.class;
        return doGet("/participants", Map.of(), queryParams, reType);
    }

    public Participants getParticipants(int offset, int limit, OAuth2AuthorizedClient authorizedClient) {
        Map<String, Object> queryParams = buildPagingParams(offset, limit);
        return doGet("/participants", Map.of(), queryParams, Participants.class, authorizedClient);
    }

    public Participant addParticipant(String participantCredential) {
        return doPost("/participants", participantCredential, Map.of(), Map.of(), Participant.class);
    }

    public Participant addParticipant(String participantCredential, OAuth2AuthorizedClient authorizedClient) {
        return doPost("/participants", participantCredential, Map.of(), Map.of(), Participant.class, authorizedClient);
    }

    public Participant deleteParticipant(String participantId) {
        Map<String, Object> pathParams = Map.of("participantId", participantId);
        return doDelete("/participants/{participantId}", pathParams, Map.of(), Participant.class);
    }

    public Participant deleteParticipant(String participantId, OAuth2AuthorizedClient authorizedClient) {
        Map<String, Object> pathParams = Map.of("participantId", participantId);
        return doDelete("/participants/{participantId}", pathParams, Map.of(), Participant.class, authorizedClient);
    }

    public Participant updateParticipant(String participantId, String participantCredential) {
        Map<String, Object> pathParams = Map.of("participantId", participantId);
        return doPut("/participants/{participantId}", participantCredential, pathParams, Map.of(), Participant.class);
    }

    public Participant updateParticipant(String participantId, String participantCredential, OAuth2AuthorizedClient authorizedClient) {
        Map<String, Object> pathParams = Map.of("participantId", participantId);
        return doPut("/participants/{participantId}", participantCredential, pathParams, Map.of(), Participant.class, authorizedClient);
    }
}
