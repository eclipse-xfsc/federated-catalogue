package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.service.verification.TrustFrameworkBaseClass.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.riot.system.stream.StreamManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.SchemeRouter;
import com.danubetech.keyformats.jose.JWK;
import com.danubetech.verifiablecredentials.CredentialSubject;
import com.danubetech.verifiablecredentials.VerifiableCredential;
import com.danubetech.verifiablecredentials.VerifiablePresentation;
import com.danubetech.verifiablecredentials.jsonld.VerifiableCredentialKeywords;

import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.core.dao.ValidatorCacheDao;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.pojo.SemanticValidationResult;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.pojo.VerificationResultOffering;
import eu.xfsc.fc.core.pojo.VerificationResultParticipant;
import eu.xfsc.fc.core.pojo.VerificationResultResource;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.verification.cache.CachingLocator;
import eu.xfsc.fc.core.service.verification.claims.ClaimExtractor;
import eu.xfsc.fc.core.service.verification.claims.DanubeTechClaimExtractor;
import eu.xfsc.fc.core.service.verification.claims.TitaniumClaimExtractor;
import eu.xfsc.fc.core.service.verification.signature.SignatureVerifier;
import eu.xfsc.fc.core.util.ClaimValidator;
import foundation.identity.jsonld.JsonLDObject;
import info.weboftrust.ldsignatures.LdProof;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;


/**
 * Implementation of the {@link VerificationService} interface.
 */
@Slf4j
@Component
public class VerificationServiceImpl implements VerificationService {

  private static final ClaimExtractor[] extractors = new ClaimExtractor[]{new TitaniumClaimExtractor(), new DanubeTechClaimExtractor()};

  @Value("${federated-catalogue.verification.require-vp:true}")
  private boolean requireVP;
  @Value("${federated-catalogue.verification.semantics:true}")
  private boolean verifySemantics;
  @Value("${federated-catalogue.verification.schema:true}")
  private boolean verifySchema;
  @Value("${federated-catalogue.verification.vp-signature:true}")
  private boolean verifyVPSignature;
  @Value("${federated-catalogue.verification.vc-signature:true}")
  private boolean verifyVCSignature;
  @Value("${federated-catalogue.verification.drop-validarors:false}")
  private boolean dropValidators;

  @Value("${federated-catalogue.verification.participant.type}")
  private String participantType; 
  @Value("${federated-catalogue.verification.service-offering.type}")
  private String serviceOfferingType; 
  @Value("${federated-catalogue.verification.resource.type}")
  private String resourceType;

  private Map<TrustFrameworkBaseClass, String> trustFrameworkBaseClassUris;

  @Autowired
  private SchemaStore schemaStore;
  @Autowired
  private SignatureVerifier signVerifier;

  @Autowired
  @Qualifier("contextCacheFileStore")
  private FileStore fileStore;

  @Autowired
  private DocumentLoader documentLoader;
  @Autowired
  private ValidatorCacheDao validatorCache;

  @Value("${federated-catalogue.verification.trust-anchor-url}")
  private String trustAnchorAddr;
  @Value("${federated-catalogue.verification.http-timeout:5000}") 
  private int httpTimeout;
  @Value("${federated-catalogue.verification.validator-expire:1D}")
  private Duration validatorExpiration;

  private static final int HTTP_TIMEOUT = 5*1000; 

  private RestTemplate rest;
  private boolean loadersInitialised;
  private StreamManager streamManager;

  public VerificationServiceImpl() {
    rest = restTemplate();
  }

  private RestTemplate restTemplate() {
    HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
    factory.setConnectTimeout(HTTP_TIMEOUT);
    factory.setConnectionRequestTimeout(HTTP_TIMEOUT);
    return new RestTemplate(factory); 
  }
  
  @PostConstruct
  private void initializeTrustFrameworkBaseClasses() {
    trustFrameworkBaseClassUris = new HashMap<>();
    trustFrameworkBaseClassUris.put(SERVICE_OFFERING, serviceOfferingType);
    trustFrameworkBaseClassUris.put(RESOURCE, resourceType);
    trustFrameworkBaseClassUris.put(PARTICIPANT, participantType);
  }
  
  /**
   * The function validates the Self-Description as JSON and tries to parse the json handed over.
   *
   * @param payload ContentAccessor to SD which should be syntactically validated.
   * @return a Participant metadata validation result. If the validation fails, the reason explains the issue.
   */
  @Override
  public VerificationResultParticipant verifyParticipantSelfDescription(ContentAccessor payload) throws VerificationException {
    return (VerificationResultParticipant) verifySelfDescription(payload, true, PARTICIPANT, verifySemantics, verifySchema, verifyVPSignature, verifyVCSignature);
  }

