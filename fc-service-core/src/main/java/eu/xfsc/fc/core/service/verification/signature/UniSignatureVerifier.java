package eu.xfsc.fc.core.service.verification.signature;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.danubetech.keyformats.crypto.PublicKeyVerifier;
import com.danubetech.keyformats.crypto.PublicKeyVerifierFactory;
import com.danubetech.keyformats.jose.JWK;
import com.danubetech.keyformats.jose.JWSAlgorithm;
import com.danubetech.keyformats.jose.KeyTypeName;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;

import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import foundation.identity.did.DIDDocument;
import foundation.identity.did.VerificationMethod;
import foundation.identity.jsonld.JsonLDException;
import foundation.identity.jsonld.JsonLDObject;
import info.weboftrust.ldsignatures.LdProof;
import info.weboftrust.ldsignatures.verifier.LdVerifier;
import info.weboftrust.ldsignatures.verifier.LdVerifierRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UniSignatureVerifier implements SignatureVerifier {

	@Autowired
	private DidDocumentResolver didResolver;
	
	@Override
	public Validator checkSignature(JsonLDObject payload, LdProof proof) {
		String alg = getAlgFromProof(proof);
		String did = getDidFromProof(proof);
	    return verifyLDProof(payload, proof, did, alg);
	}
	
	private Validator verifyLDProof(JsonLDObject payload, LdProof proof, String did, String alg) {
		log.debug("verifyLDProof.enter; did: {}, alg: {}, payload: {}", did, alg, payload);
		DIDDocument diDoc = didResolver.resolveDidDocument(did);
		List<VerificationMethod> vrMethods = diDoc.getVerificationMethods();
		log.debug("verifyVCSignature; methods: {}; resolved proof: {}", vrMethods, proof);
		Optional<VerificationMethod> ovm = vrMethods.stream().filter(vm -> {
			log.debug("verifyVCSignature; veryfying with: {}", vm);
			try {
				JWK jwkPublic = JWK.fromMap(vm.getPublicKeyJwk());
				if (verify(payload, proof, jwkPublic, alg)) {
					return true;
				}
				log.debug("verifyLDProof; payload not verified; suite: {}", proof.getType());
			} catch (Throwable ex) {
				log.warn("verifyLDProof; error verifying signature", ex);
			}
			return false;
		}).findFirst();

		
		log.debug("verifyLDProof; verified by: {}", ovm);
		if (ovm.isEmpty()) {
			throw new VerificationException("Signatures error; " + payload.getClass().getSimpleName() + " does not match with proof");
		}
		
		try {
			JWK jwk = JWK.fromMap(ovm.get().getPublicKeyJwk());
			if (jwk.getAlg() == null) {
				jwk.setAlg(alg);
			}
			Validator result = new Validator(proof.getVerificationMethod().toString(), jwk.toJson(), null);
			log.debug("verifyLDProof.exit; returning: {}", result);
			return result;
		} catch (IOException ex) {
			// cannot be this..
			throw new VerificationException(ex);
		}
	}

	@Override
	public boolean verify(JsonLDObject payload, LdProof proof, JWK jwk, String alg) {
		log.debug("verify.enter; alg: {}, jwk: {}", alg, jwk);
		LdVerifier<?> verifier = LdVerifierRegistry.getLdVerifierBySignatureSuiteTerm(proof.getType());
		PublicKeyVerifier<?> pkVerifier = PublicKeyVerifierFactory.publicKeyVerifierForJWK(jwk, alg);
		verifier.setVerifier(pkVerifier);
	    try {
			return verifier.verify(payload);
		} catch (IOException | GeneralSecurityException | JsonLDException ex) {
			log.info("verify.error: {}", ex.getMessage());
		}
		return false;
	}
	
	private String getAlgFromProof(LdProof proof) {
		if (proof.getType().contains(KeyTypeName.Ed25519.getValue())) {
			return JWSAlgorithm.EdDSA;
		}
		if (proof.getType().startsWith("BbsBls")) {
			return JWSAlgorithm.BBSPlus;
		}
		if (proof.getType().startsWith("RSA")) {
			return JWSAlgorithm.RS256;
		}
		if (proof.getType().startsWith("EcdsaSecp256k")) {
			return JWSAlgorithm.ES256K;
		}
		if (proof.getType().startsWith("EcdsaKoblitz")) {
			return JWSAlgorithm.ES256K;
		}
		if (proof.getType().startsWith("JcsEcdsaSecp256k")) {
			return JWSAlgorithm.ES256K;
		}
		// else we got JsonWebSignature2020 which maps to:
		// Map.of(KeyTypeName.RSA, List.of(JWSAlgorithm.PS256, JWSAlgorithm.RS256),
		// KeyTypeName.Ed25519, List.of(JWSAlgorithm.EdDSA),
		// KeyTypeName.secp256k1, List.of(JWSAlgorithm.ES256K),
		// KeyTypeName.P_256, List.of(JWSAlgorithm.ES256),
		// KeyTypeName.P_384, List.of(JWSAlgorithm.ES384)),
		// so, will need more info on how to choose proper algo..
		if (proof.getJws() != null) {
			JWT jwt;
			try {
				jwt = JWTParser.parse(proof.getJws());
				return jwt.getHeader().getAlgorithm().getName();
			} catch (ParseException ex) {
				log.debug("getAlgFromProof; error parsing JWS: {}", ex.getMessage());
			}
		}
		return JWSAlgorithm.ES256K;
	}

	private String getDidFromProof(LdProof proof) {
		URI vmUri = proof.getVerificationMethod();
		String vmStr = vmUri.toString();
		int idx = vmStr.lastIndexOf('#');
		if (idx > 0) {
			vmStr = vmStr.substring(0, idx);
		}
		idx = vmStr.lastIndexOf('?');
		if (idx > 0) {
			vmStr = vmStr.substring(0, idx);
		}
		return vmStr;
	}
	
}
