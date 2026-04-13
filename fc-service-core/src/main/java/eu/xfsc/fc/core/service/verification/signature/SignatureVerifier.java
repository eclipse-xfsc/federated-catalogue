package eu.xfsc.fc.core.service.verification.signature;


import com.danubetech.dataintegrity.DataIntegrityProof;
import com.danubetech.keyformats.jose.JWK;

import eu.xfsc.fc.core.pojo.Validator;
import foundation.identity.jsonld.JsonLDObject;

public interface SignatureVerifier {

	  Validator checkSignature(JsonLDObject payload, DataIntegrityProof proof);
	  boolean verify(JsonLDObject payload, DataIntegrityProof proof, JWK jwk, String alg);
	
}
