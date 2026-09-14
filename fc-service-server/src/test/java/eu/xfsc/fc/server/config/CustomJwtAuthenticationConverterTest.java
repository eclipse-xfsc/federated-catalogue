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

import static eu.xfsc.fc.server.util.CommonConstants.ASSET_CREATE;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_DELETE;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_READ;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_UPDATE;
import static eu.xfsc.fc.server.util.CommonConstants.CATALOGUE_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_USER_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_CREATE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_DELETE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_READ_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_UPDATE_WITH_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Unit test for {@link CustomJwtAuthenticationConverter}, isolated from any Spring HTTP/security context.
 *
 * <p>Directly exercises {@code convert()} — the only public entry point, and thereby the only way to reach
 * the private {@code extractResourceRoles()} — against constructed JWTs, to determine which
 * {@link GrantedAuthority} set a given {@code resource_access} (or realm-level
 * {@code roles}) claim actually produces.</p>
 */
class CustomJwtAuthenticationConverterTest {

  private static final String RESOURCE_ID = "federated-catalogue";
  private static final String OTHER_RESOURCE_ID = "some-other-client";

  private final CustomJwtAuthenticationConverter converter = new CustomJwtAuthenticationConverter(RESOURCE_ID);

  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void attachLogAppender() {
    logAppender = new ListAppender<>();
    logAppender.start();
    converterLogger().addAppender(logAppender);
  }

  @AfterEach
  void detachLogAppender() {
    converterLogger().detachAppender(logAppender);
  }

  private static Logger converterLogger() {
    return (Logger) LoggerFactory.getLogger(CustomJwtAuthenticationConverter.class);
  }

  @Test
  void convert_resourceAccessWithAllFourFineGrainedRoles_producesAllFourAuthorities() {
    Jwt jwt = jwtWithResourceRoles(RESOURCE_ID, List.of(ASSET_CREATE, ASSET_READ, ASSET_UPDATE, ASSET_DELETE));

    AbstractAuthenticationToken token = converter.convert(jwt);

    Set<String> actualAuthorities = authorityStrings(token);
    assertEquals(
        Set.of(ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX, ASSET_UPDATE_WITH_PREFIX, ASSET_DELETE_WITH_PREFIX),
        actualAuthorities);
  }

  @Test
  void convert_resourceAccessWithSingleRole_producesExactlyOneAuthority() {
    Jwt jwt = jwtWithResourceRoles(RESOURCE_ID, List.of(ASSET_CREATE));

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertEquals(Set.of(ASSET_CREATE_WITH_PREFIX), authorityStrings(token));
  }

  @Test
  void convert_missingResourceAccessClaimAndNoRealmRoles_producesNoAuthorities() {
    Jwt jwt = jwtWithClaims(Map.of());

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertTrue(authorityStrings(token).isEmpty(),
        "No resource_access and no realm-level roles claim must yield an empty authority set");
  }

  @Test
  void convert_resourceAccessPresentButRoleListEmpty_producesNoAuthorities() {
    Jwt jwt = jwtWithResourceRoles(RESOURCE_ID, List.of());

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertTrue(authorityStrings(token).isEmpty(),
        "An empty roles list under the configured resource id must yield an empty authority set");
  }

  @Test
  void convert_resourceAccessForDifferentResourceId_rolesDoNotLeakIntoAuthorities() {
    // Roles are granted on a *different* Keycloak client id than the one this converter is configured for.
    Jwt jwt = jwtWithResourceRoles(OTHER_RESOURCE_ID, List.of(ASSET_CREATE, ASSET_READ, ASSET_UPDATE, ASSET_DELETE));

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertTrue(authorityStrings(token).isEmpty(),
        "Roles granted on a different resource id must not leak into this resource's authorities");
  }

  @Test
  void convert_realmLevelRolesClaim_whenResourceAccessAbsent_mapsKnownRealmRolesToLegacyAuthorities() {
    Jwt jwt = jwtWithClaims(Map.of("roles", List.of("gaia-x-admin", "gaia-x-notar", "gaia-x-business-owner")));

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertEquals(
        Set.of(CATALOGUE_ADMIN_ROLE_WITH_PREFIX, PARTICIPANT_ADMIN_ROLE_WITH_PREFIX,
            PARTICIPANT_USER_ADMIN_ROLE_WITH_PREFIX),
        authorityStrings(token));
  }

  @Test
  void convert_realmLevelRolesClaim_unknownRoleName_isSilentlyIgnored() {
    Jwt jwt = jwtWithClaims(Map.of("roles", List.of("some-unmapped-realm-role")));

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertTrue(authorityStrings(token).isEmpty(),
        "A realm role with no known mapping must not produce an authority");
  }