  /**
   * The function validates the Self-Description as JSON and tries to parse the json handed over.
   *
   * @param payload ContentAccessor to SD which should be syntactically validated.
   * @return a Verification result. If the verification fails, the reason explains the issue.
   */
  @Override
  public VerificationResultOffering verifyOfferingSelfDescription(ContentAccessor payload) throws VerificationException {
    return (VerificationResultOffering) verifySelfDescription(payload, true, SERVICE_OFFERING, verifySemantics, verifySchema, verifyVPSignature, verifyVCSignature);
  }
  
  /**
   * The function validates the Self-Description as JSON and tries to parse the json handed over.
   *
   * @param payload ContentAccessor to SD which should be syntactically validated.
   * @return a Verification result. If the verification fails, the reason explains the issue.
   */
  @Override
  public VerificationResultResource verifyResourceSelfDescription(ContentAccessor payload) throws VerificationException {
	return (VerificationResultResource) verifySelfDescription(payload, true, RESOURCE, verifySemantics, verifySchema, verifyVPSignature, verifyVCSignature);
  }
  
  /**
   * The function validates the Self-Description as JSON and tries to parse the json handed over.
   *
   * @param payload ContentAccessor to SD which should be syntactically validated.
   * @return a Self-Description metadata validation result. If the validation fails, the reason explains the issue.
   */
  @Override
  public VerificationResult verifySelfDescription(ContentAccessor payload) throws VerificationException {
    return verifySelfDescription(payload, verifySemantics, verifySchema, verifyVPSignature, verifyVCSignature);
  }

  @Override
  public VerificationResult verifySelfDescription(ContentAccessor payload, boolean verifySemantics, boolean verifySchema, 
		  boolean verifyVPSignatures, boolean verifyVCSignatures) throws VerificationException {
    return verifySelfDescription(payload, false, UNKNOWN, verifySemantics, verifySchema, verifyVPSignatures, verifyVCSignatures);
  }

  private VerificationResult verifySelfDescription(ContentAccessor payload, boolean strict, TrustFrameworkBaseClass expectedClass, boolean verifySemantics, 
		  boolean verifySchema, boolean verifyVPSignatures, boolean verifyVCSignatures) throws VerificationException {
    log.debug("verifySelfDescription.enter; strict: {}, expectedType: {}, verifySemantics: {}, verifySchema: {}, verifyVPSignatures: {}, verifyVCSignatures: {}",
            strict, expectedClass, verifySemantics, verifySchema, verifyVPSignatures, verifyVCSignatures);
    long stamp = System.currentTimeMillis();

    // syntactic validation
    JsonLDObject ld = parseContent(payload);
    log.debug("verifySelfDescription; content parsed, time taken: {}", System.currentTimeMillis() - stamp);

    // see https://gitlab.eclipse.org/eclipse/xfsc/cat/fc-service/-/issues/200
    // add GAIA-X context(s) if present
    ld.setDocumentLoader(this.documentLoader);
    
    // semantic verification
    long stamp2 = System.currentTimeMillis();
    TypedCredentials typedCredentials = parseCredentials(ld, strict && requireVP, verifySemantics);
    log.debug("verifySelfDescription; credentials processed, time taken: {}", System.currentTimeMillis() - stamp2);

    if (verifySemantics && !typedCredentials.hasClasses()) {
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
  	  partCount = classMap.getOrDefault(PARTICIPANT,  0);
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
   	    throw new VerificationException("Semantic error: SD has several types: " + baseClasses);
      }
      if (expectedClass != UNKNOWN && baseClass != expectedClass) {
        throw new VerificationException("Semantic error: expected SD of type " + expectedClass + " but found " + baseClass);
      }
    }

    stamp2 = System.currentTimeMillis();
    List<SdClaim> claims = extractClaims(payload);
    log.debug("verifySelfDescription; claims extracted: {}, time taken: {}", (claims == null ? "null" : claims.size()),
   		System.currentTimeMillis() - stamp2);

