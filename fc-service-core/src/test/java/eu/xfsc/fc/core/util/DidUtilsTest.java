package eu.xfsc.fc.core.util;

import static eu.xfsc.fc.core.util.DidUtils.resolveWebUri;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.junit.jupiter.api.Test;

class DidUtilsTest {

  @Test
  void resolveWebUri_portAndPath_returnsHttpsWithPort() throws Exception {
    URI did = URI.create("did:web:example.com%3A3000:user:alice");

    URI result = resolveWebUri(did);

    assertEquals("https://example.com:3000/user/alice/did.json", result.toString());
  }

  @Test
  void resolveWebUri_hostOnly_returnsWellKnown() throws Exception {
    URI did = URI.create("did:web:compliance.lab.gaia-x.eu");

    URI result = resolveWebUri(did);

    assertEquals("https://compliance.lab.gaia-x.eu/.well-known/did.json", result.toString());
  }

  @Test
  void resolveWebUri_hostWithFragment_appendsFragment() throws Exception {
    URI did = URI.create("did:web:compliance.lab.gaia-x.eu#key-1");

    URI result = resolveWebUri(did);

    assertEquals("https://compliance.lab.gaia-x.eu/.well-known/did.json#key-1", result.toString());
  }

  @Test
  void resolveWebUri_deepPathWithFragment_resolvesFully() throws Exception {
    URI did = URI.create("did:web:integration.gxfs.dev:api:dynamic:did:cloudService1#key-1");

    URI result = resolveWebUri(did);

    assertEquals("https://integration.gxfs.dev/api/dynamic/did/cloudService1/did.json#key-1", result.toString());
  }

}
