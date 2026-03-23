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

    public CredentialVerificationResult verifyCredential(ContentAccessor payload, boolean strict, TrustFrameworkBaseClass expectedClass,
                                                         boolean verifySemantics, boolean verifySchema, boolean verifyVPSignatures,
                                                         boolean verifyVCSignatures) throws VerificationException {
        log.debug("verifyCredential.enter; strict: {}, expectedType: {}, verifySemantics: {}, verifySchema: {}, verifyVPSignatures: {}, verifyVCSignatures: {}",
                strict, expectedClass, verifySemantics, verifySchema, verifyVPSignatures, verifyVCSignatures);
        long stamp = System.currentTimeMillis();

        // Read content once (fixes double-read)
        String body = payload.getContentAsString().strip();
        payload = new ContentAccessorDirect(body, payload.getContentType());
        boolean isJwt = jwtPreprocessor.isJwtWrapped(payload);
        // JWT signature verification — fires before unwrap (Issue #12)
        Validator jwtValidator = null;
        if (isJwt && (verifyVCSignatures || verifyVPSignatures)) {
            jwtValidator = jwtSignatureVerifier.verify(body);
        }
        // Version dispatch — payload is unwrapped JSON-LD after this point
        // JWT-wrapped payloads are VC 2.0-only; fall back to vc2Processor when context parse fails
        boolean isVc2 = isVc2Context(body) || isJwt;
        payload = (isVc2 ? vc2Processor : vc11Processor).preProcess(payload);
        // NOTE: CAT-FR-GD-02's VcStructureDetector.isVcStructured() call MUST be placed AFTER this
        // line, not before — a JWT-wrapped VC 2.0 payload would not be recognised as a VC until unwrapped.

        // syntactic validation
        JsonLDObject ld = parseContent(payload);
        log.debug("verifyCredential; content parsed, time taken: {}", System.currentTimeMillis() - stamp);

        // see https://gitlab.eclipse.org/eclipse/xfsc/cat/fc-service/-/issues/200
        // add GAIA-X context(s) if present
        ld.setDocumentLoader(this.documentLoader);

        // semantic verification
        long stamp2 = System.currentTimeMillis();
        TypedCredentials typedCredentials = parseCredentials(ld, strict && requireVP, verifySemantics);
        log.debug("verifyCredential; credentials processed, time taken: {}", System.currentTimeMillis() - stamp2);

        // Gate on gaiaxTrustFrameworkEnabled: non-Gaia-X VC 2.0 credentials have no known TrustFrameworkBaseClass
        // TODO(gaia-x-loire): when Loire ontology support lands, resolveBaseClass should return the correct
        //   Loire type for Gaia-X credentials. Remove this gate and let hasClasses() enforce the check unconditionally.
        if (verifySemantics && gaiaxTrustFrameworkEnabled && !typedCredentials.hasClasses()) {
            throw new VerificationException("Semantic Error: no proper CredentialSubject found");
        }

        int partCount = 0;
        int soffCount = 0;
        TrustFrameworkBaseClass baseClass = UNKNOWN;
        Collection<TrustFrameworkBaseClass> baseClasses = typedCredentials.getBaseClasses();
        if (baseClasses.size() > 1) {
            Map<TrustFrameworkBaseClass, Integer> classMap = baseClasses.stream().reduce(new HashMap<>(), (map, e) -> {
                        map.merge(e, 1, Integer::sum);
                        return map;
                    },
                    (m, m2) -> {
                        m.putAll(m2);
                        return m;
                    }
            );
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
                throw new VerificationException("Semantic error: expected credential of type " + expectedClass + " but found " + baseClass);
            }
        }

        stamp2 = System.currentTimeMillis();
        List<CredentialClaim> claims = extractClaims(payload);

        FilteredClaims filtered = protectedNamespaceFilter.filterClaims(claims, "claims extraction");
        claims = filtered.claims();

        log.debug("verifyCredential; claims extracted: {}, time taken: {}", (claims == null ? "null" : claims.size()),
                System.currentTimeMillis() - stamp2);

        if (verifySemantics && strict) {
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
                StringBuilder sb = new StringBuilder("Semantic Errors: There are different subject ids in credential subjects: ").append(sep);
                for (String s : subjects) {
                    sb.append(s).append(sep);
                }
                throw new VerificationException(sb.toString());
            } else if (subjects.isEmpty()) {
                throw new VerificationException("Semantic Errors: There is no uniquely identified credential subject");
            }
        }

        // schema verification
        if (verifySchema) {
            eu.xfsc.fc.core.pojo.SchemaValidationResult result = schemaValidationService.validateClaimsAgainstCompositeSchema(claims);
            if (result == null || !result.isConforming()) {
                throw new VerificationException("Schema error: " + (result == null ? "unknown" : result.getValidationReport()));
            }
        }

        // VP JWT: detect VP type, run semantic holder check, and guard inner VC verification
        boolean isVpJwt = false;
        if (isJwt && jwtValidator != null) {
            try {
                JWTClaimsSet jwtClaims = SignedJWT.parse(body).getJWTClaimsSet();
                isVpJwt = isVpJwtClaims(jwtClaims);
                if (verifySemantics) {
                    checkVpJwtHolder(jwtClaims);
                }
            } catch (java.text.ParseException ex) {
                // body was already verified by JwtSignatureVerifier — unexpected
                log.warn("verifyCredential; JWT claims re-parse error: {}", ex.getMessage());
            }
        }

        // signature verification
        List<Validator> validators;
        if (isJwt) {
            validators = jwtValidator != null ? new ArrayList<>(List.of(jwtValidator)) : null;
            if (verifyVCSignatures && isVpJwt) {
                List<Validator> innerValidators = verifyInnerVcCredentials(ld);
                if (!innerValidators.isEmpty()) {
                    if (validators == null) {
                        validators = innerValidators;
                    } else {
                        validators.addAll(innerValidators);
                    }
                }
            }
        } else if (verifyVPSignatures || verifyVCSignatures) {
            validators = checkCryptography(typedCredentials, verifyVPSignatures, verifyVCSignatures);
        } else {
            validators = null;
        }

        String id = typedCredentials.getID();
        String issuer = typedCredentials.getIssuer();
        Instant issuedDate = typedCredentials.getIssuanceDate();

        CredentialVerificationResult result;
        if (baseClass == PARTICIPANT) {
            if (issuer == null) {
                issuer = id;
            }
            String method = typedCredentials.getProofMethod();
            String holder = typedCredentials.getHolder();
            String name = holder == null ? issuer : holder;
            result = new CredentialVerificationResultParticipant(Instant.now(), AssetStatus.ACTIVE.getValue(), issuer, issuedDate,
                    claims, validators, name, method);
        } else if (baseClass == SERVICE_OFFERING) {
            result = new CredentialVerificationResultOffering(Instant.now(), AssetStatus.ACTIVE.getValue(), issuer, issuedDate,
                    id, claims, validators);
        } else if (baseClass == RESOURCE) {
            result = new CredentialVerificationResultResource(Instant.now(), AssetStatus.ACTIVE.getValue(), issuer, issuedDate,
                    id, claims, validators);
        } else {
            result = new CredentialVerificationResult(Instant.now(), AssetStatus.ACTIVE.getValue(), issuer, issuedDate,
                    id, claims, validators);
        }

        if (filtered.hasWarning()) {
            result.setWarnings(List.of(filtered.warning()));
        }

        stamp = System.currentTimeMillis() - stamp;
        log.debug("verifyCredential.exit; returning: {}; time taken: {}", result, stamp);
        return result;
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
            JsonNode ctx = root.get("@context");
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
        if (checkAbsence(presentation, "@context")) {
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
        if (checkAbsence(credential, "@context")) {
            sb.append(" - VerifiableCredential[").append(idx).append("] must contain '@context' property").append(sep);
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
        Object ctx = credential.getJsonObject().get("@context");
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
        if (obj == null) {
            creds = Collections.emptyMap();
        } else if (obj instanceof List) {
            List<?> l = (List<?>) obj;
            creds = new LinkedHashMap<>(l.size());
            for (Object entry : l) {
                if (entry instanceof String) {
                    // Compact JWT VC — opaque for semantic processing; signatures handled by verifyInnerVcCredentials
                    // TODO(gaia-x-loire): if Loire requires semantic validation of embedded JWT VCs, unwrap and
                    //   resolve their base class here instead of skipping.
                    continue;
                }
                @SuppressWarnings("unchecked")
                VerifiableCredential vc = VerifiableCredential.fromMap((Map<String, Object>) entry);
                vc.setDocumentLoader(vp.getDocumentLoader());
                creds.put(vc, resolveBaseClass(vc));
            }
        } else if (obj instanceof String) {
            // Single compact JWT VC — opaque for semantic processing
            // TODO(gaia-x-loire): same as list case above — unwrap if Loire requires semantic validation.
            creds = Collections.emptyMap();
        } else {
            @SuppressWarnings("unchecked")
            VerifiableCredential vc = VerifiableCredential.fromMap((Map<String, Object>) obj);
            vc.setDocumentLoader(vp.getDocumentLoader());
            creds = Map.of(vc, resolveBaseClass(vc));
        }
        TypedCredentials tcs = new TypedCredentials(vp, creds);
        log.trace("getCredentials.exit; returning: {}", tcs);
        return tcs;
    }

    private TypedCredentials getCredentials(VerifiableCredential vc) {
        log.trace("getCredentials.enter; got VC: {}", vc);
        TypedCredentials tcs = new TypedCredentials(null, Map.of(vc, resolveBaseClass(vc)));
        log.trace("getCredentials.exit; returning: {}", tcs);
        return tcs;
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

    public void setBaseClassUri(TrustFrameworkBaseClass baseClass, String uri) {
        trustFrameworkBaseClassUris.put(baseClass, uri);
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
            // ICAM v24.07: holder is a top-level claim; older format: nested in vp claim
            String holder = claims.getStringClaim("holder");
            if (holder == null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> vpClaim = (Map<String, Object>) claims.getClaim("vp");
                if (vpClaim != null) {
                    holder = (String) vpClaim.get("holder");
                }
            }
            String iss = claims.getIssuer();
            if (holder == null) {
                throw new VerificationException(
                        "VP JWT missing 'holder' claim — iss/holder binding cannot be verified");
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
                if (isEnvelopedVerifiableCredential(vcMap.get("type"), vcMap.get("@context"))) {
                    // ICAM v24.07 EnvelopedVerifiableCredential: extract JWT from data: URL
                    Object idObj = vcMap.get("id");
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

    private static final String VC_2_CONTEXT = "https://www.w3.org/ns/credentials/v2";

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
            return VC_2_CONTEXT.equals(ctx);
        }
        if (contextObj instanceof List<?> contexts) {
            return contexts.contains(VC_2_CONTEXT);
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
}
