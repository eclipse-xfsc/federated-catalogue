package eu.xfsc.fc.core.service.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.pojo.FilteredClaims;
import eu.xfsc.fc.core.pojo.FilteredModel;
import eu.xfsc.fc.core.pojo.SdClaim;

/**
 * Unit tests for {@link ProtectedNamespaceFilter}.
 */
class ProtectedNamespaceFilterTest {

  private static final String NS =
      "https://projects.eclipse.org/projects/technology.xfsc/federated-catalogue/meta#";
  private static final String PREFIX = "fcmeta";
  private static final String NORMAL_SUBJ = "<https://example.org/subject1>";
  private static final String NORMAL_PRED = "<https://example.org/predicate1>";
  private static final String NORMAL_OBJ = "<https://example.org/object1>";

  private ProtectedNamespaceFilter filter;
  private ProtectedNamespaceProperties properties;

  @BeforeEach
  void setUp() {
    properties = new ProtectedNamespaceProperties();
    properties.setNamespace(NS);
    properties.setPrefix(PREFIX);
    filter = new ProtectedNamespaceFilter(properties);
  }

  @Test
  void filterClaimsProtectedSubject() {
    SdClaim claim = new SdClaim("<" + NS + "someSubject>", NORMAL_PRED, NORMAL_OBJ);
    FilteredClaims result = filter.filterClaims(List.of(claim), "test");
    assertTrue(result.claims().isEmpty());
    assertTrue(result.hasWarning());
  }

  @Test
  void filterClaimsProtectedPredicate() {
    SdClaim claim = new SdClaim(NORMAL_SUBJ, "<" + NS + "complianceResult>", NORMAL_OBJ);
    FilteredClaims result = filter.filterClaims(List.of(claim), "test");
    assertTrue(result.claims().isEmpty());
    assertTrue(result.hasWarning());
  }

  @Test
  void filterClaimsProtectedObjectIri() {
    SdClaim claim = new SdClaim(NORMAL_SUBJ, NORMAL_PRED, "<" + NS + "someValue>");
    FilteredClaims result = filter.filterClaims(List.of(claim), "test");
    assertTrue(result.claims().isEmpty());
    assertTrue(result.hasWarning());
  }

  @Test
  void filterClaimsLiteralObjectNotFiltered() {
    // Literal object containing namespace string — should NOT be filtered
    SdClaim claim = new SdClaim(NORMAL_SUBJ, NORMAL_PRED, "\"" + NS + "someValue\"");
    FilteredClaims result = filter.filterClaims(List.of(claim), "test");
    assertEquals(1, result.claims().size());
    assertFalse(result.hasWarning());
  }

  @Test
  void filterClaimsBlankNodeNotFiltered() {
    SdClaim claim = new SdClaim(NORMAL_SUBJ, NORMAL_PRED, "_:b0");
    FilteredClaims result = filter.filterClaims(List.of(claim), "test");
    assertEquals(1, result.claims().size());
    assertFalse(result.hasWarning());
  }

  @Test
  void filterClaimsNormalClaims() {
    SdClaim claim1 = new SdClaim(NORMAL_SUBJ, NORMAL_PRED, NORMAL_OBJ);
    SdClaim claim2 = new SdClaim("<https://example.org/s2>", "<https://example.org/p2>", "\"literal\"");
    FilteredClaims result = filter.filterClaims(List.of(claim1, claim2), "test");
    assertEquals(2, result.claims().size());
    assertFalse(result.hasWarning());
  }

  @Test
  void filterClaimsEmptyList() {
    List<SdClaim> input = List.of();
    FilteredClaims result = filter.filterClaims(input, "test");
    assertSame(input, result.claims());
    assertFalse(result.hasWarning());
  }

  @Test
  void filterClaimsNullList() {
    FilteredClaims result = filter.filterClaims(null, "test");
    assertNull(result.claims());
    assertFalse(result.hasWarning());
  }

  @Test
  void filterClaimsMixedClaims() {
    SdClaim normal = new SdClaim(NORMAL_SUBJ, NORMAL_PRED, NORMAL_OBJ);
    SdClaim protectedClaim = new SdClaim(NORMAL_SUBJ, "<" + NS + "complianceResult>", NORMAL_OBJ);
    SdClaim anotherNormal = new SdClaim("<https://example.org/s2>", NORMAL_PRED, "\"text\"");

    FilteredClaims result = filter.filterClaims(List.of(normal, protectedClaim, anotherNormal), "test");
    assertEquals(2, result.claims().size());
    assertTrue(result.claims().contains(normal));
    assertTrue(result.claims().contains(anotherNormal));
    assertTrue(result.hasWarning());
  }

