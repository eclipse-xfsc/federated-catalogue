package eu.xfsc.fc.core.pojo;

import java.time.Instant;

/**
 * Configuration for a registered trust framework.
 */
public record TrustFrameworkConfig(
    String id,
    String name,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt) {}
