package eu.xfsc.fc.core.service.provenance;

import eu.xfsc.fc.core.dao.provenance.ProvenanceType;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.RdfClaim;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProvOTripleBuilderTest {

  private static final String PROV_NS = "http://www.w3.org/ns/prov#";
  private static final String ASSET_ID = "did:web:example:asset:v1";
  private static final String OBJECT_VALUE = "did:web:example:activity";

  static Stream<Arguments> provenanceTypeMappings() {
    return Stream.of(
        Arguments.of(ProvenanceType.CREATION, PROV_NS + "wasGeneratedBy"),
        Arguments.of(ProvenanceType.DERIVATION, PROV_NS + "wasDerivedFrom"),
        Arguments.of(ProvenanceType.ATTRIBUTION, PROV_NS + "wasAttributedTo"),
        Arguments.of(ProvenanceType.MODIFICATION, PROV_NS + "wasRevisionOf"),
        Arguments.of(ProvenanceType.GENERATION, PROV_NS + "generated"),
        Arguments.of(ProvenanceType.USAGE, PROV_NS + "used"),
        Arguments.of(ProvenanceType.ASSOCIATION, PROV_NS + "wasAssociatedWith"),
        Arguments.of(ProvenanceType.DELEGATION, PROV_NS + "actedOnBehalfOf")
    );
  }

  @ParameterizedTest
  @MethodSource("provenanceTypeMappings")
  void build_provenanceType_mapsToCorrectPredicate(ProvenanceType type, String expectedPredicate) {
    List<RdfClaim> triples = ProvOTripleBuilder.build(ASSET_ID, type, OBJECT_VALUE);

    assertEquals(1, triples.size());
    RdfClaim triple = triples.getFirst();
    assertEquals("<" + ASSET_ID + ">", triple.getSubjectString());
    assertEquals("<" + expectedPredicate + ">", triple.getPredicateString());
    assertEquals("<" + OBJECT_VALUE + ">", triple.getObjectString());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "> . <http://evil> <http://evil> <http://evil",  // angle bracket injection
      "http://example.com/path with spaces",           // whitespace
      "http://example.com/\"quoted\"",                 // double quote
      "http://example.com/{path}",                     // curly brace
      "relative/path"                                  // relative IRI (no scheme)
  })
  void build_invalidObjectValue_throwsClientException(String invalidObjectValue) {
    assertThrows(ClientException.class,
        () -> ProvOTripleBuilder.build(ASSET_ID, ProvenanceType.CREATION, invalidObjectValue));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "> . <http://evil> <http://evil> <http://evil",
      "did:web:example:asset with spaces:v1",
      "relative/asset/path"
  })
  void build_invalidAssetId_throwsClientException(String invalidAssetId) {
    assertThrows(ClientException.class,
        () -> ProvOTripleBuilder.build(invalidAssetId, ProvenanceType.CREATION, OBJECT_VALUE));
  }

  @org.junit.jupiter.api.Test
  void buildAll_multipleFacts_emitsOneTriplePerFactInOrder() {
    List<ProvenanceInfo> facts = List.of(
        new ProvenanceInfo(ProvenanceType.CREATION, "did:web:example:activity"),
        new ProvenanceInfo(ProvenanceType.ASSOCIATION, "did:web:example:agent"),
        new ProvenanceInfo(ProvenanceType.DELEGATION, "did:web:example:organisation"));

    List<RdfClaim> triples = ProvOTripleBuilder.buildAll(ASSET_ID, facts);

    assertEquals(3, triples.size());
    assertEquals("<" + PROV_NS + "wasGeneratedBy>", triples.get(0).getPredicateString());
    assertEquals("<" + PROV_NS + "wasAssociatedWith>", triples.get(1).getPredicateString());
    assertEquals("<" + PROV_NS + "actedOnBehalfOf>", triples.get(2).getPredicateString());
    assertEquals("<did:web:example:agent>", triples.get(1).getObjectString());
  }
}
