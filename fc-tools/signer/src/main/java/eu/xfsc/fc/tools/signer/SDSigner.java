package eu.xfsc.fc.tools.signer;

import com.apicatalog.jsonld.JsonLdError;
import com.danubetech.keyformats.crypto.PrivateKeySigner;
import com.danubetech.keyformats.crypto.PrivateKeySignerFactory;
import com.danubetech.keyformats.crypto.PublicKeyVerifier;
import com.danubetech.keyformats.crypto.impl.RSA_PS256_PrivateKeySigner;
import com.danubetech.keyformats.crypto.impl.RSA_PS256_PublicKeyVerifier;
import com.danubetech.keyformats.jose.JWK;
import com.danubetech.verifiablecredentials.VerifiableCredential;
import com.danubetech.verifiablecredentials.VerifiablePresentation;
import com.danubetech.verifiablecredentials.jsonld.VerifiableCredentialKeywords;

import foundation.identity.jsonld.JsonLDException;
import foundation.identity.jsonld.JsonLDObject;
import info.weboftrust.ldsignatures.LdProof;
import info.weboftrust.ldsignatures.jsonld.LDSecurityKeywords;
import info.weboftrust.ldsignatures.signer.JsonWebSignature2020LdSigner;
import info.weboftrust.ldsignatures.signer.LdSigner;
import info.weboftrust.ldsignatures.suites.JsonWebSignature2020SignatureSuite;
import info.weboftrust.ldsignatures.verifier.JsonWebSignature2020LdVerifier;
import info.weboftrust.ldsignatures.verifier.LdVerifier;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class SDSigner {
    //openssl req -x509 -newkey rsa:4096 -keyout prk.ss.pem -out cert.ss.pem -sha256 -days 365 -nodes
    private static String PATH_TO_PRIVATE_KEY = "src/main/resources/prk.ss.pem";
    private static String PATH_TO_PUBLIC_KEY = null;
    private static String PATH_TO_SELF_DESCRIPTION = "src/main/resources/vc.json";
    private static String PATH_TO_SIGNED_SELF_DESCRIPTION = null; //"src/main/resources/sd.signed.json";
    private static String VMETHOD = "did:web:compliance.lab.gaia-x.eu";
    private static String ALGO = "EdDSA";
    

    static String readFile(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    public static void main(String[] args) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        System.out.println("args: " + java.util.Arrays.toString(args));
        
    	for (String arg: args) {
    		String[] parts = arg.split("=");
    		if ("a".equals(parts[0]) || "algo".equals(parts[0])) {
    			ALGO = parts[1];
    			continue;
    		}
    		if ("m".equals(parts[0]) || "vmethod".equals(parts[0])) {
    			VMETHOD = parts[1];
    			continue;
    		}
    		if ("puk".equals(parts[0]) || "public-key".equals(parts[0])) {
    			PATH_TO_PUBLIC_KEY = parts[1];
    			continue;
    		}
    		if ("prk".equals(parts[0]) || "private-key".equals(parts[0])) {
    			PATH_TO_PRIVATE_KEY = parts[1];
    			continue;
    		}
    		if ("sd".equals(parts[0]) || "self-description".equals(parts[0])) {
    			PATH_TO_SELF_DESCRIPTION = parts[1];
    			continue;
    		}
    		if ("ssd".equals(parts[0]) || "signed-description".equals(parts[0])) {
    			PATH_TO_SIGNED_SELF_DESCRIPTION = parts[1];
    			continue;
    		}
			System.out.println("unknown parameter: " + arg);
    	}
        
    	boolean check = PATH_TO_PUBLIC_KEY != null; 
        String json = readFile(PATH_TO_SELF_DESCRIPTION);
    	JsonLDObject ld = JsonLDObject.fromJson(json);
    	List<VerifiableCredential> vcs = new ArrayList<>();;
    	VerifiablePresentation vp = null;
        System.out.println("SD types: " + ld.getTypes()); 
    	
    	if (ld.isType(VerifiableCredentialKeywords.JSONLD_TERM_VERIFIABLE_PRESENTATION)) {
            vp = VerifiablePresentation.fromJsonLDObject(ld);
            Object vc = vp.getJsonObject().get(VerifiableCredentialKeywords.JSONLD_TERM_VERIFIABLECREDENTIAL);
            if (vc instanceof Map) {
            	vcs.add(VerifiableCredential.fromMap((Map<String, Object>) vc));
            } else if (vc instanceof List) {
                List<Map<String, Object>> l = (List<Map<String, Object>>) vc;
                for (Map<String, Object> m: l) {
                	vcs.add(VerifiableCredential.fromMap(m));
                }
            } else {
                System.out.println("Unknown VC type: " + vc);
                // but we still can sign VP
            }
        } else if (ld.isType(VerifiableCredentialKeywords.JSONLD_TERM_VERIFIABLE_CREDENTIAL)) {
        	vcs.add(VerifiableCredential.fromJsonLDObject(ld));
        } else {
            System.out.println("Unknown SD type: " + ld.getTypes()); 
            return;
        }
        
    	for (VerifiableCredential vc: vcs) {
    		System.out.println("Signing VC");
    		LdProof vc_proof = sign(vc);
    		System.out.println("VC Signed");
    		if (check) {
    			check(vc, vc_proof);
    			System.out.println("VC Signature verified");
    		}
    	}

        if (vp != null) {
	        System.out.println("Signing VP");
	        LdProof vp_proof = sign(vp);
	        System.out.println("VP Signed");
	        if (check) {
	            check(vp, vp_proof);
	            System.out.println("VP Signature verified");
	        }
        }
        
        if (PATH_TO_SIGNED_SELF_DESCRIPTION == null) {
        	int idx = PATH_TO_SELF_DESCRIPTION.lastIndexOf(".");
        	String ext = "";
        	if (idx > 0) {
        		PATH_TO_SIGNED_SELF_DESCRIPTION = PATH_TO_SELF_DESCRIPTION.substring(0, idx);
            	ext = PATH_TO_SELF_DESCRIPTION.substring(idx);
        	} else {
        		PATH_TO_SIGNED_SELF_DESCRIPTION = PATH_TO_SELF_DESCRIPTION;
        	}
    		PATH_TO_SIGNED_SELF_DESCRIPTION += ".signed" + ext;
        }
        Files.writeString(Path.of(PATH_TO_SIGNED_SELF_DESCRIPTION), ld.toJson(true));
    }

    public static LdProof sign(JsonLDObject credential) throws IOException, GeneralSecurityException, JsonLDException, URISyntaxException, JsonLdError {
    	String ext = PATH_TO_PRIVATE_KEY.substring(PATH_TO_PRIVATE_KEY.lastIndexOf(".") + 1);
    	FileReader reader = new FileReader(PATH_TO_PRIVATE_KEY);
        PrivateKeySigner<?> privateKeySigner;
        if ("pem".equals(ext)) {
        	try (PEMParser pemParser = new PEMParser(reader)) {
				Object inst = pemParser.readObject();
				PrivateKeyInfo privateKeyInfo;
				if (inst instanceof PEMKeyPair) {
					privateKeyInfo = ((PEMKeyPair) inst).getPrivateKeyInfo();
				} else {
					privateKeyInfo = PrivateKeyInfo.getInstance(inst);
				}
				JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
				PrivateKey prk = converter.getPrivateKey(privateKeyInfo);
				KeyPair kp = new KeyPair(null, prk);
				privateKeySigner = new RSA_PS256_PrivateKeySigner(kp);
			}
        } else if (ext.equals("json")) {
        	JWK jwk = JWK.fromJson(reader);
        	privateKeySigner = PrivateKeySignerFactory.privateKeySignerForKey(jwk, ALGO);
        } else {
        	throw new IllegalArgumentException("Unknown key format");
        }
        
		//ConfigurableDocumentLoader loader = (ConfigurableDocumentLoader) VerifiableCredentialContexts.DOCUMENT_LOADER;
		//loader.getLocalCache().put(new URI("https://schema.org"), JsonDocument.of(new StringReader("{\"@context\": {}}")));
        
        LdSigner<JsonWebSignature2020SignatureSuite> signer = new JsonWebSignature2020LdSigner(privateKeySigner);

        signer.setCreated(new Date());
        signer.setProofPurpose(LDSecurityKeywords.JSONLD_TERM_ASSERTIONMETHOD);
        signer.setVerificationMethod(URI.create(VMETHOD));

        LdProof ldProof = signer.sign(credential); 
        return ldProof;
    }

    public static void check(JsonLDObject credential, LdProof proof) throws IOException, GeneralSecurityException, JsonLDException {
        //---extract Expiration Date--- https://stackoverflow.com/a/11621488
        String certString = readFile(PATH_TO_PUBLIC_KEY);
        ByteArrayInputStream certStream  =  new ByteArrayInputStream(certString.getBytes(StandardCharsets.UTF_8));
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certs = (List<X509Certificate>) certFactory.generateCertificates(certStream);

        for(X509Certificate cert : certs) {
            PublicKey puk = cert.getPublicKey();
            PublicKeyVerifier<?> pkVerifier = new RSA_PS256_PublicKeyVerifier((RSAPublicKey) puk);
            LdVerifier<JsonWebSignature2020SignatureSuite> verifier = new JsonWebSignature2020LdVerifier(pkVerifier);
            boolean verified = verifier.verify(credential, proof);
            System.out.println("issuer: " + cert.getIssuerX500Principal() + "; verified: " + verified);
        }
    }
}