  @Test
  void filterClaimsWarningContainsCountNamespaceAndTripleDetails() {
    SdClaim p1 = new SdClaim(NORMAL_SUBJ, "<" + NS + "complianceResult>", NORMAL_OBJ);
    SdClaim p2 = new SdClaim(NORMAL_SUBJ, "<" + NS + "validationTimestamp>", NORMAL_OBJ);
    FilteredClaims result = filter.filterClaims(List.of(p1, p2), "test");
    assertNotNull(result.warning());
    assertTrue(result.warning().contains("2 triple(s)"));
    assertTrue(result.warning().contains(NS));
    assertTrue(result.warning().contains("complianceResult"));
    assertTrue(result.warning().contains("validationTimestamp"));
  }

  @Test
  void filterModelProtectedStatements() {
    Model model = ModelFactory.createDefaultModel();
    // Normal statement
    model.add(
        ResourceFactory.createResource("https://example.org/s1"),
        ResourceFactory.createProperty("https://example.org/p1"),
        ResourceFactory.createResource("https://example.org/o1"));
    // Protected statement (predicate in protected namespace)
    model.add(
        ResourceFactory.createResource("https://example.org/s1"),
        ResourceFactory.createProperty(NS + "complianceResult"),
        ResourceFactory.createPlainLiteral("true"));

    FilteredModel result = filter.filterModel(model, "test");
    assertEquals(1, result.model().size());
    assertTrue(result.hasWarning());
    assertTrue(result.warning().contains("1 statement(s)"));
    assertTrue(result.warning().contains(NS));
    assertTrue(result.warning().contains("complianceResult"));
  }

  @Test
  void filterModelNormalStatements() {
    Model model = ModelFactory.createDefaultModel();
    model.add(
        ResourceFactory.createResource("https://example.org/s1"),
        ResourceFactory.createProperty("https://example.org/p1"),
        ResourceFactory.createResource("https://example.org/o1"));
    model.add(
        ResourceFactory.createResource("https://example.org/s2"),
        ResourceFactory.createProperty("https://example.org/p2"),
        ResourceFactory.createPlainLiteral("value"));

    FilteredModel result = filter.filterModel(model, "test");
    assertEquals(2, result.model().size());
    assertFalse(result.hasWarning());
  }

  @Test
  void filterModelNullModel() {
    FilteredModel result = filter.filterModel(null, "test");
    assertNull(result.model());
    assertFalse(result.hasWarning());
  }

  @Test
  void filterModelEmptyModel() {
    Model empty = ModelFactory.createDefaultModel();
    FilteredModel result = filter.filterModel(empty, "test");
    assertSame(empty, result.model());
    assertFalse(result.hasWarning());
  }

  @Test
  void filterModelProtectedSubject() {
    Model model = ModelFactory.createDefaultModel();
    // Protected statement (subject in protected namespace)
    model.add(
        ResourceFactory.createResource(NS + "InternalClass"),
        ResourceFactory.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
        ResourceFactory.createResource("http://www.w3.org/2002/07/owl#Class"));
    // Normal statement
    model.add(
        ResourceFactory.createResource("https://example.org/s1"),
        ResourceFactory.createProperty("https://example.org/p1"),
        ResourceFactory.createResource("https://example.org/o1"));

    FilteredModel result = filter.filterModel(model, "test");
    assertEquals(1, result.model().size());
    assertTrue(result.hasWarning());
  }

  @Test
  void filterModelProtectedObjectIri() {
    Model model = ModelFactory.createDefaultModel();
    // Protected statement (IRI object in protected namespace)
    model.add(
        ResourceFactory.createResource("https://example.org/s1"),
        ResourceFactory.createProperty("http://www.w3.org/2002/07/owl#equivalentClass"),
        ResourceFactory.createResource(NS + "InternalClass"));
    // Normal statement
    model.add(
        ResourceFactory.createResource("https://example.org/s2"),
        ResourceFactory.createProperty("https://example.org/p2"),
        ResourceFactory.createResource("https://example.org/o2"));

    FilteredModel result = filter.filterModel(model, "test");
    assertEquals(1, result.model().size());
    assertTrue(result.hasWarning());
  }
}