    if (verifySemantics && strict) {
      Set<String> subjects = new HashSet<>();
      Set<String> objects = new HashSet<>();
      if (claims != null && !claims.isEmpty()) {
        for (SdClaim claim : claims) {
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
      SemanticValidationResult result = verifyClaimsAgainstCompositeSchema(claims);
      if (result == null || !result.isConforming()) {
        throw new VerificationException("Schema error: " + (result == null ? "unknown" : result.getValidationReport()));
      }
    }

    // signature verification
    List<Validator> validators;
    if (verifyVPSignatures || verifyVCSignatures) {
      validators = checkCryptography(typedCredentials, verifyVPSignatures, verifyVCSignatures);
    } else {
      validators = null; //is it ok?
    }

    String id = typedCredentials.getID();
    String issuer = typedCredentials.getIssuer();
    Instant issuedDate = typedCredentials.getIssuanceDate();

    VerificationResult result;
    if (baseClass == PARTICIPANT) {
      if (issuer == null) {
        issuer = id;
      }
      String method = typedCredentials.getProofMethod();
      String holder = typedCredentials.getHolder();
      String name = holder == null ? issuer : holder;
      result = new VerificationResultParticipant(Instant.now(), SelfDescriptionStatus.ACTIVE.getValue(), issuer, issuedDate,
              claims, validators, name, method);
    } else if (baseClass == SERVICE_OFFERING) {
      result = new VerificationResultOffering(Instant.now(), SelfDescriptionStatus.ACTIVE.getValue(), issuer, issuedDate,
              id, claims, validators);
    } else if (baseClass == RESOURCE) {
      result = new VerificationResultResource(Instant.now(), SelfDescriptionStatus.ACTIVE.getValue(), issuer, issuedDate,
              id, claims, validators);
    } else {
      result = new VerificationResult(Instant.now(), SelfDescriptionStatus.ACTIVE.getValue(), issuer, issuedDate,
              id, claims, validators);
    }

    stamp = System.currentTimeMillis() - stamp;
    log.debug("verifySelfDescription.exit; returning: {}; time taken: {}", result, stamp);
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
      throw new VerificationException("Semantic error: expected SD of type 'VerifiablePresentation', actual SD type: " + ld.getTypes());
    }
    if (ld.isType(VerifiableCredentialKeywords.JSONLD_TERM_VERIFIABLE_CREDENTIAL)) {
      VerifiableCredential vc = VerifiableCredential.fromJsonLDObject(ld);
      if (verifySemantics) {
        String err = verifyCredential(vc, 0);
        if (err != null && err.length() > 0) {
          throw new VerificationException("Semantic error: " + err);
        }
      }
      return getCredentials(vc);
    } 
    throw new VerificationException("Semantic error: unexpected SD type: " + ld.getTypes());
  }

  /* SD parsing, semantic validation */
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
    for (VerifiableCredential credential: credentials) {
      if (credential != null) {
    	sb.append(verifyCredential(credential, i));  
      }
      i++;
    }

    if (sb.length() > 0) {
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
    if (checkAbsence(credential, "issuanceDate")) {
      sb.append(" - VerifiableCredential[").append(idx).append("] must contain 'issuanceDate' property").append(sep);
    }

    Date today = Date.from(Instant.now());
    Date issDate = credential.getIssuanceDate();
    if (issDate != null && issDate.after(today)) {
      sb.append(" - 'issuanceDate' of VerifiableCredential[").append(idx).append("] must be in the past").append(sep);
    }
    Date expDate = credential.getExpirationDate();
    if (expDate != null && expDate.before(today)) {
      sb.append(" - 'expirationDate' of VerifiableCredential[").append(idx).append("] must be in the future").append(sep);
    }
    return sb.toString();
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
    TypedCredentials tcs = new TypedCredentials(vp);
    log.trace("getCredentials.exit; returning: {}", tcs);
    return tcs;
  }

  private TypedCredentials getCredentials(VerifiableCredential vc) {
    log.trace("getCredentials.enter; got VC: {}", vc);
    TypedCredentials tcs = new TypedCredentials(vc);
    log.trace("getCredentials.exit; returning: {}", tcs);
    return tcs;
  }
  
