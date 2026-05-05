package eu.xfsc.fc.core.dao.validation;

/** Reason a {@link ValidationResult} was marked outdated. */
public enum OutdatedReason {

  /** The asset's content was updated, superseding previous validation results. */
  ASSET_UPDATED,

  /** The asset was revoked or reached end-of-life, invalidating prior results. */
  ASSET_REVOKED
}
