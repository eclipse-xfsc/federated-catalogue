package eu.xfsc.fc.core.dao.assets;

/**
 * Distinguishes whether an asset has RDF content or not.
 * This dimension is orthogonal to AssetType (which encodes MR-HR linking status).
 */
public enum ContentKind {
  /** Asset has RDF content */
  RDF,

  /** Asset has non-RDF content (e.g., PDF, JSON, binary) */
  NON_RDF
}