  /**
   * A method that returns a list of claims given a self-description's VerifiablePresentation
   *
   * @param payload a self-description as Verifiable Presentation for claims extraction
   * @return a list of claims.
   */
  @Override
  public List<SdClaim> extractClaims(ContentAccessor payload) {
    // Make sure our interceptors are in place.
    initLoaders();
    List<SdClaim> claims = null;
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
      log.debug("initLoaders; Setting up SchemeRouter");
      SchemeRouter loader = (SchemeRouter) SchemeRouter.defaultInstance();
      loader.set("file", documentLoader);
      loader.set("http", documentLoader);
      loader.set("https", documentLoader);
      loadersInitialised = true;
    }
  }

  private StreamManager getStreamManager() {
    if (streamManager == null) {
      // Make sure Caching com.​apicatalog.​jsonld DocumentLoader is set up.
      initLoaders();
      log.debug("getStreamManager; Setting up Jena caching Locator");
      StreamManager clone = StreamManager.get().clone();
      clone.clearLocators();
      clone.addLocator(new CachingLocator(fileStore));
      streamManager = clone;
    }
    return streamManager;
  }

  /**
   * Override URI set for one of the Trust Framework base classes.
   * @param baseClass The base class for which the URI is to be overwritten
   * @param uri New URI
   */
  public void setBaseClassUri(TrustFrameworkBaseClass baseClass, String uri) {
    trustFrameworkBaseClassUris.put(baseClass, uri);
  }
  

  /* SD validation against SHACL Schemas */
  @Override
  public SemanticValidationResult verifySelfDescriptionAgainstCompositeSchema(ContentAccessor payload) {
    return verifySelfDescriptionAgainstSchema(payload, null);
  }

  @Override
  public SemanticValidationResult verifySelfDescriptionAgainstSchema(ContentAccessor payload, ContentAccessor schema) {
    log.debug("verifySelfDescriptionAgainstSchema.enter;");
    SemanticValidationResult result = null;
    try {
      if (schema == null) { 	
        schema = schemaStore.getCompositeSchema(SchemaStore.SchemaType.SHAPE);
      }
      List<SdClaim> claims = extractClaims(payload);
      result = verifyClaimsAgainstSchema(claims, schema);
    } catch (Exception exc) {
      log.info("verifySelfDescriptionAgainstSchema.error: {}", exc.getMessage());
    }
    log.debug("verifySelfDescriptionAgainstSchema.exit; conforms: {}", result.isConforming());
    return result;
  }
  
  private SemanticValidationResult verifyClaimsAgainstCompositeSchema(List<SdClaim> claims) {
	log.debug("verifyClaimsAgainstCompositeSchema.enter;");
	SemanticValidationResult result = null;
	try {
	  ContentAccessor shaclShape = schemaStore.getCompositeSchema(SchemaStore.SchemaType.SHAPE);
	  result = verifyClaimsAgainstSchema(claims, shaclShape);
	} catch (Exception exc) {
	  log.info("verifyClaimsAgainstCompositeSchema.error: {}", exc.getMessage());
	}
	log.debug("verifyClaimsAgainstCompositeSchema.exit; conforms: {}", result.isConforming());
	return result;
  }

  /**
   * Method that validates a dataGraph against shaclShape
   *
   * @param payload    ContentAccessor of a self-Description payload to be validated
   * @param shaclShape ContentAccessor of a union schemas of type SHACL
   * @return SemanticValidationResult object
   */
  private SemanticValidationResult verifyClaimsAgainstSchema(List<SdClaim> claims, ContentAccessor schema) {
	String report = ClaimValidator.validateClaimsBySchema(claims, schema, getStreamManager());
	return new SemanticValidationResult(report == null, report);
  }

  
  /* SD signatures verification */
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
      throw new VerificationException("Signatures error; no trust anchor url found");
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
    log.debug("hasPEMTrustAnchorAndIsNotExpired.enter; got uri: {}", uri);
    String pem = rest.getForObject(uri, String.class);
    InputStream certStream = new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8));
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
    for (X509Certificate cert: certs) {
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

    try {
      ResponseEntity<Map> resp = rest.postForEntity(trustAnchorAddr, Map.of("uri", uri), Map.class);
      if (!resp.getStatusCode().is2xxSuccessful()) {
        log.info("hasPEMTrustAnchorAndIsNotExpired; Trust anchor is not set in the registry. URI: {}", uri);
      }
    } catch (Exception ex) {
      log.warn("hasPEMTrustAnchorAndIsNotExpired; trust anchor error: {}", ex.getMessage());
      throw new VerificationException("Signatures error; " + ex.getMessage());
    }
    Instant exp = relevant == null ? null : relevant.getNotAfter().toInstant();
    log.debug("hasPEMTrustAnchorAndIsNotExpired.exit; returning: {}", exp);
    return exp;
  }
  
  
  private class TypedCredentials {

    private VerifiablePresentation presentation;
    private Map<VerifiableCredential, TrustFrameworkBaseClass> credentials;

    TypedCredentials(VerifiablePresentation presentation) {
      this.presentation = presentation;
      initCredentials();
    }

    TypedCredentials(VerifiableCredential credential) {
      this.presentation = null;
      Map<VerifiableCredential, TrustFrameworkBaseClass> creds;
      TrustFrameworkBaseClass bc = getSDBaseClass(credential);
      creds = Map.of(credential, bc);
      this.credentials = creds;
    }

    @SuppressWarnings("unchecked")
    private void initCredentials() {
      Object obj = presentation.getJsonObject().get("verifiableCredential");
      Map<VerifiableCredential, TrustFrameworkBaseClass> creds;
      if (obj == null) {
        creds = Collections.emptyMap();
      } else if (obj instanceof List) {
        List<Map<String, Object>> l = (List<Map<String, Object>>) obj;
        creds = new LinkedHashMap<>(l.size());
        for (Map<String, Object> _vc : l) {
          VerifiableCredential vc = VerifiableCredential.fromMap(_vc);
          vc.setDocumentLoader(this.presentation.getDocumentLoader());
          TrustFrameworkBaseClass bc = getSDBaseClass(vc);
          creds.put(vc, bc);
        }
      } else {
        VerifiableCredential vc = VerifiableCredential.fromMap((Map<String, Object>) obj);
        vc.setDocumentLoader(this.presentation.getDocumentLoader());
        TrustFrameworkBaseClass bc = getSDBaseClass(vc);
        creds = Map.of(vc, bc);
      }
      this.credentials = creds;
    }

    private VerifiableCredential getFirstVC() {
      return credentials.isEmpty() ? null : credentials.keySet().iterator().next();
    }

    Collection<TrustFrameworkBaseClass> getBaseClasses() {
      return credentials.values().stream().filter(bc -> bc != UNKNOWN).distinct().toList();
    }

    Collection<VerifiableCredential> getCredentials() {
      return credentials.keySet();
    }

    String getHolder() {
      if (presentation == null) {
    	return null;  
      }
      URI holder = presentation.getHolder();
      if (holder == null) {
        return null;
      }
      return holder.toString();
    }

    String getID() {
      VerifiableCredential first = getFirstVC();
      if (first == null) {
        return null;
      }

      List<CredentialSubject> subjects = getSubjects(first);
      if (subjects.isEmpty()) {
        return getID(first.getJsonObject());
      }
      return getID(subjects.get(0).getJsonObject());
    }

    String getIssuer() {
      VerifiableCredential first = getFirstVC();
      if (first == null) {
        return null;
      }
      URI issuer = first.getIssuer();
      if (issuer == null) {
        return null;
      }
      return issuer.toString();
    }

    Instant getIssuanceDate() {
      VerifiableCredential first = getFirstVC();
      if (first == null) {
        return null;
      }
      Date issDate = first.getIssuanceDate();
      if (issDate == null) {
        return Instant.now();
      }
      return issDate.toInstant();
    }

    VerifiablePresentation getPresentation() {
      return presentation;
    }

    String getProofMethod() {
      LdProof proof = null;	
      if (presentation == null) {
    	if (!credentials.isEmpty()) {
    	  proof = credentials.keySet().iterator().next().getLdProof();
    	}
      } else {
        proof = presentation.getLdProof();
      }
      URI method = proof == null ? null : proof.getVerificationMethod();
      return method == null ? null : method.toString();
    }

    boolean hasClasses() {
      return credentials.values().stream().anyMatch(bc -> bc != UNKNOWN);
    }

    private TrustFrameworkBaseClass getSDBaseClass(VerifiableCredential credential) {
      ContentAccessor gaxOntology = schemaStore.getCompositeSchema(SchemaStore.SchemaType.ONTOLOGY);
      TrustFrameworkBaseClass result = ClaimValidator.getSubjectType(gaxOntology, getStreamManager(), credential.toJson(), trustFrameworkBaseClassUris);
      if (result == null) {
    	  result = UNKNOWN;
      }
      log.debug("getSDBaseClass; got type result: {}", result);
      return result;
    }
    
    @SuppressWarnings("unchecked")
    private List<CredentialSubject> getSubjects(VerifiableCredential credential) {
      Object obj = credential.getJsonObject().get("credentialSubject");

      if (obj == null) {
        return Collections.emptyList();
      } else if (obj instanceof List) {
        List<Map<String, Object>> l = (List<Map<String, Object>>) obj;
        List<CredentialSubject> result = new ArrayList<>(l.size());
        for (Map<String, Object> _cs : l) {
          CredentialSubject cs = CredentialSubject.fromMap(_cs);
          result.add(cs);
        }
        return result;
      } else if (obj instanceof Map) {
        CredentialSubject vc = CredentialSubject.fromMap((Map<String, Object>) obj);
        return List.of(vc);
      } else {
        return Collections.emptyList();
      }
    }

    private String getID(Map<String, Object> map) {
      Object id = map.get("id");
      if (id != null) {
        return id.toString();
      }
      id = map.get("@id");
      if (id != null) {
        return id.toString();
      }
      return null;
    }

  }

}