  @Test
  void convert_resourceAccessAndRealmRolesBothPresent_realmRolesAreDropped() {
    // resource_access is present (even with an empty/irrelevant resource entry), so the converter's
    // else-branch (which reads the top-level "roles" claim) is never reached: any realm-level roles
    // are silently lost whenever resource_access is present at all, regardless of its own content.
    Jwt jwt = jwtWithClaims(Map.of(
        "resource_access", Map.of(RESOURCE_ID, Map.of("roles", List.of(ASSET_READ))),
        "roles", List.of("gaia-x-admin")));

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertEquals(Set.of(ASSET_READ_WITH_PREFIX), authorityStrings(token),
        "Realm-level 'roles' must not be merged in once resource_access is present");
  }

  @Test
  void convert_resourceAccessPresentForDifferentResourceIdAndLegacyRolesPresent_producesNoAuthorities() {
    // Arrange: resource_access carries an entry only for a different client; a legacy top-level
    // "roles" claim also happens to be present. The legacy fallback must NOT fire here — resource_access
    // being present at all means the issuer is on the token-scoped-client-roles model, and reading the
    // legacy realm-level claim in that case would widen who can obtain catalogue-admin authority.
    Jwt jwt = jwtWithClaims(Map.of(
        "resource_access", Map.of(OTHER_RESOURCE_ID, Map.of("roles", List.of(ASSET_CREATE, ASSET_READ))),
        "roles", List.of("gaia-x-admin")));

    // Act
    AbstractAuthenticationToken token = converter.convert(jwt);

    // Assert
    assertTrue(authorityStrings(token).isEmpty(),
        "The legacy top-level roles fallback must not fire when resource_access is present but "
            + "has no entry for the configured resource id — only the WARN diagnostic fires");
  }

  @Test
  void convert_resourceAccessPresentForDifferentResourceIdAndLegacyRolesPresent_warnsWithResourceIdAndClientKeys() {
    // Arrange: same misconfiguration shape — resource_access carries an entry only for a
    // different client, so the configured resource id cannot be resolved from it.
    Jwt jwt = jwtWithClaims(Map.of(
        "resource_access", Map.of(OTHER_RESOURCE_ID, Map.of("roles", List.of(ASSET_CREATE))),
        "roles", List.of("gaia-x-admin")));

    // Act
    converter.convert(jwt);

    // Assert
    List<ILoggingEvent> warnEvents = logAppender.list.stream()
        .filter(event -> event.getLevel() == Level.WARN)
        .collect(Collectors.toList());
    assertEquals(1, warnEvents.size(), "Exactly one WARN must be emitted for the unresolved resource id");
    String formattedMessage = warnEvents.get(0).getFormattedMessage();
    assertTrue(formattedMessage.contains(RESOURCE_ID),
        "Warning must name the configured resource id so the misconfiguration is diagnosable: " + formattedMessage);
    assertTrue(formattedMessage.contains(OTHER_RESOURCE_ID),
        "Warning must name the client keys actually present in the token: " + formattedMessage);
  }

  @Test
  void convert_resourceAccessPresentForDifferentResourceIdAndLegacyRolesPresent_doesNotLeakOtherClientRoles() {
    // Arrange: same JWT shape as above; assert explicitly that the other client's fine-grained
    // roles never end up as authorities on this converter's token, regardless of the fallback.
    Jwt jwt = jwtWithClaims(Map.of(
        "resource_access", Map.of(OTHER_RESOURCE_ID, Map.of("roles", List.of(ASSET_CREATE, ASSET_READ))),
        "roles", List.of("gaia-x-admin")));

    // Act
    AbstractAuthenticationToken token = converter.convert(jwt);

    // Assert
    Set<String> actualAuthorities = authorityStrings(token);
    assertFalse(actualAuthorities.contains(ASSET_CREATE_WITH_PREFIX),
        "Fine-grained roles granted on a different resource id must never leak into this resource's authorities");
    assertFalse(actualAuthorities.contains(ASSET_READ_WITH_PREFIX),
        "Fine-grained roles granted on a different resource id must never leak into this resource's authorities");
  }

  private static Set<String> authorityStrings(AbstractAuthenticationToken token) {
    return token.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .collect(Collectors.toSet());
  }

  private static Jwt jwtWithResourceRoles(String resourceId, List<String> roles) {
    return jwtWithClaims(Map.of("resource_access", Map.of(resourceId, Map.of("roles", roles))));
  }

  private static Jwt jwtWithClaims(Map<String, Object> extraClaims) {
    Jwt.Builder builder = Jwt.withTokenValue("test-token-value")
        .header("alg", "none")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .subject("test-subject");
    extraClaims.forEach(builder::claim);
    return builder.build();
  }
}
