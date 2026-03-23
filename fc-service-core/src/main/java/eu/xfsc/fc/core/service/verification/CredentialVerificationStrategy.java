package eu.xfsc.fc.core.service.verification;

import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.SchemeRouter;
import com.danubetech.keyformats.jose.JWK;
import com.danubetech.verifiablecredentials.VerifiableCredential;
import com.danubetech.verifiablecredentials.VerifiablePresentation;
import com.danubetech.verifiablecredentials.jsonld.VerifiableCredentialKeywords;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.dao.ValidatorCacheDao;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialClaim;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultOffering;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultParticipant;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultResource;
import eu.xfsc.fc.core.pojo.FilteredClaims;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.verification.cache.CachingLocator;
import eu.xfsc.fc.core.service.verification.claims.ClaimExtractor;
import eu.xfsc.fc.core.service.verification.claims.DanubeTechClaimExtractor;
import eu.xfsc.fc.core.service.verification.claims.TitaniumClaimExtractor;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
import eu.xfsc.fc.core.service.verification.signature.SignatureVerifier;
import eu.xfsc.fc.core.util.ClaimValidator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import foundation.identity.jsonld.JsonLDObject;
import info.weboftrust.ldsignatures.LdProof;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.riot.system.stream.StreamManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static eu.xfsc.fc.core.service.verification.TrustFrameworkBaseClass.PARTICIPANT;
import static eu.xfsc.fc.core.service.verification.TrustFrameworkBaseClass.RESOURCE;
import static eu.xfsc.fc.core.service.verification.TrustFrameworkBaseClass.SERVICE_OFFERING;
import static eu.xfsc.fc.core.service.verification.TrustFrameworkBaseClass.UNKNOWN;


