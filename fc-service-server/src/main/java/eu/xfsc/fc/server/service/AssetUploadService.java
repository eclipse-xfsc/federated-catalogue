package eu.xfsc.fc.server.service;

import static eu.xfsc.fc.core.util.HashUtils.calculateSha256AsHex;
import static eu.xfsc.fc.server.util.SessionUtils.checkParticipantAccess;
import static eu.xfsc.fc.server.util.SessionUtils.getSessionParticipantId;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.AssetEnrichmentResponse;
import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.pojo.ContentAccessorBinary;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.FilteredClaims;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.service.assetstore.IriGenerator;
import eu.xfsc.fc.core.service.assetstore.RdfDetector;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter;
import eu.xfsc.fc.core.service.verification.VerificationService;
import eu.xfsc.fc.core.service.verification.VerificationConstants;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.GraphStoreDisabledException;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service layer for asset uploads. "Asset" is the umbrella term for anything
 * stored in the catalogue. Two paths exist:
 * <ul>
 *   <li><b>RDF-Data - currently only credential data</b> (RDF) — verified via {@link VerificationService}, indexed in the graph store.</li>
 *   <li><b>Non-RDF Asset</b> ("unstructured" data) — stored as-is in the file store, no verification.</li>
 * </ul>
 *
 * @see eu.xfsc.fc.core.service.assetstore.RdfDetector
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetUploadService {

    private final VerificationService verificationService;
    private final AssetStore assetStorePublisher;
    private final RdfDetector rdfDetector;
    private final IriGenerator iriGenerator;
    private final ProtectedNamespaceFilter protectedNamespaceFilter;
    private final GraphStore graphStore;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<DocumentBuilderFactory> secureDocumentBuilderFactoryProvider;

    public UploadResult processUpload(byte[] content, String contentType, String originalFilename) {
        return processUpload(content, contentType, originalFilename, null);
    }

    /**
     * Process an upload, optionally enriching an existing non-RDF asset.
     *
     * @param existingId when non-null, the IRI of an existing active asset to update (new Envers revision);
     *                   when null, a fresh asset is created with a generated IRI
     */
    public UploadResult processUpload(byte[] content, String contentType, String originalFilename, String existingId) {
        if (content == null || content.length == 0) {
            throw new ClientException("Upload content must not be empty");
        }

        String normalizedContentType = normalizeContentType(contentType);

        if (!rdfDetector.isRdf(normalizedContentType, content)) {
            return new UploadResult.AssetCreated(
                handleNonRdfAsset(content, normalizedContentType, originalFilename, existingId));
        }

        // Try enrichment path: check if RDF describes an existing non-RDF asset
        String subjectId = extractSubjectIdFromRdf(content, normalizedContentType);
        if (subjectId != null) {
            var existing = assetStorePublisher.findEnrichableAsset(subjectId);
            if (existing.isPresent()) {
                log.debug("processUpload; detected enrichment case for non-RDF asset {}", subjectId);
                return new UploadResult.AssetEnriched(enrichAsset(existing.get(), content, normalizedContentType));
            }
        }

        // Not an enrichment case; process as new RDF asset
        return new UploadResult.AssetCreated(handleCredential(content, normalizedContentType));
    }

    /**
     * Strip MIME parameters (e.g. {@code ;charset=UTF-8}) so downstream comparisons match the bare
     * type/subtype. Invalid media types fall back to the raw value to preserve existing error paths.
     */
    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            return mediaType.getType() + "/" + mediaType.getSubtype();
        } catch (InvalidMediaTypeException ex) {
            return contentType;
        }
    }

    private AssetMetadata handleCredential(byte[] content, String contentType) {
        log.debug("handleCredential; detected RDF content, delegating to verification pipeline");
        String text = new String(content, StandardCharsets.UTF_8);
        ContentAccessorDirect contentAccessor = new ContentAccessorDirect(text, contentType);

        CredentialVerificationResult verificationResult = verificationService.verifyCredential(contentAccessor);

        // Non-credential RDF: ID and issuer are null — resolve both from local context.
        String assetId = verificationResult.getId() != null
                ? verificationResult.getId()
                : iriGenerator.resolveIri(contentAccessor, true);
        String issuer = verificationResult.getIssuer() != null
                ? verificationResult.getIssuer()
                : getSessionParticipantId();

        AssetMetadata assetMetadata = new AssetMetadata(assetId, issuer, verificationResult.getValidators(), contentAccessor);
        assetMetadata.setContentType(contentType);
        assetMetadata.setFileSize((long) content.length);

        checkParticipantAccess(assetMetadata.getIssuer());
        assetStorePublisher.storeCredential(assetMetadata, verificationResult);

        if (verificationResult.getWarnings() != null && !verificationResult.getWarnings().isEmpty()) {
            assetMetadata.setWarnings(verificationResult.getWarnings());
        }

        log.debug("handleCredential.exit; stored credential with hash: {}", assetMetadata.getAssetHash());
        return assetMetadata;
    }

    private AssetMetadata handleNonRdfAsset(byte[] content, String contentType, String originalFilename, String existingId) {
        log.debug("handleNonRdfAsset; non-RDF asset, skipping verification");
        String assetHash = calculateSha256AsHex(content);
        String participantId = getSessionParticipantId();
        Instant now = Instant.now();

        ContentAccessorBinary contentAccessor = new ContentAccessorBinary(content);
        AssetMetadata assetMetadata = new AssetMetadata(assetHash, existingId, AssetStatus.ACTIVE,
                participantId, null, now, now, contentAccessor);
        assetMetadata.setContentType(contentType);
        assetMetadata.setFileSize((long) content.length);

        return assetStorePublisher.storeUnverified(assetMetadata, originalFilename);
    }

    /**
     * Enriches an existing non-RDF asset with RDF metadata.
     *
     * @throws GraphStoreDisabledException if the graph store backend is disabled (graphstore.impl=none)
     */
    private AssetEnrichmentResponse enrichAsset(AssetRecord record, byte[] rdfPayload, String contentType) {
        String subjectId = record.getId();
        log.debug("enrichAsset.enter; assetId={}, contentType={}", subjectId, contentType);

        // Only the asset's issuer (or a catalogue admin) may enrich its metadata.
        checkParticipantAccess(record.getIssuer());

        // Fail-fast: no point parsing the payload if the graph backend can't accept it.
        if (graphStore.getBackendType() == GraphBackendType.NONE) {
            throw new GraphStoreDisabledException(
                    "Cannot enrich asset: graph store is disabled (graphstore.impl=none). "
                    + "Enable a graph backend (neo4j or fuseki) to perform metadata enrichment.");
        }

        String rawPayloadText = new String(rdfPayload, StandardCharsets.UTF_8);
        List<RdfClaim> claims = parseRdfClaims(rawPayloadText, contentType);
        log.debug("enrichAsset; parsed {} claims", claims.size());

        boolean hasSubjectClaim = claims.stream()
                .anyMatch(c -> subjectId.equals(c.getSubjectValue()));
        if (!hasSubjectClaim && !claims.isEmpty()) {
            throw new ClientException(
                    "RDF payload must contain at least one claim with subject IRI: " + subjectId);
        }

        FilteredClaims filteredClaims = protectedNamespaceFilter.filterClaims(claims, "asset enrichment");
        int rejectedCount = claims.size() - filteredClaims.claims().size();
        log.debug("enrichAsset; filtered {} claims, {} remaining", rejectedCount, filteredClaims.claims().size());

        graphStore.deleteClaims(subjectId);
        log.debug("enrichAsset; deleted old triples for subject {}", subjectId);

        graphStore.addClaims(filteredClaims.claims(), subjectId);
        log.debug("enrichAsset; added {} new triples", filteredClaims.claims().size());

        // deleteClaims wipes every triple under this subject — including SF-03 link triples for an
        // MR asset that's linked to a human-readable attachment. Restore them.
        assetStorePublisher.findLink(subjectId).ifPresent(link ->
            assetStorePublisher.writeAssetLinkTriples(subjectId, link.linkedIri()));

        // Persist the unfiltered RDF so a full graph rebuild can replay enrichment from source.
        assetStorePublisher.saveEnrichedContent(record, rawPayloadText);
        log.debug("enrichAsset; persisted enrichment content, asset updated");

        AssetEnrichmentResponse response = new AssetEnrichmentResponse();
        response.setAssetId(subjectId);
        response.setTriplesAdded(filteredClaims.claims().size());
        response.setTriplesRejected(rejectedCount);
        log.debug("enrichAsset.exit; triplesAdded={}, triplesRejected={}", response.getTriplesAdded(), response.getTriplesRejected());
        return response;
    }

    /**
     * Extracts the subject IRI from an RDF payload.
     * Returns the first subject found; null if parsing fails or RDF is empty.
     */
    private String extractSubjectIdFromRdf(byte[] rdfPayload, String contentType) {
        try {
            String text = new String(rdfPayload, StandardCharsets.UTF_8);
            Lang lang = detectLang(contentType);
            Model model = ModelFactory.createDefaultModel();
            RDFParser.fromString(text, lang).parse(model);

            // Return the first subject found
            if (model.listSubjects().hasNext()) {
                Resource subj = model.listSubjects().next();
                return subj.getURI();
            }
            return null;
        } catch (RiotException ex) {
            log.debug("extractSubjectIdFromRdf; parsing failed: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Parses RDF claims from a payload string.
     * Supports JSON-LD, Turtle, N-Triples, and RDF/XML with fallback.
     */
    private List<RdfClaim> parseRdfClaims(String rdfPayload, String contentType) {
        Lang lang = detectLang(contentType);
        Model model = parseWithFallback(rdfPayload, lang);
        List<RdfClaim> claims = new ArrayList<>();

        StmtIterator it = model.listStatements();
        while (it.hasNext()) {
            claims.add(new RdfClaim(it.nextStatement(), objectMapper));
        }

        return claims;
    }

    /**
     * Parses RDF with fallback sequence: primary lang, then JSON-LD, Turtle, N-Triples, RDF/XML.
     */
    private Model parseWithFallback(String rdfPayload, Lang primaryLang) {
        Lang[] fallbackSequence = buildLangSequence(primaryLang);
        RiotException lastException = null;

        for (Lang lang : fallbackSequence) {
            try {
                assertSecureRdfXml(rdfPayload, lang);
                Model model = ModelFactory.createDefaultModel();
                RDFParser.fromString(rdfPayload, lang).parse(model);
                return model;
            } catch (RiotException ex) {
                log.debug("parseWithFallback; lang {} failed: {}", lang, ex.getMessage());
                lastException = ex;
            }
        }

        throw lastException != null ? lastException
                : new RiotException("RDF parsing failed: no supported format matched");
    }

    /**
     * Builds fallback sequence with primary lang first, then remaining langs.
     */
    private Lang[] buildLangSequence(Lang primaryLang) {
        List<Lang> sequence = new ArrayList<>();
        sequence.add(primaryLang);
        for (Lang lang : new Lang[]{Lang.JSONLD, Lang.TURTLE, Lang.NTRIPLES, Lang.RDFXML}) {
            if (lang != primaryLang) {
                sequence.add(lang);
            }
        }
        return sequence.toArray(new Lang[0]);
    }

    private void assertSecureRdfXml(String rdfPayload, Lang lang) {
        if (lang != Lang.RDFXML) {
            return;
        }
        try {
            DocumentBuilderFactory dbf = secureDocumentBuilderFactoryProvider.getObject();
            dbf.newDocumentBuilder().parse(new InputSource(new StringReader(rdfPayload)));
        } catch (SAXException | ParserConfigurationException | IOException ex) {
            throw new RiotException("RDF/XML failed XXE-hardened pre-validation: " + ex.getMessage(), ex);
        }
    }

    /**
     * Maps MIME content type to Jena Lang. Defaults to JSON-LD for null/unknown types.
     */
    private static Lang detectLang(String contentType) {
        if (contentType == null) {
            return Lang.JSONLD;
        }
        return switch (contentType.strip().toLowerCase()) {
            case VerificationConstants.MEDIA_TYPE_TURTLE -> Lang.TURTLE;
            case VerificationConstants.MEDIA_TYPE_NTRIPLES -> Lang.NTRIPLES;
            case VerificationConstants.MEDIA_TYPE_RDF_XML -> Lang.RDFXML;
            default -> Lang.JSONLD;
        };
    }
}