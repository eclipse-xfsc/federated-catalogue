package eu.xfsc.fc.core.service.verification.signature;

import static eu.xfsc.fc.core.util.DidUtils.resolveWebUri;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Autowired;

import com.danubetech.keyformats.crypto.PublicKeyVerifier;
import com.danubetech.keyformats.crypto.PublicKeyVerifierFactory;
import com.danubetech.keyformats.jose.JWK;

import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import foundation.identity.did.DIDDocument;
import foundation.identity.jsonld.JsonLDException;
import foundation.identity.jsonld.JsonLDObject;
import info.weboftrust.ldsignatures.LdProof;
import info.weboftrust.ldsignatures.verifier.JsonWebSignature2020LdVerifier;
import info.weboftrust.ldsignatures.verifier.LdVerifier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LocalSignatureVerifier implements SignatureVerifier {

    private static final Set<String> SIGNATURES = Set.of("JsonWebSignature2020"); 
	
	@Autowired
	private HttpDocumentResolver httpResolver;
    
    public LocalSignatureVerifier() {
      Security.addProvider(new BouncyCastleProvider());
    }

	@Override
	public Validator checkSignature(JsonLDObject payload, LdProof proof) {
	  try {
	    log.debug("checkSignature.enter; got payload, proof: {}", proof);
	    Validator validator = getVerifiedValidator(payload, proof);
		log.debug("checkSignature.exit; returning: {}", validator);
	    return validator;
	  } catch (IOException ex) {
	    throw new VerificationException(ex);
	  }
	}

	private Validator getVerifiedValidator(JsonLDObject payload, LdProof proof) throws IOException {
	  log.debug("getVerifiedVerifier.enter;");
	    
	  if (!SIGNATURES.contains(proof.getType())) {
	    throw new VerificationException("Signatures error; The proof type is not supported yet: " + proof.getType());
	  }

	  URI uri = proof.getVerificationMethod();
	  if (!uri.getScheme().equals("did")) {
	    throw new VerificationException("Signatures error; Unknown Verification Method: " + uri);
	  }

	  DIDDocument diDoc = getDIDocFromURI(uri);
	  Validator result = null;
      Map<String, Object> jwkMap = getRelevantKey(diDoc, uri.toString());
	  if (jwkMap == null) {
    	throw new VerificationException("Signatures error; no proper VerificationMethod found");
	  } 
        
	  JWK jwk = JWK.fromMap(jwkMap);
	  try {	
	    if (verify(payload, proof, jwk, jwk.getAlg())) { 
		  result = new Validator(uri.toString(), jwk.toJson(), null);
		}
	  } catch (Exception ex) {
	    log.info("getVerifiedVerifier.error: {}", ex.getMessage());
	  }
	  
	  if (result == null) {
        throw new VerificationException("Signatures error; " + payload.getClass().getSimpleName() + " does not match with proof");
	  }
	  log.debug("getVerifiedVerifier.exit; returning validator: {}", result);
	  return result;
	}
	  
	@Override
	public boolean verify(JsonLDObject payload, LdProof proof, JWK jwk, String alg) {
      log.debug("verify; got jwk: {}, alg: {}", jwk, alg);
	  PublicKeyVerifier<?> pkVerifier = PublicKeyVerifierFactory.publicKeyVerifierForKey(jwk, alg);
	  LdVerifier<?> verifier = new JsonWebSignature2020LdVerifier(pkVerifier);
	  try {
		return verifier.verify(payload);
	  } catch (IOException | GeneralSecurityException | JsonLDException ex) {
		log.info("verify.error: {}", ex.getMessage());
	  }
	  return false;
	}

	private DIDDocument getDIDocFromURI(URI uri) throws IOException { 
	  log.debug("readDIDFromURI.enter; got uri: {}", uri);
	  DIDDocument diDoc;
	  URI  docUri = resolveWebUri(uri);
	  if (docUri == null) {
	    throw new IOException("Couldn't load key. Method not supported");
	  }
	  diDoc = loadDIDocFromURI(docUri);
	  log.debug("readDIDFromURI.exit; returning: {}", diDoc);
	  return diDoc;
	}
	  
	private DIDDocument loadDIDocFromURI(URI docUri) throws IOException {
	  log.debug("loadDIDFromURL; loading DIDDocument from: {}", docUri.toString());
	  return httpResolver.resolveDidDocument(docUri.toString());
	}
	  
	@SuppressWarnings("unchecked")
	private Map<String, Object> getRelevantKey(DIDDocument diDoc, String verificationMethodURI) {
	  // better to get methods from doc using DIDDocument API...
	  List<Map<String, Object>> methods = (List<Map<String, Object>>) diDoc.toMap().get("verificationMethod");
	  log.debug("getRelevantJWK; methods: {}", methods);
	  for (Map<String, Object> method: methods) {
	    String id = (String) method.get("id");
	    if (verificationMethodURI.equals(id)) {
	      // publicKeyMultibase can be used also..  
	      return (Map<String, Object>) method.get("publicKeyJwk");
	    }
	  }
	  return null;
	}

}
