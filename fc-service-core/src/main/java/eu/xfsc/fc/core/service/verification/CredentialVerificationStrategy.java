package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.service.verification.VerificationConstants.JWT_PREFIX;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.MEDIA_TYPE_VC_JWT;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.MEDIA_TYPE_VC_LD_JSON;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.MEDIA_TYPE_VP_JWT;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.MEDIA_TYPE_VP_LD_JSON;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.RDF_CONTEXT_KEY;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.VC_20_CONTEXT;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.VERIFIABLE_CREDENTIAL_KEY;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.VP_TYPE;

import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.SchemeRouter;
import com.danubetech.verifiablecredentials.VerifiableCredential;
import com.danubetech.verifiablecredentials.VerifiablePresentation;
import com.danubetech.verifiablecredentials.jsonld.VerifiableCredentialKeywords;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.FilteredClaims;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.pojo.SchemaValidationResult;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.trustframework.ResolvedRole;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkService;
import eu.xfsc.fc.core.service.verification.cache.CachingLocator;
import eu.xfsc.fc.core.service.verification.claims.ClaimExtractionService;
import eu.xfsc.fc.core.util.ClaimValidator;
import foundation.identity.jsonld.JsonLDObject;
import jakarta.annotation.PostConstruct;

import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.riot.system.stream.StreamManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verification strategy for Verifiable Credential payloads (VP/VC).
 *
 * <p>Owns the verification pipeline orchestration: format detection, semantic checks,
 * schema verification, signature collection, claim extraction, role resolution, and result
 * assembly. Format-specific envelope handling is delegated to {@link CredentialFormatProcessor}
 * implementations; W3C VC 2.0 Enveloped Credential plumbing lives in
 * {@link EnvelopedCredentialResolver}; VC 2.0 date validation lives in {@link Vc2DateValidation}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CredentialVerificationStrategy implements RdfIngestionStrategy {

  @Value("${federated-catalogue.verification.require-vp:false}")
  private boolean requireVP;

  private final SchemaModuleConfigService schemaModuleConfigService;
  private final TrustFrameworkRegistry trustFrameworkRegistry;
  private final TrustFrameworkService trustFrameworkService;
  private final ProtectedNamespaceFilter protectedNamespaceFilter;
  private final SchemaStore schemaStore;
  private final SchemaValidationService schemaValidationService;
  @Qualifier("contextCacheFileStore")
  private final FileStore fileStore;
  private final DocumentLoader documentLoader;
  private final CredentialFormatDetector formatDetector;
  private final List<CredentialFormatProcessor> formatProcessors;
  private final EnvelopedCredentialResolver envelopedCredentialResolver;
  private final ClaimExtractionService claimExtractionService;

  private volatile boolean loadersInitialised;
  private volatile StreamManager streamManager;

  private Map<CredentialFormat, CredentialFormatProcessor> processorsByFormat;

  @PostConstruct
  private void indexProcessors() {
    processorsByFormat = new EnumMap<>(CredentialFormat.class);
    for (CredentialFormatProcessor p : formatProcessors) {
      processorsByFormat.put(p.getFormat(), p);
    }
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
  ) {
  }

  @Override
  public CredentialVerificationResult ingest(ContentAccessor payload,
                                             boolean verifySemantics, boolean verifySchema,
                                             boolean verifyVPSignatures, boolean verifyVCSignatures)
      throws VerificationException {
    log.debug("ingest.enter; verifySemantics: {}, verifySchema: {},"
            + " verifyVPSignatures: {}, verifyVCSignatures: {}",
        verifySemantics, verifySchema, verifyVPSignatures, verifyVCSignatures);
    long stamp = System.currentTimeMillis();

    VerificationContext ctx = detectAndUnwrap(payload, verifyVCSignatures || verifyVPSignatures);

    JsonLDObject ld = parseContent(ctx.payload());
    ld.setDocumentLoader(this.documentLoader);
    log.debug("ingest; content parsed, time taken: {}", System.currentTimeMillis() - stamp);

    TypedCredentials typedCredentials = parseCredentials(ld, requireVP, verifySemantics, ctx.format());

    if (verifySemantics && trustFrameworkService.hasAnyEnabled() && !typedCredentials.hasClasses()) {
      throw new VerificationException("Semantic Error: no proper CredentialSubject found");
    }

    ResolvedRole resolvedRole = resolvePrimaryRole(typedCredentials, verifySemantics);
    FilteredClaims filtered = extractAndValidateClaims(ctx.payload());

    if (verifySchema) {
      if (!schemaModuleConfigService.isModuleEnabled(SchemaModuleType.SHACL)) {
        throw new ClientException(
            SchemaModuleConfigService.MODULE_DISABLED_PREFIX + SchemaModuleType.SHACL);
      }
      validateSchema(filtered.claims());
    }

    List<Validator> validators = collectValidators(ctx, ld,
        verifySemantics, verifyVCSignatures, verifyVPSignatures);

    CredentialVerificationResult result =
        assembleResult(resolvedRole, typedCredentials, filtered.claims(), validators);
    if (filtered.hasWarning()) {
      result.setWarnings(List.of(filtered.warning()));
    }

    log.debug("ingest.exit; returning: {}; time taken: {}",
        result, System.currentTimeMillis() - stamp);
    return result;
  }

  /**
   * Phase 1: Detect credential format, verify the JWT signature, and unwrap to JSON-LD.
   * After this method returns the payload is always JSON-LD.
   */
  private VerificationContext detectAndUnwrap(ContentAccessor payload, boolean verifySigs) {
    String body = payload.getContentAsString().strip();
    payload = new ContentAccessorDirect(body, payload.getContentType());

    validateContentTypeMatchesBody(payload.getContentType(), body);

    CredentialFormat format = formatDetector.detect(payload);

    // W3C VC 2.0 Enveloped wrapper — unwrap to the inner JWT before dispatching.
    if (!body.startsWith(JWT_PREFIX)) {
      String envelopedJwt = envelopedCredentialResolver.extractEnvelopedJwt(body);
      if (envelopedJwt != null) {
        body = envelopedJwt;
        payload = new ContentAccessorDirect(body);
        format = formatDetector.detect(payload);
      }
    }

    // Reject ambiguous JWTs — UNKNOWN non-JWT payloads are routed to the non-credential RDF
    // path in ingest() before parseContent().
    if (format == CredentialFormat.UNKNOWN && body.startsWith(JWT_PREFIX)) {
      throw new ClientException(
          "Unrecognizable JWT credential format — JWT does not have recognized "
              + "typ header or vc/vp wrapper claims");
    }

    CredentialFormatProcessor processor = processorsByFormat.get(format);
    boolean isJwt = false;
    Validator jwtValidator = null;
    if (processor != null) {
      ProcessedEnvelope envelope = processor.process(body, payload, verifySigs);
      payload = envelope.unwrappedPayload();
      isJwt = envelope.wasJwt();
      jwtValidator = envelope.jwtValidator();
    }

    return new VerificationContext(body, format, isJwt, payload, jwtValidator);
  }

  /**
   * Validates that an explicit VC/VP content-type header matches the actual body format.
   * Generic content-types ({@code application/json}, {@code application/ld+json}, or null)
   * are auto-detected and skip this check.
   *
   * @throws ClientException if the content-type declares JWT but body is JSON-LD, or vice versa
   */
  private static void validateContentTypeMatchesBody(String contentType, String body) {
    if (contentType == null) {
      return;
    }
    String ct = contentType.strip().toLowerCase();
    boolean ctExpectsJwt = ct.equals(MEDIA_TYPE_VC_JWT) || ct.equals(MEDIA_TYPE_VP_JWT);
    boolean ctExpectsJsonLd =
        ct.equals(MEDIA_TYPE_VC_LD_JSON) || ct.equals(MEDIA_TYPE_VP_LD_JSON);
    if (!ctExpectsJwt && !ctExpectsJsonLd) {
      return;
    }
    boolean bodyIsJwt = body.startsWith(JWT_PREFIX);
    if (ctExpectsJwt && !bodyIsJwt) {
      throw new ClientException(
          "Content-Type '" + contentType + "' expects a JWT but body is not a JWT");
    }
    if (ctExpectsJsonLd && bodyIsJwt) {
      throw new ClientException(
          "Content-Type '" + contentType + "' expects JSON-LD but body is a JWT");
    }
  }

    /**
     * Phase 2: Resolve the dominant role from typed credentials and validate type constraints.
     */
    private ResolvedRole resolvePrimaryRole(TypedCredentials typedCredentials, boolean verifySemantics) {
      Collection<ResolvedRole> roles = typedCredentials.getResolvedRoles();
      if (roles.isEmpty()) {
        return ResolvedRole.UNKNOWN;
      }
      if (roles.size() > 1 && verifySemantics) {
        long distinctRoles = roles.stream().map(ResolvedRole::role).distinct().count();
        if (distinctRoles > 1) {
          throw new VerificationException("Semantic error: credential has several types: " + roles);
        }
      }
      return roles.iterator().next();
    }

  /**
   * Phase 3: Extract claims and filter protected namespaces.
   */
  private FilteredClaims extractAndValidateClaims(ContentAccessor payload) {
    long stamp = System.currentTimeMillis();
    // Resolve inner EVCs before claim extraction — claim extractors cannot handle EVC wrappers
    // with data: URIs. Intentionally NOT in detectAndUnwrap() because signature verification
    // needs the original EVC entries intact.
    ContentAccessor claimPayload =
        envelopedCredentialResolver.resolveInnerEnvelopedCredentials(payload);
    List<RdfClaim> claims = claimExtractionService.extractCredentialClaims(claimPayload);
    FilteredClaims filtered = protectedNamespaceFilter.filterClaims(claims, "claims extraction");
    log.debug("ingest; claims extracted: {}, time taken: {}",
        filtered.claims() == null ? "null" : filtered.claims().size(),
        System.currentTimeMillis() - stamp);
    return filtered;
  }

  private void validateSchema(List<RdfClaim> claims) {
    SchemaValidationResult result =
        schemaValidationService.validateClaimsAgainstCompositeSchema(claims);
    if (result == null || !result.isConforming()) {
      throw new VerificationException(
          "Schema error: " + (result == null ? "unknown" : result.getValidationReport()));
    }
  }

  /**
   * Phase 4: Collect all signature validators — JWT outer plus JWT inner VCs.
   */
  private List<Validator> collectValidators(VerificationContext ctx, JsonLDObject ld,
                                            boolean verifySemantics, boolean verifyVCSignatures,
                                            boolean verifyVPSignatures) {
    boolean isVpJwt = false;
    if (ctx.isJwt() && ctx.jwtValidator() != null) {
      try {
        JWTClaimsSet jwtClaims = SignedJWT.parse(ctx.body()).getJWTClaimsSet();
        isVpJwt = isVpJwtClaims(jwtClaims);
        if (verifySemantics) {
          checkVpJwtHolder(jwtClaims);
        }
      } catch (ParseException ex) {
        log.warn("collectValidators; JWT claims re-parse error: {}", ex.getMessage(), ex);
      }
    }

    if (ctx.isJwt()) {
      List<Validator> validators = ctx.jwtValidator() != null
          ? new ArrayList<>(List.of(ctx.jwtValidator())) : new ArrayList<>();
      if (verifyVCSignatures && isVpJwt) {
        List<Validator> inner = envelopedCredentialResolver.verifyInnerVcCredentials(ld);
        if (!inner.isEmpty()) {
          validators.addAll(inner);
        }
      }
      return validators;
    }
    if (verifyVPSignatures || verifyVCSignatures) {
      return rejectLdProofVerification();
    }
    return List.of();
  }

  /**
   * Phase 5: Build the generic result from credential data and the resolved role.
   * No branching on role name — the role is stored as data, not used for dispatch.
   */
  private CredentialVerificationResult assembleResult(ResolvedRole resolvedRole,
                                                      TypedCredentials typedCredentials, List<RdfClaim> graphClaims,
                                                      List<Validator> validators) {
    String credentialSubjectId = typedCredentials.getID();
    String issuer = typedCredentials.getIssuer();
    Instant issuedDate = typedCredentials.getIssuanceDate();
    Instant now = Instant.now();
    String status = AssetStatus.ACTIVE.getValue();
    String role = resolvedRole.isResolved() ? resolvedRole.role() : null;
    String profileId = resolvedRole.isResolved() ? resolvedRole.frameworkProfileId() : null;

    String effectiveIssuer = issuer != null ? issuer : credentialSubjectId;
    String holder = typedCredentials.getHolder();
    String name = holder != null ? holder : effectiveIssuer;
    String publicKey = validators.isEmpty() ? null : validators.getFirst().getDidURI();

    CredentialVerificationResult result = new CredentialVerificationResult(now, status,
        effectiveIssuer, issuedDate, credentialSubjectId, graphClaims, validators, role, profileId);
    result.setName(name);
    result.setPublicKey(publicKey);
    return result;
  }

  private TypedCredentials parseCredentials(JsonLDObject ld, boolean vpRequired,
                                            boolean verifySemantics, CredentialFormat format) {
    if (ld.isType(VerifiableCredentialKeywords.JSONLD_TERM_VERIFIABLE_PRESENTATION)) {
      VerifiablePresentation vp = VerifiablePresentation.fromJsonLDObject(ld);
      if (verifySemantics) {
        return verifyPresentation(vp, format);
      }
      return getCredentials(vp, format);
    }
    if (vpRequired) {
      throw new VerificationException(
          "Semantic error: expected credential of type 'VerifiablePresentation',"
              + " actual credential type: " + ld.getTypes());
    }
    if (ld.isType(VerifiableCredentialKeywords.JSONLD_TERM_VERIFIABLE_CREDENTIAL)) {
      VerifiableCredential vc = VerifiableCredential.fromJsonLDObject(ld);
      if (verifySemantics) {
        String err = findVcErrors(vc, 0);
        if (!err.isEmpty()) {
          throw new VerificationException("Semantic error: " + err);
        }
      }
      return getCredentials(vc, format);
    }
    throw new VerificationException("Semantic error: unexpected credential type: " + ld.getTypes());
  }

  private JsonLDObject parseContent(ContentAccessor content) {
    try {
      return JsonLDObject.fromJson(content.getContentAsString());
    } catch (Exception ex) {
      log.warn("parseContent.syntactic error: {}", ex.getMessage(), ex);
      throw new ClientException("Syntactic error: " + ex.getMessage(), ex);
    }
  }

  private TypedCredentials verifyPresentation(VerifiablePresentation presentation,
                                              CredentialFormat format) {
    log.debug("verifyPresentation.enter; got presentation with id: {}", presentation.getId());
    StringBuilder sb = new StringBuilder();
    String sep = System.lineSeparator();
    if (checkAbsence(presentation, RDF_CONTEXT_KEY)) {
      sb.append(" - VerifiablePresentation must contain '@context' property").append(sep);
    }
    if (checkAbsence(presentation, "type", "@type")) {
      sb.append(" - VerifiablePresentation must contain 'type' property").append(sep);
    }
    if (checkAbsence(presentation, VERIFIABLE_CREDENTIAL_KEY)) {
      sb.append(" - VerifiablePresentation must contain 'verifiableCredential' property").append(sep);
    }
    TypedCredentials tcreds = getCredentials(presentation, format);
    Collection<VerifiableCredential> credentials = tcreds.getCredentials();
    int i = 0;
    for (VerifiableCredential credential : credentials) {
      if (credential != null) {
        sb.append(findVcErrors(credential, i));
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

  private String findVcErrors(VerifiableCredential credential, int idx) {
    StringBuilder sb = new StringBuilder();
    String sep = System.lineSeparator();
    if (checkAbsence(credential, RDF_CONTEXT_KEY)) {
      sb.append(" - VerifiableCredential[").append(idx).append("] must contain ")
          .append(RDF_CONTEXT_KEY).append(" property").append(sep);
    }
    if (checkAbsence(credential, "type", "@type")) {
      sb.append(" - VerifiableCredential[").append(idx).append("] must contain 'type' property")
          .append(sep);
    }
    if (checkAbsence(credential, "credentialSubject")) {
      sb.append(" - VerifiableCredential[").append(idx)
          .append("] must contain 'credentialSubject' property").append(sep);
    }
    if (checkAbsence(credential, "issuer")) {
      sb.append(" - VerifiableCredential[").append(idx).append("] must contain 'issuer' property")
          .append(sep);
    }
    requireVc2Context(credential);
    sb.append(Vc2DateValidation.validate(credential, idx));
    return sb.toString();
  }

  private static void requireVc2Context(VerifiableCredential credential) {
    Object ctx = credential.getJsonObject().get(RDF_CONTEXT_KEY);
    boolean isVc2 = (ctx instanceof List<?>)
        ? ((List<?>) ctx).contains(VC_20_CONTEXT)
        : VC_20_CONTEXT.equals(ctx);
    if (!isVc2) {
      throw new ClientException("Credential does not contain recognized VC 2.0 context");
    }
  }

  private boolean checkAbsence(JsonLDObject container, String... keys) {
    for (String key : keys) {
      if (container.getJsonObject().containsKey(key)) {
        return false;
      }
    }
    return true;
  }

  private TypedCredentials getCredentials(VerifiablePresentation vp, CredentialFormat format) {
    log.trace("getCredentials.enter; got VP: {}", vp);
    Object obj = vp.getJsonObject().get(VERIFIABLE_CREDENTIAL_KEY);
    Map<VerifiableCredential, ResolvedRole> creds;
    switch (obj) {
      case null -> creds = Collections.emptyMap();
      case List<?> l -> {
        creds = new LinkedHashMap<>(l.size());
        for (Object entry : l) {
          if (entry instanceof String jwtStr) {
            VerifiableCredential unwrapped =
                envelopedCredentialResolver.tryUnwrapJwtVc(jwtStr, vp.getDocumentLoader());
            if (unwrapped != null) {
              creds.put(unwrapped, resolveRole(unwrapped, format));
            }
            continue;
          }
          @SuppressWarnings("unchecked")
          Map<String, Object> entryMap = (Map<String, Object>) entry;
          if (EnvelopedCredentialResolver.isEnvelopedVerifiableCredential(
              EnvelopedCredentialResolver.resolveType(entryMap),
              entryMap.get(RDF_CONTEXT_KEY))) {
            VerifiableCredential unwrapped =
                envelopedCredentialResolver.tryUnwrapEnvelopedVc(entryMap, vp.getDocumentLoader());
            if (unwrapped != null) {
              creds.put(unwrapped, resolveRole(unwrapped, format));
            }
            continue;
          }
          VerifiableCredential vc = VerifiableCredential.fromMap(entryMap);
          vc.setDocumentLoader(vp.getDocumentLoader());
          creds.put(vc, resolveRole(vc, format));
        }
      }
      case String jwtStr -> {
        VerifiableCredential unwrapped =
            envelopedCredentialResolver.tryUnwrapJwtVc(jwtStr, vp.getDocumentLoader());
        creds = unwrapped != null
            ? Map.of(unwrapped, resolveRole(unwrapped, format))
            : Collections.emptyMap();
      }
      default -> {
        @SuppressWarnings("unchecked")
        VerifiableCredential vc = VerifiableCredential.fromMap((Map<String, Object>) obj);
        vc.setDocumentLoader(vp.getDocumentLoader());
        creds = Map.of(vc, resolveRole(vc, format));
      }
    }
    TypedCredentials tcs = new TypedCredentials(vp, creds);
    log.trace("getCredentials.exit; returning: {}", tcs);
    return tcs;
  }

  private TypedCredentials getCredentials(VerifiableCredential vc, CredentialFormat format) {
    log.trace("getCredentials.enter; got VC: {}", vc);
    TypedCredentials tcs = new TypedCredentials(null, Map.of(vc, resolveRole(vc, format)));
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

  /**
   * Resolves the trust-framework role of a credential. Uses the registry index as the fast path;
   * falls back to the composite schema ontology for subclasses introduced via dynamically
   * uploaded schemas.
   *
   * <p>The OWL composite ontology is only consulted when the {@code OWL} schema module is
   * administratively enabled (admin toggle on the schema-validation page). When OWL is
   * disabled, this method passes {@code null} to {@link ClaimValidator#resolveSubjectRole}:
   * the registry-index fast path still runs (so framework-direct types like Participant,
   * ServiceOffering, Resource keep resolving), but the slow {@code rdfs:subClassOf+} walk
   * over uploaded ontologies is skipped — custom subclass-only types resolve to
   * {@link ResolvedRole#UNKNOWN}.</p>
   *
   * <p>The toggle does not ride on the caller's {@code verifySchema} or
   * {@code verifySemantics} flags: type dispatch is a configuration of the catalogue's
   * type system, not a per-request validation step.</p>
   */
  private ResolvedRole resolveRole(VerifiableCredential credential, CredentialFormat format) {
    ContentAccessor compositeOntology =
        schemaModuleConfigService.isModuleEnabled(SchemaModuleType.OWL)
            ? schemaStore.getCompositeSchema(SchemaStore.SchemaType.ONTOLOGY)
            : null;
    ResolvedRole result = ClaimValidator.resolveSubjectRole(
        getStreamManager(), credential.toJson(), trustFrameworkRegistry, compositeOntology);
    log.debug("resolveRole; format: {}, got role: {}", format, result);
    return result;
  }

  /**
   * Rejects non-JWT credentials that request signature verification. Linked Data proof
   * verification (JsonWebSignature2020) is not supported; callers must use JWT or the
   * W3C VC 2.0 Enveloped Credential format.
   */
  private List<Validator> rejectLdProofVerification() {
    throw new VerificationException(
        "Signatures error; Linked Data proof verification is not supported."
            + " Use JWT or Enveloped Credential format.");
  }

  /**
   * Returns true if the JWT claims represent a Verifiable Presentation. Detects both the
   * VC-JOSE-COSE / VCDM 2.0 layout (top-level {@code type} claim) and the legacy VC-JWT
   * layout ({@code vp} wrapper claim).
   */
  private boolean isVpJwtClaims(JWTClaimsSet claims) {
    Object typeObj = claims.getClaim("type");
    if (typeObj instanceof List<?> types && types.contains(VP_TYPE)) {
      return true;
    }
    if (typeObj instanceof String typeStr && VP_TYPE.equals(typeStr)) {
      return true;
    }
    return claims.getClaim("vp") != null;
  }

  /**
   * Checks that the VP JWT {@code iss} matches the VP {@code holder} (ADR-3 holder binding).
   * Called only when {@code verifySemantics=true} and the outer JWT was successfully verified.
   *
   * @param claims already-parsed JWT claims (avoids re-parsing the JWT body)
   */
  private void checkVpJwtHolder(JWTClaimsSet claims) {
    if (!isVpJwtClaims(claims)) {
      return;
    }
    try {
      // VC-JOSE-COSE / VCDM 2.0: holder is a top-level claim; legacy VC-JWT: nested in vp claim
      String holder = claims.getStringClaim("holder");
      if (holder == null) {
        @SuppressWarnings("unchecked") // use of raw Map is unavoidable due to the flexible VP claim structure
        Map<String, Object> vpClaim = (Map<String, Object>) claims.getClaim("vp");
        if (vpClaim != null) {
          holder = (String) vpClaim.get("holder");
        }
      }
      String iss = claims.getIssuer();
      // holder is optional per VCDM 2.0; when absent, the VP issuer (iss) is implicitly the
      // holder. Also tolerate a top-level "issuer" claim alongside iss.
      if (holder == null) {
        String issuerClaim = claims.getStringClaim("issuer");
        if (issuerClaim != null && iss != null && !iss.equals(issuerClaim)) {
          throw new VerificationException(
              "VP JWT iss '" + iss + "' does not match VP issuer '" + issuerClaim + "'");
        }
        log.debug("checkVpJwtHolder; holder absent, iss '{}' accepted as implicit holder", iss);
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
    } catch (ParseException ex) {
      log.warn("checkVpJwtHolder; unexpected claims parse error: {}", ex.getMessage(), ex);
    }
  }
}
