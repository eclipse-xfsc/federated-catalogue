package eu.xfsc.fc.server.service;

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

import eu.xfsc.fc.core.dao.UserDao;
import eu.xfsc.fc.server.generated.controller.RolesApiDelegate;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Implementation of the {@link eu.xfsc.fc.server.generated.controller.RolesApiDelegate} interface.
 */
@Slf4j
@Service
public class RolesService implements RolesApiDelegate {

  @Autowired
  private UserDao userDao;

  /**
   * GET /roles : Get all registered roles in the catalogue.
   *
   * @return All roles (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<List<String>> getAllRoles() {
    List<String> roles = userDao.getAllRoles();
    log.debug("getAllRoles; returning {} roles", roles.size());
    return ResponseEntity.ok(roles);
  }
}
