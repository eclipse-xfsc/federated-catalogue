package eu.xfsc.fc.core.pojo;

/**
 * Intrinsic type of asset in a linked MR–HR pair.
 */
public enum AssetType {
  /** Machine-readable asset: the primary RDF or structured representation. */
  MACHINE_READABLE,
  /** Human-readable asset: a document (PDF, HTML, DOCX, plain text) linked from a machine-readable asset. */
  HUMAN_READABLE
}
