package eu.xfsc.fc.core.service.trustframework.compliance;

import eu.xfsc.fc.core.pojo.ContentAccessor;

/**
 * SPI for trust-framework compliance check implementations.
 *
 * <p>Each implementation handles one client type (e.g. {@code "gaiax-ipfs"},
 * {@code "train"}). Implementations are registered via {@link TrustFrameworkClientRegistry}.
 */
public interface TrustFrameworkClient {

  /**
   * Returns the client-type key that this implementation handles.
   * Must match the {@code clientType} field in {@link TrustFrameworkProfileConfig}.
   */
  String clientType();

  /**
   * Performs a compliance check for the given credential against the specified profile configuration.
   *
   * @param credential the credential payload to check (verifiable presentation or self-description)
   * @param config     resolved profile configuration providing endpoint and trust-list parameters
   * @return the outcome of the compliance check; never {@code null}
   */
  ComplianceCheckOutcome check(ContentAccessor credential, TrustFrameworkProfileConfig config);
}
