package eu.xfsc.fc.core.service.sdstore;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.xfsc.fc.core.config.RdfContentTypeProperties;

class RdfDetectorTest {

    private RdfDetector rdfDetector;

    @BeforeEach
    void setUp() {
        RdfContentTypeProperties props = new RdfContentTypeProperties();
        props.setContentTypes(new LinkedHashSet<>(Set.of(
                "application/ld+json",
                "text/turtle",
                "application/n-triples",
                "application/rdf+xml",
                "application/n-quads",
                "application/trig"
        )));
        rdfDetector = new RdfDetector(props);
    }

    @Test
    void configuredRdfTypesDetectedAsRdf() {
        assertTrue(rdfDetector.isRdf("application/ld+json", null));
        assertTrue(rdfDetector.isRdf("text/turtle", null));
        assertTrue(rdfDetector.isRdf("application/n-triples", null));
        assertTrue(rdfDetector.isRdf("application/rdf+xml", null));
        assertTrue(rdfDetector.isRdf("application/n-quads", null));
        assertTrue(rdfDetector.isRdf("application/trig", null));
    }

    @Test
    void unknownTypesDetectedAsNonRdf() {
        assertFalse(rdfDetector.isRdf("text/plain", null));
        assertFalse(rdfDetector.isRdf("application/pdf", null));
        assertFalse(rdfDetector.isRdf("application/x-yaml", null));
        assertFalse(rdfDetector.isRdf("application/octet-stream", null));
        assertFalse(rdfDetector.isRdf("image/png", null));
    }

    @Test
    void applicationJsonWithContextIsRdf() {
        String jsonLd = """
                {
                  "@context": ["https://www.w3.org/2018/credentials/v1"],
                  "type": "VerifiablePresentation"
                }
                """;
        byte[] content = jsonLd.getBytes(StandardCharsets.UTF_8);
        assertTrue(rdfDetector.isRdf("application/json", content));
    }

    @Test
    void applicationJsonWithoutContextIsNonRdf() {
        String plainJson = """
                {
                  "contractId": "contract-001",
                  "title": "Data Processing Agreement"
                }
                """;
        byte[] content = plainJson.getBytes(StandardCharsets.UTF_8);
        assertFalse(rdfDetector.isRdf("application/json", content));
    }

    @Test
    void applicationJsonWithNullContentIsNonRdf() {
        assertFalse(rdfDetector.isRdf("application/json", null));
    }

    @Test
    void nullContentTypeIsNonRdf() {
        assertFalse(rdfDetector.isRdf(null, null));
    }

    @Test
    void contentTypeIsCaseInsensitive() {
        assertTrue(rdfDetector.isRdf("Application/LD+JSON", null));
        assertTrue(rdfDetector.isRdf("TEXT/TURTLE", null));
    }

    @Test
    void customTypeAddedToConfigIsRdf() {
        RdfContentTypeProperties customProps = new RdfContentTypeProperties();
        customProps.setContentTypes(new LinkedHashSet<>(Set.of("application/x-custom-rdf")));
        RdfDetector customDetector = new RdfDetector(customProps);

        assertTrue(customDetector.isRdf("application/x-custom-rdf", null));
        assertFalse(customDetector.isRdf("application/ld+json", null));
    }

}