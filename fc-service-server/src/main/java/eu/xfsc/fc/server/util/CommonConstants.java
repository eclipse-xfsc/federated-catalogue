package eu.xfsc.fc.server.util;

/**
 * Roles and permissions constant class.
 */
public final class CommonConstants {
  private CommonConstants() {}

  // Legacy composite roles
  public static final String SD_ADMIN_ROLE = "Ro-SD-A";
  public static final String CATALOGUE_ADMIN_ROLE = "Ro-MU-CA";
  public static final String PARTICIPANT_ADMIN_ROLE = "Ro-MU-A";
  public static final String PARTICIPANT_USER_ADMIN_ROLE = "Ro-PA-A";
  public static final String PREFIX = "ROLE_";
  public static final String CATALOGUE_ADMIN_ROLE_WITH_PREFIX = PREFIX + CATALOGUE_ADMIN_ROLE;
  public static final String PARTICIPANT_ADMIN_ROLE_WITH_PREFIX = PREFIX + PARTICIPANT_ADMIN_ROLE;
  public static final String PARTICIPANT_USER_ADMIN_ROLE_WITH_PREFIX = PREFIX + PARTICIPANT_USER_ADMIN_ROLE;

  // Fine-grained permission roles
  public static final String ASSET_CREATE = "ASSET_CREATE";
  public static final String ASSET_READ = "ASSET_READ";
  public static final String ASSET_UPDATE = "ASSET_UPDATE";
  public static final String ASSET_DELETE = "ASSET_DELETE";
  public static final String SCHEMA_CREATE = "SCHEMA_CREATE";
  public static final String SCHEMA_READ = "SCHEMA_READ";
  public static final String SCHEMA_UPDATE = "SCHEMA_UPDATE";
  public static final String SCHEMA_DELETE = "SCHEMA_DELETE";
  public static final String QUERY_EXECUTE = "QUERY_EXECUTE";
  public static final String ADMIN_ALL = "ADMIN_ALL";

  // Prefixed permission roles (Spring Security ROLE_ prefix)
  public static final String ADMIN_ALL_WITH_PREFIX = PREFIX + ADMIN_ALL;
}
