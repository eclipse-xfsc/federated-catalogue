package eu.xfsc.fc.core.service.verification.signature;


import com.danubetech.keyformats.jose.JWK;

import eu.xfsc.fc.core.pojo.Validator;
import foundation.identity.jsonld.JsonLDObject;
import info.weboftrust.ldsignatures.LdProof;

public interface SignatureVerifier {

	  Validator checkSignature(JsonLDObject payload, LdProof proof);
	  boolean verify(JsonLDObject payload, LdProof proof, JWK jwk, String alg);
	
}