/**
 * Verification strategy for Verifiable Credential payloads (VP/VC).
 * Contains the credential verification logic: syntactic parsing of VP/VCs,
 * semantic checks, schema verification, signature verification, and claim extraction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CredentialVerificationStrategy implements VerificationStrategy {

    private static final ClaimExtractor[] extractors = new ClaimExtractor[]{new TitaniumClaimExtractor(), new DanubeTechClaimExtractor()};
    private static final String VC2_CONTEXT = "https://www.w3.org/ns/credentials/v2";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final String RDF_CONTEXT_KEY = "@context";

    /**
     * Resolves the {@code type} or {@code @type} value from a JSON-LD map.
     * ICAM 24.07: "{@code @type} is aliased to {@code type}. Consequently, we MAY also use this alias."
     */
    private static Object resolveType(Map<?, ?> map) {
        Object val = map.get("type");
        return val != null ? val : map.get("@type");
    }

    /**
     * Resolves the {@code id} or {@code @id} value from a JSON-LD map.
     * ICAM 24.07: "{@code @id} is aliased to {@code id}. Consequently, we MAY also use this alias."
     */
    private static Object resolveId(Map<?, ?> map) {
        Object val = map.get("id");
        return val != null ? val : map.get("@id");
    }

    @Value("${federated-catalogue.verification.require-vp:true}")
    private boolean requireVP;
    @Value("${federated-catalogue.verification.drop-validarors:false}")
    private boolean dropValidators;

    /**
     * When true, Gaia-X Trust Framework validation is enforced including trust anchor registry calls.
     * When false (default), credentials can be uploaded without Gaia-X compliance validation.
     */
    @Value("${federated-catalogue.verification.trust-framework.gaiax.enabled:false}")
    boolean gaiaxTrustFrameworkEnabled;

    @Value("${federated-catalogue.verification.participant.type}")
    private String participantType;
    @Value("${federated-catalogue.verification.service-offering.type}")
    private String serviceOfferingType;
    @Value("${federated-catalogue.verification.resource.type}")
    private String resourceType;

    private Map<TrustFrameworkBaseClass, String> trustFrameworkBaseClassUris;

    private final JwtContentPreprocessor jwtPreprocessor;
    private final ProtectedNamespaceFilter protectedNamespaceFilter;
    private final SchemaStore schemaStore;
    private final SchemaValidationService schemaValidationService;
    private final SignatureVerifier signVerifier;
    @Qualifier("contextCacheFileStore")
    private final FileStore fileStore;
    private final DocumentLoader documentLoader;
    private final ValidatorCacheDao validatorCache;
    private final Vc11Processor vc11Processor;
    private final Vc2Processor vc2Processor;
    private final JwtSignatureVerifier jwtSignatureVerifier;
    private final FormatDetector formatDetector;
    private final LoireJwtParser loireJwtParser;

    @Value("${federated-catalogue.verification.trust-framework.gaiax.trust-anchor-url:}")
    private String trustAnchorAddr;
    @Value("${federated-catalogue.verification.http-timeout:5000}")
    private int httpTimeout;
    @Value("${federated-catalogue.verification.validator-expire:1D}")
    private Duration validatorExpiration;

    private RestTemplate rest;
    private volatile boolean loadersInitialised;
    private volatile StreamManager streamManager;

    private RestTemplate restTemplate() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(httpTimeout);
        factory.setConnectionRequestTimeout(httpTimeout);
        return new RestTemplate(factory);
    }

    @PostConstruct
    private void initRestTemplate() {
        rest = restTemplate();
    }

    @PostConstruct
    private void initializeTrustFrameworkBaseClasses() {
        trustFrameworkBaseClassUris = new HashMap<>();
        trustFrameworkBaseClassUris.put(SERVICE_OFFERING, serviceOfferingType);
        trustFrameworkBaseClassUris.put(RESOURCE, resourceType);
        trustFrameworkBaseClassUris.put(PARTICIPANT, participantType);
    }

    /**
     * Pipeline state carried between verification phases.
     */
    private record VerificationContext(
        String body,
        CredentialFormat format,
        boolean isJwt,
        ContentAccessor payload,
        Validator jwtValidator
    ) {}

    public CredentialVerificationResult verifyCredential(ContentAccessor payload, boolean strict, TrustFrameworkBaseClass expectedClass,
                                                         boolean verifySemantics, boolean verifySchema, boolean verifyVPSignatures,
                                                         boolean verifyVCSignatures) throws VerificationException {
        log.debug("verifyCredential.enter; strict: {}, expectedType: {}, verifySemantics: {}, verifySchema: {}, verifyVPSignatures: {}, verifyVCSignatures: {}",
                strict, expectedClass, verifySemantics, verifySchema, verifyVPSignatures, verifyVCSignatures);
        long stamp = System.currentTimeMillis();

        VerificationContext ctx = detectAndUnwrap(payload, verifyVCSignatures || verifyVPSignatures);

        // syntactic + semantic validation
        JsonLDObject ld = parseContent(ctx.payload());
        ld.setDocumentLoader(this.documentLoader);
        log.debug("verifyCredential; content parsed, time taken: {}", System.currentTimeMillis() - stamp);

        TypedCredentials typedCredentials = parseCredentials(ld, strict && requireVP, verifySemantics);

        // TODO(loire-compatibility): CAT-FR-GD-01 part 3 - Loire Ontology Migration will update resolveBaseClass to handle 2511 namespace types.
        if (verifySemantics && gaiaxTrustFrameworkEnabled && !typedCredentials.hasClasses()) {
            throw new VerificationException("Semantic Error: no proper CredentialSubject found");
        }

        TrustFrameworkBaseClass baseClass = resolvePrimaryBaseClass(typedCredentials, verifySemantics, expectedClass);

        FilteredClaims filtered = extractAndValidateClaims(ctx.payload(), verifySemantics && strict);

        if (verifySchema) {
            validateSchema(filtered.claims());
        }

        List<Validator> validators = collectValidators(ctx, ld, typedCredentials,
                verifySemantics, verifyVCSignatures, verifyVPSignatures);

        CredentialVerificationResult result = assembleResult(baseClass, typedCredentials, filtered.claims(), validators);
        if (filtered.hasWarning()) {
            result.setWarnings(List.of(filtered.warning()));
        }

        stamp = System.currentTimeMillis() - stamp;
        log.debug("verifyCredential.exit; returning: {}; time taken: {}", result, stamp);
        return result;
    }

    public List<CredentialClaim> extractClaims(ContentAccessor payload) {
        // Make sure our interceptors are in place.
        initLoaders();
        List<CredentialClaim> claims = null;
        for (ClaimExtractor extra : extractors) {
            try {
                claims = extra.extractClaims(payload);
                if (claims != null) {
                    break;
                }
            } catch (Exception ex) {
                log.error("extractClaims.error using {}: {}", extra.getClass().getName(), ex.getMessage());
            }
        }
        return claims;
    }

    public void setBaseClassUri(TrustFrameworkBaseClass baseClass, String uri) {
        trustFrameworkBaseClassUris.put(baseClass, uri);
    }

    /**
     * Phase 1: Detect credential format, verify JWT signature, enforce Loire policies, and
     * unwrap to JSON-LD. After this method, the payload is always JSON-LD.
     */
    private VerificationContext detectAndUnwrap(ContentAccessor payload, boolean verifySigs) {
        String body = payload.getContentAsString().strip();
        payload = new ContentAccessorDirect(body, payload.getContentType());

        CredentialFormat format = formatDetector.detect(payload);
        // Reject ambiguous/unrecognizable JWTs — non-JWT payloads fall through to
        // vc11Processor which provides more specific syntactic error messages.
        if (format == CredentialFormat.UNKNOWN && body.startsWith("eyJ")) {
            throw new ClientException(
                "Unrecognizable JWT credential format — JWT does not have recognized "
                    + "typ header or vc/vp wrapper claims");
        }
        boolean isJwt = format == CredentialFormat.GAIAX_V2_LOIRE
            || (format == CredentialFormat.VC2_DANUBETECH && body.startsWith("eyJ"));

        Validator jwtValidator = null;
        if (isJwt && verifySigs) {
            jwtValidator = jwtSignatureVerifier.verify(body);
        }

        if (format == CredentialFormat.GAIAX_V2_LOIRE && gaiaxTrustFrameworkEnabled) {
            enforceDidWebRestriction(body);
            if (jwtValidator != null) {
                enforceLoireTrustChain(jwtValidator);
            }
        }

        // Version dispatch — unwrap to JSON-LD
        // NOTE: CAT-FR-GD-02's VcStructureDetector.isVcStructured() call MUST be placed AFTER
        // unwrapping, not before — a JWT-wrapped VC 2.0 payload would not be recognised as a VC.
        if (format == CredentialFormat.GAIAX_V2_LOIRE) {
            payload = loireJwtParser.unwrap(payload);
        } else if (format == CredentialFormat.GAIAX_V1_TAGUS) {
            payload = vc11Processor.preProcess(payload);
        } else {
            boolean isVc2 = isVc2Context(body) || isJwt;
            payload = (isVc2 ? vc2Processor : vc11Processor).preProcess(payload);
        }

        return new VerificationContext(body, format, isJwt, payload, jwtValidator);
    }

    /**
     * Phase 2: Resolve the dominant base class from typed credentials and validate type constraints.
     */
    private TrustFrameworkBaseClass resolvePrimaryBaseClass(TypedCredentials typedCredentials,
        boolean verifySemantics, TrustFrameworkBaseClass expectedClass) {
        int partCount = 0;
        int soffCount = 0;
        TrustFrameworkBaseClass baseClass = UNKNOWN;
        Collection<TrustFrameworkBaseClass> baseClasses = typedCredentials.getBaseClasses();

        if (baseClasses.size() > 1) {
            Map<TrustFrameworkBaseClass, Integer> classMap = baseClasses.stream()
                .reduce(new HashMap<>(),
                    (map, e) -> { map.merge(e, 1, Integer::sum); return map; },
                    (m, m2) -> { m.putAll(m2); return m; });
            partCount = classMap.getOrDefault(PARTICIPANT, 0);
            soffCount = classMap.getOrDefault(SERVICE_OFFERING, 0);
            if (partCount > 0) {
                baseClass = PARTICIPANT;
            } else if (soffCount > 0) {
                baseClass = SERVICE_OFFERING;
            } else if (classMap.get(RESOURCE) != null) {
                baseClass = RESOURCE;
            }
        } else if (!baseClasses.isEmpty()) {
            baseClass = baseClasses.iterator().next();
        }

        if (verifySemantics) {
            if (partCount > 0 && soffCount > 0) {
                throw new VerificationException("Semantic error: credential has several types: " + baseClasses);
            }
            if (expectedClass != UNKNOWN && baseClass != expectedClass) {
                throw new VerificationException(
                    "Semantic error: expected credential of type " + expectedClass + " but found " + baseClass);
            }
        }
        return baseClass;
    }

    /**
     * Phase 3: Extract claims, filter protected namespaces, and validate subject uniqueness.
     */
    private FilteredClaims extractAndValidateClaims(ContentAccessor payload, boolean strictSemantics) {
        long stamp = System.currentTimeMillis();
        List<CredentialClaim> claims = extractClaims(payload);
        FilteredClaims filtered = protectedNamespaceFilter.filterClaims(claims, "claims extraction");
        claims = filtered.claims();
        log.debug("verifyCredential; claims extracted: {}, time taken: {}",
                (claims == null ? "null" : claims.size()), System.currentTimeMillis() - stamp);

        if (strictSemantics) {
            validateSubjectUniqueness(claims);
        }
        return filtered;
    }

    private void validateSubjectUniqueness(List<CredentialClaim> claims) {
        Set<String> subjects = new HashSet<>();
        Set<String> objects = new HashSet<>();
        if (claims != null && !claims.isEmpty()) {
            for (CredentialClaim claim : claims) {
                subjects.add(claim.getSubjectString());
                objects.add(claim.getObjectString());
            }
        }
        subjects.removeAll(objects);

        if (subjects.size() > 1) {
            String sep = System.lineSeparator();
            StringBuilder sb = new StringBuilder(
                "Semantic Errors: There are different subject ids in credential subjects: ").append(sep);
            for (String s : subjects) {
                sb.append(s).append(sep);
            }
            throw new VerificationException(sb.toString());
        } else if (subjects.isEmpty()) {
            throw new VerificationException("Semantic Errors: There is no uniquely identified credential subject");
        }
    }

    private void validateSchema(List<CredentialClaim> claims) {
        eu.xfsc.fc.core.pojo.SchemaValidationResult result =
            schemaValidationService.validateClaimsAgainstCompositeSchema(claims);
        if (result == null || !result.isConforming()) {
            throw new VerificationException(
                "Schema error: " + (result == null ? "unknown" : result.getValidationReport()));
        }
    }

    /**
     * Phase 4: Collect all signature validators — JWT outer, JWT inner VCs, and LD proofs.
     */
    private List<Validator> collectValidators(VerificationContext ctx, JsonLDObject ld,
        TypedCredentials typedCredentials, boolean verifySemantics,
        boolean verifyVCSignatures, boolean verifyVPSignatures) {
        boolean isVpJwt = false;
        if (ctx.isJwt() && ctx.jwtValidator() != null) {
            try {
                JWTClaimsSet jwtClaims = SignedJWT.parse(ctx.body()).getJWTClaimsSet();
                isVpJwt = isVpJwtClaims(jwtClaims);
                if (verifySemantics) {
                    checkVpJwtHolder(jwtClaims);
                }
            } catch (java.text.ParseException ex) {
                log.warn("collectValidators; JWT claims re-parse error: {}", ex.getMessage());
            }
        }

        if (ctx.isJwt()) {
            List<Validator> validators = ctx.jwtValidator() != null
                ? new ArrayList<>(List.of(ctx.jwtValidator())) : null;
            if (verifyVCSignatures && isVpJwt) {
                List<Validator> inner = verifyInnerVcCredentials(ld);
                if (!inner.isEmpty()) {
                    validators.addAll(inner);
                }
            }
            return validators;
        }
        if (verifyVPSignatures || verifyVCSignatures) {
            return checkCryptography(typedCredentials, verifyVPSignatures, verifyVCSignatures);
        }
        return null;
    }

    /**
     * Phase 5: Build the typed result object based on the resolved base class.
     */
    private CredentialVerificationResult assembleResult(TrustFrameworkBaseClass baseClass,
        TypedCredentials typedCredentials, List<CredentialClaim> claims, List<Validator> validators) {
        String id = typedCredentials.getID();
        String issuer = typedCredentials.getIssuer();
        Instant issuedDate = typedCredentials.getIssuanceDate();
        Instant now = Instant.now();
        String status = AssetStatus.ACTIVE.getValue();

        if (baseClass == PARTICIPANT) {
            if (issuer == null) {
                issuer = id;
            }
            String method = typedCredentials.getProofMethod();
            String holder = typedCredentials.getHolder();
            String name = holder == null ? issuer : holder;
            return new CredentialVerificationResultParticipant(now, status, issuer, issuedDate,
                    claims, validators, name, method);
        }
        if (baseClass == SERVICE_OFFERING) {
            return new CredentialVerificationResultOffering(now, status, issuer, issuedDate,
                    id, claims, validators);
        }
        if (baseClass == RESOURCE) {
            return new CredentialVerificationResultResource(now, status, issuer, issuedDate,
                    id, claims, validators);
        }
        return new CredentialVerificationResult(now, status, issuer, issuedDate,
                id, claims, validators);
    }

    private TypedCredentials parseCredentials(JsonLDObject ld, boolean vpRequired, boolean verifySemantics) {
        if (ld.isType(VerifiableCredentialKeywords.JSONLD_TERM_VERIFIABLE_PRESENTATION)) {
            VerifiablePresentation vp = VerifiablePresentation.fromJsonLDObject(ld);
            if (verifySemantics) {
                return verifyPresentation(vp);
            }
            return getCredentials(vp);
        }
        if (vpRequired) {
            throw new VerificationException("Semantic error: expected credential of type 'VerifiablePresentation', actual credential type: " + ld.getTypes());
        }
        if (ld.isType(VerifiableCredentialKeywords.JSONLD_TERM_VERIFIABLE_CREDENTIAL)) {
            VerifiableCredential vc = VerifiableCredential.fromJsonLDObject(ld);
            if (verifySemantics) {
                String err = verifyCredential(vc, 0);
                if (!err.isEmpty()) {
                    throw new VerificationException("Semantic error: " + err);
                }
            }
            return getCredentials(vc);
        }
        throw new VerificationException("Semantic error: unexpected credential type: " + ld.getTypes());
    }

    private boolean isVc2Context(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode ctx = root.get(RDF_CONTEXT_KEY);
            if (ctx == null) {
                return false;
            }
            if (ctx.isArray()) {
                for (JsonNode element : ctx) {
                    if (VC2_CONTEXT.equals(element.asText())) {
                        return true;
                    }
                }
                return false;
            }
            return VC2_CONTEXT.equals(ctx.asText());
        } catch (JsonProcessingException ex) {
            return false;
        }
    }

    /* Credential parsing, semantic validation */
    private JsonLDObject parseContent(ContentAccessor content) {
        try {
            return JsonLDObject.fromJson(content.getContentAsString());
        } catch (Exception ex) {
            log.warn("parseContent.syntactic error: {}", ex.getMessage());
            throw new ClientException("Syntactic error: " + ex.getMessage(), ex);
        }
    }

    private TypedCredentials verifyPresentation(VerifiablePresentation presentation) {
        log.debug("verifyPresentation.enter; got presentation with id: {}", presentation.getId());
        StringBuilder sb = new StringBuilder();
        String sep = System.lineSeparator();
        if (checkAbsence(presentation, RDF_CONTEXT_KEY)) {
            sb.append(" - VerifiablePresentation must contain '@context' property").append(sep);
        }
        if (checkAbsence(presentation, "type", "@type")) {
            sb.append(" - VerifiablePresentation must contain 'type' property").append(sep);
        }
        if (checkAbsence(presentation, "verifiableCredential")) {
            sb.append(" - VerifiablePresentation must contain 'verifiableCredential' property").append(sep);
        }
        TypedCredentials tcreds = getCredentials(presentation);
        Collection<VerifiableCredential> credentials = tcreds.getCredentials();
        int i = 0;
        for (VerifiableCredential credential : credentials) {
            if (credential != null) {
                sb.append(verifyCredential(credential, i));
            }
            i++;
        }

        if (!sb.isEmpty()) {
            sb.insert(0, "Semantic Errors:").insert(16, sep);
            throw new VerificationException(sb.toString());
        }

        log.debug("verifyPresentation.exit; returning {} VCs", credentials.size());
        return tcreds;
    }

    private String verifyCredential(VerifiableCredential credential, int idx) {
        StringBuilder sb = new StringBuilder();
        String sep = System.lineSeparator();
        if (checkAbsence(credential, RDF_CONTEXT_KEY)) {
            sb.append(" - VerifiableCredential[").append(idx).append("] must contain ").append(RDF_CONTEXT_KEY).append(" property").append(sep);
        }
        if (checkAbsence(credential, "type", "@type")) {
            sb.append(" - VerifiableCredential[").append(idx).append("] must contain 'type' property").append(sep);
        }
        if (checkAbsence(credential, "credentialSubject")) {
            sb.append(" - VerifiableCredential[").append(idx).append("] must contain 'credentialSubject' property").append(sep);
        }
        if (checkAbsence(credential, "issuer")) {
            sb.append(" - VerifiableCredential[").append(idx).append("] must contain 'issuer' property").append(sep);
        }
        sb.append(getVersionProcessor(credential).validateDates(credential, idx));
        return sb.toString();
    }

    private VersionedCredentialProcessor getVersionProcessor(VerifiableCredential credential) {
        Object ctx = credential.getJsonObject().get(RDF_CONTEXT_KEY);
        boolean isVc2 = (ctx instanceof java.util.List<?>)
                ? ((java.util.List<?>) ctx).contains(VC2_CONTEXT)
                : VC2_CONTEXT.equals(ctx);
        return isVc2 ? vc2Processor : vc11Processor;
    }

    private boolean checkAbsence(JsonLDObject container, String... keys) {
        for (String key : keys) {
            if (container.getJsonObject().containsKey(key)) {
                return false;
            }
        }
        return true;
    }

    private TypedCredentials getCredentials(VerifiablePresentation vp) {
        log.trace("getCredentials.enter; got VP: {}", vp);
        Object obj = vp.getJsonObject().get("verifiableCredential");
        Map<VerifiableCredential, TrustFrameworkBaseClass> creds;
        switch (obj) {
            case null -> creds = Collections.emptyMap();
            case List<?> l -> {
                creds = new LinkedHashMap<>(l.size());
                for (Object entry : l) {
                    if (entry instanceof String jwtStr) {
                        VerifiableCredential unwrapped = tryUnwrapJwtVc(jwtStr, vp.getDocumentLoader());
                        if (unwrapped != null) {
                            creds.put(unwrapped, resolveBaseClass(unwrapped));
                        }
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> entryMap = (Map<String, Object>) entry;
                    if (isEnvelopedVerifiableCredential(resolveType(entryMap), entryMap.get(RDF_CONTEXT_KEY))) {
                        VerifiableCredential unwrapped = tryUnwrapEnvelopedVc(entryMap, vp.getDocumentLoader());
                        if (unwrapped != null) {
                            creds.put(unwrapped, resolveBaseClass(unwrapped));
                        }
                        continue;
                    }
                    VerifiableCredential vc = VerifiableCredential.fromMap(entryMap);
                    vc.setDocumentLoader(vp.getDocumentLoader());
                    creds.put(vc, resolveBaseClass(vc));
                }
            }
            case String jwtStr -> {
                VerifiableCredential unwrapped = tryUnwrapJwtVc(jwtStr, vp.getDocumentLoader());
                if (unwrapped != null) {
                    creds = Map.of(unwrapped, resolveBaseClass(unwrapped));
                } else {
                    creds = Collections.emptyMap();
                }
            }
            default -> {
                @SuppressWarnings("unchecked")
                VerifiableCredential vc = VerifiableCredential.fromMap((Map<String, Object>) obj);
                vc.setDocumentLoader(vp.getDocumentLoader());
                creds = Map.of(vc, resolveBaseClass(vc));
            }
        }
        TypedCredentials tcs = new TypedCredentials(vp, creds);
        log.trace("getCredentials.exit; returning: {}", tcs);
        return tcs;
    }

    /**
     * Attempts to unwrap an EnvelopedVerifiableCredential (Loire data: URI) to a VC.
     */
    private VerifiableCredential tryUnwrapEnvelopedVc(Map<String, Object> entryMap,
        DocumentLoader docLoader) {
        Object idObj = resolveId(entryMap);
        if (!(idObj instanceof String idStr) || !idStr.startsWith("data:")) {
            log.debug("tryUnwrapEnvelopedVc; no data: URI in EnvelopedVerifiableCredential");
            return null;
        }
        try {
            int commaIdx = idStr.indexOf(',');
            if (commaIdx < 0) {
                int semiIdx = idStr.indexOf(';');
                if (semiIdx < 0) {
                    return null;
                }
                commaIdx = semiIdx;
            }
            String jwtStr = idStr.substring(commaIdx + 1);
            return tryUnwrapJwtVc(jwtStr, docLoader);
        } catch (Exception ex) {
            log.debug("tryUnwrapEnvelopedVc; failed to unwrap: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Attempts to unwrap a compact JWT VC string to a VerifiableCredential for semantic processing.
     */
    private VerifiableCredential tryUnwrapJwtVc(String jwtStr, DocumentLoader docLoader) {
        if (!jwtStr.startsWith("eyJ")) {
            return null;
        }
        try {
            ContentAccessor jwtContent = new ContentAccessorDirect(jwtStr);
            CredentialFormat innerFormat = formatDetector.detect(jwtContent);
            ContentAccessor unwrapped;
            if (innerFormat == CredentialFormat.GAIAX_V2_LOIRE) {
                unwrapped = loireJwtParser.unwrap(jwtContent);
            } else {
                unwrapped = jwtPreprocessor.unwrap(jwtContent);
            }
            VerifiableCredential vc = VerifiableCredential.fromJson(unwrapped.getContentAsString());
            vc.setDocumentLoader(docLoader);
            return vc;
        } catch (Exception ex) {
            log.debug("tryUnwrapJwtVc; failed to unwrap JWT VC: {}", ex.getMessage());
            return null;
        }
    }

    private TypedCredentials getCredentials(VerifiableCredential vc) {
        log.trace("getCredentials.enter; got VC: {}", vc);
        TypedCredentials tcs = new TypedCredentials(null, Map.of(vc, resolveBaseClass(vc)));
        log.trace("getCredentials.exit; returning: {}", tcs);
        return tcs;
    }

    private void initLoaders() {
        if (!loadersInitialised) {
            synchronized (this) {
                if (!loadersInitialised) {
                    log.debug("initLoaders; Setting up SchemeRouter");
                    SchemeRouter loader = (SchemeRouter) SchemeRouter.defaultInstance();
                    loader.set("file", documentLoader);
                    loader.set("http", documentLoader);
                    loader.set("https", documentLoader);
                    loadersInitialised = true;
                }
            }
        }
    }

    private StreamManager getStreamManager() {
        if (streamManager == null) {
            synchronized (this) {
                if (streamManager == null) {
                    // Make sure Caching com.apicatalog.jsonld DocumentLoader is set up.
                    initLoaders();
                    log.debug("getStreamManager; Setting up Jena caching Locator");
                    StreamManager clone = StreamManager.get().clone();
                    clone.clearLocators();
                    clone.addLocator(new CachingLocator(fileStore));
                    streamManager = clone;
                }
            }
        }
        return streamManager;
    }

    private TrustFrameworkBaseClass resolveBaseClass(VerifiableCredential credential) {
        ContentAccessor ontology = schemaStore.getCompositeSchema(SchemaStore.SchemaType.ONTOLOGY);
        TrustFrameworkBaseClass result = ClaimValidator.getSubjectType(
                ontology, getStreamManager(), credential.toJson(), trustFrameworkBaseClassUris);
        if (result == null) {
            result = UNKNOWN;
        }
        log.debug("resolveBaseClass; got type result: {}", result);
        return result;
    }


    /* Credential signatures verification */
    private List<Validator> checkCryptography(TypedCredentials tcs, boolean verifyVP, boolean verifyVC) {
        log.debug("checkCryptography.enter;");
        long timestamp = System.currentTimeMillis();

        Set<Validator> validators = new HashSet<>();
        try {
            if (verifyVC) {
                for (VerifiableCredential credential : tcs.getCredentials()) {
                    validators.add(checkSignature(credential));
                }
            }
            if (verifyVP) {
                validators.add(checkSignature(tcs.getPresentation()));
            }
        } catch (VerificationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("checkCryptography.error", ex);
            throw new VerificationException("Signatures error; " + ex.getMessage(), ex);
        }
        timestamp = System.currentTimeMillis() - timestamp;
        log.debug("checkCryptography.exit; returning: {}; time taken: {}", validators, timestamp);
        return new ArrayList<>(validators);
    }

    /**
     * Returns true if the JWT claims represent a Verifiable Presentation.
     * Detects ICAM v24.07 (top-level {@code type} claim) and older format ({@code vp} claim).
     */
    private boolean isVpJwtClaims(JWTClaimsSet claims) {
        Object typeObj = claims.getClaim("type");
        if (typeObj instanceof List<?> types && types.contains("VerifiablePresentation")) {
            return true;
        }
        if (typeObj instanceof String typeStr && "VerifiablePresentation".equals(typeStr)) {
            return true;
        }
        return claims.getClaim("vp") != null;
    }

    /**
     * Checks that VP JWT {@code iss} matches the VP {@code holder} field (ICAM v24.07, ADR-3).
     * Called only when verifySemantics=true and the outer JWT was successfully verified.
     *
     * @param claims already-parsed JWT claims (avoids re-parsing the JWT body)
     */
    private void checkVpJwtHolder(JWTClaimsSet claims) {
        if (!isVpJwtClaims(claims)) {
            return;
        }
        try {
            // ICAM v24.07 (Loire): holder is a top-level claim; older format: nested in vp claim
            String holder = claims.getStringClaim("holder");
            if (holder == null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> vpClaim = (Map<String, Object>) claims.getClaim("vp");
                if (vpClaim != null) {
                    holder = (String) vpClaim.get("holder");
                }
            }
            String iss = claims.getIssuer();
            // Loire VPs: holder is optional per VCDM 2.0; when absent, the VP issuer (iss)
            // is implicitly the holder. Also check top-level "issuer" claim as Loire fallback.
            if (holder == null) {
                String issuerClaim = claims.getStringClaim("issuer");
                if (issuerClaim != null && iss != null && !iss.equals(issuerClaim)) {
                    throw new VerificationException(
                        "VP JWT iss '" + iss + "' does not match VP issuer '" + issuerClaim + "'");
                }
                // holder absent: iss is implicitly the holder — binding passes
                log.debug("checkVpJwtHolder; holder absent, iss '{}' accepted as implicit holder",
                    iss);
                return;
            }
            if (iss == null) {
                log.warn("checkVpJwtHolder; VP JWT has holder '{}' but no iss claim", holder);
                return;
            }
            if (!iss.equals(holder)) {
                throw new VerificationException(
                        "VP JWT iss '" + iss + "' does not match VP holder '" + holder + "'");
            }
        } catch (java.text.ParseException ex) {
            // getStringClaim can throw ParseException on malformed claims
            log.warn("checkVpJwtHolder; unexpected claims parse error: {}", ex.getMessage());
        }
    }

    /**
     * Verifies inner VC credentials in a VP JWT.
     * Handles EnvelopedVerifiableCredential (ICAM v24.07), plain compact JWT strings,
     * and inline JSON-LD VCs with proof fields.
     */
    private List<Validator> verifyInnerVcCredentials(JsonLDObject ld) {
        Object vcArrayObj = ld.getJsonObject().get("verifiableCredential");
        if (vcArrayObj == null) {
            return List.of();
        }

        List<Object> vcEntries;
        if (vcArrayObj instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> cast = (List<Object>) list;
            vcEntries = cast;
        } else {
            vcEntries = List.of(vcArrayObj);
        }

        List<Validator> validators = new ArrayList<>();
        for (Object entry : vcEntries) {
            if (entry instanceof String jwtStr && jwtStr.startsWith("eyJ")) {
                // Plain compact JWT string
                validators.add(jwtSignatureVerifier.verify(jwtStr));
            } else if (entry instanceof Map<?, ?> vcMap) {
                if (isEnvelopedVerifiableCredential(resolveType(vcMap), vcMap.get(RDF_CONTEXT_KEY))) {
                    // ICAM v24.07 EnvelopedVerifiableCredential: extract JWT from data: URL
                    Object idObj = resolveId(vcMap);
                    if (!(idObj instanceof String idStr)) {
                        throw new ClientException(
                                "EnvelopedVerifiableCredential missing 'id' field");
                    }
                    validators.add(jwtSignatureVerifier.verifyFromDataUrl(idStr));
                } else {
                    // Inline JSON-LD VC with proof — existing LD verification path
                    @SuppressWarnings("unchecked")
                    VerifiableCredential innerVc = VerifiableCredential.fromMap(
                            (Map<String, Object>) vcMap);
                    innerVc.setDocumentLoader(ld.getDocumentLoader());
                    validators.add(checkSignature(innerVc));
                }
            }
        }
        return validators;
    }

    private boolean isEnvelopedVerifiableCredential(Object typeObj, Object contextObj) {
        boolean hasType = false;
        if (typeObj instanceof String typeStr) {
            hasType = "EnvelopedVerifiableCredential".equals(typeStr);
        } else if (typeObj instanceof List<?> types) {
            hasType = types.contains("EnvelopedVerifiableCredential");
        }
        if (!hasType) {
            return false;
        }
        // Also verify the VC 2.0 context to avoid false-positives from other vocabularies
        if (contextObj instanceof String ctx) {
            return VC2_CONTEXT.equals(ctx);
        }
        if (contextObj instanceof List<?> contexts) {
            return contexts.contains(VC2_CONTEXT);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Validator checkSignature(JsonLDObject payload) {
        Map<String, Object> proofMap = (Map<String, Object>) payload.getJsonObject().get("proof");
        if (proofMap == null) {
            throw new VerificationException("Signatures error; No proof found");
        }

        LdProof proof = LdProof.fromMap(proofMap);
        if (proof.getType() == null) {
            throw new VerificationException("Signatures error; Proof must have 'type' property");
        }

        try {
            return checkProofSignature(payload, proof);
        } catch (IOException ex) {
            throw new VerificationException(ex);
        }
    }

    private Validator checkProofSignature(JsonLDObject payload, LdProof proof) throws IOException {
        String vmKey = proof.getVerificationMethod().toString();
        Validator validator = validatorCache.getFromCache(vmKey);
        if (validator == null) {
            log.debug("checkSignature; validator not found in cache");
        } else {
            log.debug("checkSignature; got validator from cache");
            JWK jwk = JWK.fromJson(validator.getPublicKey());
            if (signVerifier.verify(payload, proof, jwk, jwk.getAlg())) {
                return validator;
            }

            // validator doesn't verifies any more. let's drop it
            if (dropValidators) {
                validatorCache.removeFromCache(vmKey);
            } else {
                throw new VerificationException("Signatures error; " + payload.getClass().getSimpleName() + " does not match with proof");
            }
        }

        validator = signVerifier.checkSignature(payload, proof);
        Instant expiration = null;
        JWK jwk = JWK.fromJson(validator.getPublicKey());
        String url = jwk.getX5u();
        if (url == null) {
            // When Gaia-X trust framework is enabled, x5u URL is required for trust anchor validation
            if (gaiaxTrustFrameworkEnabled) {
                throw new VerificationException("Signatures error; no trust anchor url found");
            }
            log.debug("checkProofSignature; no x5u URL in JWK, skipping trust anchor validation (gaiax.enabled=false)");
        } else {
            expiration = hasPEMTrustAnchorAndIsNotExpired(url);
        }
        if (expiration == null) {
            // set default expiration at next midnight
            expiration = Instant.now().plus(validatorExpiration).truncatedTo(ChronoUnit.DAYS);
        }
        validator.setExpirationDate(expiration);
        validatorCache.addToCache(validator);
        return validator;
    }

    /**
     * Fetches the certificate chain from the given x5u URL, validates certificate expiry,
     * and checks the URI against the Gaia-X Trust Anchor Registry (when gaiax.enabled=true).
     *
     * <p>Note: does not perform PKIX path validation (RFC 5280) or revocation checking.
     * ICAM 24.07 does not explicitly require these — it requires x5c/x5u presence (MUST)
     * and x5u trust anchor resolution (SHOULD).
     */
    @SuppressWarnings("unchecked")
    private Instant hasPEMTrustAnchorAndIsNotExpired(String uri) throws VerificationException {
        log.debug("hasPEMTrustAnchorAndIsNotExpired.enter; got uri: {}, gaiaxTrustFrameworkEnabled: {}", uri, gaiaxTrustFrameworkEnabled);
        String pem = rest.getForObject(uri, String.class);
        InputStream certStream = new ByteArrayInputStream(Objects.requireNonNull(pem).getBytes(StandardCharsets.UTF_8));
        List<X509Certificate> certs;
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            certs = (List<X509Certificate>) certFactory.generateCertificates(certStream);
        } catch (CertificateException ex) {
            log.warn("hasPEMTrustAnchorAndIsNotExpired; certificate error: {}", ex.getMessage());
            throw new VerificationException("Signatures error; " + ex.getMessage());
        }

        //Then extract relevant cert
        X509Certificate relevant = null;
        for (X509Certificate cert : certs) {
            try {
                cert.checkValidity();
                if (relevant == null || relevant.getNotAfter().before(cert.getNotAfter())) { // .after(cert.getNotAfter())) {
                    relevant = cert;
                }
            } catch (Exception ex) {
                log.warn("hasPEMTrustAnchorAndIsNotExpired; check validity error: {}", ex.getMessage());
                throw new VerificationException("Signatures error; " + ex.getMessage());
            }
        }

        // Only call Gaia-X Trust Anchor Registry when Gaia-X trust framework is enabled
        if (gaiaxTrustFrameworkEnabled) {
            if (trustAnchorAddr == null || trustAnchorAddr.isBlank()) {
                log.warn("hasPEMTrustAnchorAndIsNotExpired; Gaia-X trust framework enabled but trust-anchor-url not configured");
                throw new VerificationException("Signatures error; Gaia-X trust framework enabled but trust-anchor-url not configured");
            }
            try {
                ResponseEntity<Map> resp = rest.postForEntity(trustAnchorAddr, Map.of("uri", uri), Map.class);
                if (!resp.getStatusCode().is2xxSuccessful()) {
                    log.info("hasPEMTrustAnchorAndIsNotExpired; Trust anchor is not set in the registry. URI: {}", uri);
                    throw new VerificationException("Signatures error; trust anchor is not registered in the Gaia-X registry. URI: " + uri);
                }
            } catch (VerificationException ex) {
                throw ex;
            } catch (Exception ex) {
                log.warn("hasPEMTrustAnchorAndIsNotExpired; trust anchor error: {}", ex.getMessage());
                throw new VerificationException("Signatures error; " + ex.getMessage());
            }
        } else {
            log.debug("hasPEMTrustAnchorAndIsNotExpired; skipping Gaia-X trust anchor registry validation (gaiax.enabled=false)");
        }
        Instant exp = relevant == null ? null : relevant.getNotAfter().toInstant();
        log.debug("hasPEMTrustAnchorAndIsNotExpired.exit; returning: {}", exp);
        return exp;
    }

    /**
     * Enforces mandatory x5c/x5u trust chain validation for Loire credentials
     * when Gaia-X trust framework is enabled (ICAM 24.07).
     *
     * @param jwtValidator the validator from JWT signature verification
     * @throws VerificationException if x5c or x5u is missing in the publicKeyJwk
     */
    private void enforceLoireTrustChain(Validator jwtValidator) {
        String publicKeyJson = jwtValidator.getPublicKey();
        if (publicKeyJson == null) {
            throw new VerificationException(
                "Loire trust chain: publicKeyJwk is required but not available");
        }
        try {
            JsonNode jwkNode = OBJECT_MAPPER.readTree(publicKeyJson);
            boolean hasX5c = jwkNode.has("x5c") && !jwkNode.get("x5c").isEmpty();
            boolean hasX5u = jwkNode.has("x5u") && !jwkNode.get("x5u").asText().isBlank();
            if (!hasX5c && !hasX5u) {
                throw new VerificationException(
                    "Loire trust chain: publicKeyJwk must contain x5c or x5u certificate chain "
                        + "(ICAM 24.07 mandatory trust anchor). kid: " + jwtValidator.getDidURI());
            }
            if (hasX5c) {
                validateX5cChain(jwkNode.get("x5c"), jwtValidator.getDidURI());
            }
            if (hasX5u) {
                String x5uUrl = jwkNode.get("x5u").asText();
                hasPEMTrustAnchorAndIsNotExpired(x5uUrl);
            }
            log.debug("enforceLoireTrustChain; trust chain validated for kid: {}",
                jwtValidator.getDidURI());
        } catch (VerificationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new VerificationException(
                "Loire trust chain validation failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Validates certificates from the JWK {@code x5c} array: decodes base64 DER entries,
     * checks each certificate is currently valid (not expired), and verifies that the chain
     * is non-empty (ICAM 24.07).
     *
     * <p><strong>Known gap:</strong> validates certificate expiry only. Unlike the x5u path
     * ({@link #hasPEMTrustAnchorAndIsNotExpired}), this does not call the Trust Anchor Registry
     * because the registry API expects a URI, not inline certificates.
     */
    private void validateX5cChain(JsonNode x5cArray, String kid) {
        if (!x5cArray.isArray() || x5cArray.isEmpty()) {
            throw new VerificationException(
                "Loire trust chain: x5c array is empty. kid: " + kid);
        }
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            for (JsonNode entry : x5cArray) {
                byte[] certBytes;
                try {
                    certBytes = java.util.Base64.getDecoder().decode(entry.asText());
                } catch (IllegalArgumentException ex) {
                    throw new VerificationException(
                        "Loire trust chain: x5c entry is not valid base64. kid: " + kid, ex);
                }
                X509Certificate cert = (X509Certificate) certFactory.generateCertificate(
                    new ByteArrayInputStream(certBytes));
                cert.checkValidity();
            }
            log.debug("validateX5cChain; {} certificate(s) valid for kid: {}",
                x5cArray.size(), kid);
        } catch (CertificateException ex) {
            throw new VerificationException(
                "Loire trust chain: x5c certificate validation failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Enforces the Loire DID method restriction: only {@code did:web} is accepted
     * when Gaia-X trust framework is enabled (ICAM 24.07).
     *
     * @param compactJwt the original compact JWT body
     * @throws VerificationException if the issuer uses a non-did:web DID method
     */
    private void enforceDidWebRestriction(String compactJwt) {
        try {
            JWTClaimsSet claims = SignedJWT.parse(compactJwt).getJWTClaimsSet();
            String iss = claims.getIssuer();
            String issuerClaim = claims.getStringClaim("issuer");
            // Validate both when present — prevents spoofing via injected issuer claim
            if (iss != null) {
                validateDidWeb(iss);
            }
            if (issuerClaim != null && !issuerClaim.equals(iss)) {
                validateDidWeb(issuerClaim);
            }
            if (iss == null && issuerClaim == null) {
                throw new VerificationException(
                    "Loire DID method restriction: no issuer found in JWT iss or payload issuer "
                        + "(ICAM 24.07 requires did:web)");
            }
        } catch (VerificationException ex) {
            throw ex;
        } catch (ParseException ex) {
            throw new VerificationException(
                "Loire DID method restriction: JWT claims parse error: " + ex.getMessage(), ex);
        }
    }

    private void validateDidWeb(String did) {
        if (did.startsWith("did:key")) {
            throw new VerificationException(
                "Loire DID method restriction: did:key is not accepted "
                    + "(ICAM 24.07 requires did:web with JWK + x5c/x5u). Issuer: " + did);
        }
        if (!did.startsWith("did:web:")) {
            throw new VerificationException(
                "Loire DID method restriction: only did:web is accepted "
                    + "(ICAM 24.07). Found: " + did);
        }
    }
}
