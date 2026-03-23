package eu.xfsc.fc.core.service.assetstore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.core.pojo.ContentAccessorDirect;

class IriGeneratorTest {

    private static final Pattern UUID_URN_PATTERN =
        Pattern.compile("^urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private IriGenerator iriGenerator;

    @BeforeEach
    void setUp() {
        iriGenerator = new IriGenerator(new IriValidator(), new ObjectMapper());
    }

    // --- UUID URN generation ---

    @Test
    void generateUuidUrn_called_returnsValidRfc4122Urn() {
        String urn = iriGenerator.generateUuidUrn();
        assertNotNull(urn);
        assertTrue(urn.startsWith("urn:uuid:"));
        assertTrue(UUID_URN_PATTERN.matcher(urn).matches(),
                "Generated URN should match RFC 4122 format: " + urn);
    }

    @Test
    void generateUuidUrn_calledTwice_returnsUniqueValues() {
        String urn1 = iriGenerator.generateUuidUrn();
        String urn2 = iriGenerator.generateUuidUrn();
        assertNotEquals(urn1, urn2);
    }

    // --- DID generation (demonstration utility) ---

    @Test
    void generateDid_webMethod_returnsDidWebString() {
        String did = iriGenerator.generateDid("web", "example.com");
        assertEquals("did:web:example.com", did);
    }

    @Test
    void generateDid_keyMethod_returnsDidKeyString() {
        String did = iriGenerator.generateDid("key", "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK");
        assertEquals("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK", did);
    }

    // --- RDF ID extraction ---

    @Test
    void resolveIri_credentialSubjectWithId_returnsExtractedDid() {
        String jsonLd = """
                {
                    "@context": ["https://www.w3.org/2018/credentials/v1"],
                    "@type": "VerifiableCredential",
                    "credentialSubject": {
                        "id": "did:web:example.com:participant:123",
                        "name": "Test Participant"
                    }
                }
                """;
        ContentAccessorDirect content = new ContentAccessorDirect(jsonLd);
        String iri = iriGenerator.resolveIri(content, true);
        assertEquals("did:web:example.com:participant:123", iri);
    }

    @Test
    void resolveIri_credentialSubjectArray_returnsFirstId() {
        String jsonLd = """
                {
                    "@context": ["https://www.w3.org/2018/credentials/v1"],
                    "credentialSubject": [{
                        "id": "did:web:example.com:service:456",
                        "type": "ServiceOffering"
                    }]
                }
                """;
        ContentAccessorDirect content = new ContentAccessorDirect(jsonLd);
        String iri = iriGenerator.resolveIri(content, true);
        assertEquals("did:web:example.com:service:456", iri);
    }

    @Test
    void resolveIri_jsonLdWithAtId_returnsAtIdValue() {
        String jsonLd = """
                {
                    "@context": "https://www.w3.org/ns/shacl",
                    "@id": "http://example.org/PersonShape",
                    "@type": "sh:NodeShape"
                }
                """;
        ContentAccessorDirect content = new ContentAccessorDirect(jsonLd);
        String iri = iriGenerator.resolveIri(content, true);
        assertEquals("http://example.org/PersonShape", iri);
    }

    @Test
    void resolveIri_owlOntology_returnsOntologyIri() {
        String jsonLd = """
                {
                    "@id": "http://example.org/MyOntology",
                    "@type": "owl:Ontology",
                    "rdfs:label": "My Ontology"
                }
                """;
        ContentAccessorDirect content = new ContentAccessorDirect(jsonLd);
        String iri = iriGenerator.resolveIri(content, true);
        assertEquals("http://example.org/MyOntology", iri);
    }

    @Test
    void resolveIri_skosConceptScheme_returnsSchemeIri() {
        String jsonLd = """
                {
                    "@id": "http://example.org/MyScheme",
                    "@type": "skos:ConceptScheme",
                    "skos:prefLabel": "My Scheme"
                }
                """;
        ContentAccessorDirect content = new ContentAccessorDirect(jsonLd);
        String iri = iriGenerator.resolveIri(content, true);
        assertEquals("http://example.org/MyScheme", iri);
    }

    @Test
    void resolveIri_rdfWithoutId_fallsBackToUuidUrn() {
        String jsonLd = """
                {
                    "@context": "https://schema.org/",
                    "name": "Something without an ID"
                }
                """;
        ContentAccessorDirect content = new ContentAccessorDirect(jsonLd);
        String iri = iriGenerator.resolveIri(content, true);
        assertTrue(iri.startsWith("urn:uuid:"));
    }

    @Test
    void resolveIri_invalidJsonContent_fallsBackToUuidUrn() {
        ContentAccessorDirect content = new ContentAccessorDirect("not json at all");
        String iri = iriGenerator.resolveIri(content, true);
        assertTrue(iri.startsWith("urn:uuid:"));
    }

    // --- Non-RDF resolution ---

    @Test
    void resolveIri_nonRdfNullContent_returnsUuidUrn() {
        String iri = iriGenerator.resolveIri(null, false);
        assertTrue(iri.startsWith("urn:uuid:"));
        assertTrue(UUID_URN_PATTERN.matcher(iri).matches());
    }

    @Test
    void resolveIri_nonRdfWithContent_returnsUuidUrn() {
        ContentAccessorDirect content = new ContentAccessorDirect("some pdf bytes");
        String iri = iriGenerator.resolveIri(content, false);
        assertTrue(iri.startsWith("urn:uuid:"));
    }

    // --- RDF edge cases: null/empty content ---

    @Test
    void resolveIri_rdfNullContent_fallsBackToUuidUrn() {
        String iri = iriGenerator.resolveIri(null, true);
        assertTrue(iri.startsWith("urn:uuid:"));
        assertTrue(UUID_URN_PATTERN.matcher(iri).matches());
    }

    @Test
    void resolveIri_rdfEmptyContent_fallsBackToUuidUrn() {
        ContentAccessorDirect content = new ContentAccessorDirect("");
        String iri = iriGenerator.resolveIri(content, true);
        assertTrue(iri.startsWith("urn:uuid:"));
        assertTrue(UUID_URN_PATTERN.matcher(iri).matches());
    }

    // --- credentialSubject.id takes priority over @id ---

    @Test
    void resolveIri_bothCredentialSubjectAndAtId_prefersCredentialSubject() {
        String jsonLd = """
                {
                    "@id": "http://example.org/presentation",
                    "credentialSubject": {
                        "id": "did:web:example.com:priority-id"
                    }
                }
                """;
        ContentAccessorDirect content = new ContentAccessorDirect(jsonLd);
        String iri = iriGenerator.resolveIri(content, true);
        assertEquals("did:web:example.com:priority-id", iri);
    }
}
