package eu.xfsc.fc.core.service.trustframework.compliance;

/**
 * Registry that maps client-type keys to {@link TrustFrameworkClient} implementations.
 *
 * <p>Callers resolve the correct client by passing the {@code clientType} from a
 * {@link TrustFrameworkProfileConfig} to {@link #resolve(String)}.
 */
public interface TrustFrameworkClientRegistry {

  /**
   * Returns the {@link TrustFrameworkClient} registered for the given client-type key.
   *
   * @param clientType the client-type key to look up
   * @return the matching client implementation; never {@code null}
   * @throws IllegalArgumentException when no client is registered for the given type
   */
  TrustFrameworkClient resolve(String clientType);
}
