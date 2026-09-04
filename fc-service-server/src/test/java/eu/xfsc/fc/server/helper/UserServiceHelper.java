package eu.xfsc.fc.server.helper;

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

import static eu.xfsc.fc.server.util.CommonConstants.CATALOGUE_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_USER_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_ADMIN_ROLE;

import java.util.ArrayList;
import java.util.List;
import org.keycloak.representations.idm.RoleRepresentation;

public class UserServiceHelper {
  public static List<RoleRepresentation> getAllRoles() {
    List<RoleRepresentation> roles = new ArrayList<>();
    roles.add(new RoleRepresentation(ASSET_ADMIN_ROLE, ASSET_ADMIN_ROLE, false));
    roles.add(new RoleRepresentation(CATALOGUE_ADMIN_ROLE, CATALOGUE_ADMIN_ROLE, false));
    roles.add(new RoleRepresentation(PARTICIPANT_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE, false));
    roles.add(new RoleRepresentation(PARTICIPANT_USER_ADMIN_ROLE, PARTICIPANT_USER_ADMIN_ROLE, false));
    return roles;
  }
}
