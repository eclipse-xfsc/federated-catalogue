package federatedcatalogue;

import static federatedcatalogue.SimulationHelper.getResourcePath;

import com.danubetech.keyformats.crypto.PrivateKeySigner;
import com.danubetech.keyformats.crypto.PublicKeyVerifier;
import com.danubetech.keyformats.crypto.impl.RSA_PS256_PrivateKeySigner;
import com.danubetech.keyformats.crypto.impl.RSA_PS256_PublicKeyVerifier;
import com.danubetech.verifiablecredentials.VerifiableCredential;
import com.danubetech.verifiablecredentials.VerifiablePresentation;
import foundation.identity.jsonld.JsonLDException;
import foundation.identity.jsonld.JsonLDObject;
import info.weboftrust.ldsignatures.LdProof;
import info.weboftrust.ldsignatures.jsonld.LDSecurityKeywords;
import info.weboftrust.ldsignatures.signer.JsonWebSignature2020LdSigner;
import info.weboftrust.ldsignatures.signer.LdSigner;
import info.weboftrust.ldsignatures.suites.JsonWebSignature2020SignatureSuite;
import info.weboftrust.ldsignatures.verifier.JsonWebSignature2020LdVerifier;
import info.weboftrust.ldsignatures.verifier.LdVerifier;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

public class SelfDescriptionSigner {
  private static final String PATH_TO_PRIVATE_KEY = getResourcePath() + "prk.ss.pem";
  private static final String PATH_TO_PUBLIC_KEY = getResourcePath() + "cert.ss.pem";

  public static String signSd(String sd){
    try {
      Security.addProvider(new BouncyCastleProvider());
      VerifiablePresentation vp = VerifiablePresentation.fromJson(sd);
      VerifiableCredential vc = vp.getVerifiableCredential();

      LdProof vc_proof = sign(vc);
      check(vc, vc_proof);
      vc.setJsonObjectKeyValue("proof", vc.getLdProof().getJsonObject());
      vp.setJsonObjectKeyValue("verifiableCredential", vc.getJsonObject());
      LdProof vp_proof = sign(vp);
      check(vp, vp_proof);
      vp.setJsonObjectKeyValue("proof", vp.getLdProof().getJsonObject());
      return vp.toString();
    } catch (JsonLDException | GeneralSecurityException | IOException exception) {
      System.out.println("Cannot sign SD: " + exception);
    }
    return null;
  }

  public static LdProof sign(JsonLDObject credential) throws JsonLDException, GeneralSecurityException, IOException {
    try {
      FileReader fileReader = new FileReader(PATH_TO_PRIVATE_KEY);

      PEMParser pemParser = new PEMParser(fileReader);
      JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
      PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(pemParser.readObject());

      PrivateKey prk = converter.getPrivateKey(privateKeyInfo);

      KeyPair kp = new KeyPair(null, prk);
      PrivateKeySigner<?> privateKeySigner = new RSA_PS256_PrivateKeySigner(kp);

      LdSigner<JsonWebSignature2020SignatureSuite> signer = new JsonWebSignature2020LdSigner(privateKeySigner);

      signer.setCreated(new Date());
      signer.setProofPurpose(LDSecurityKeywords.JSONLD_TERM_ASSERTIONMETHOD);
      signer.setVerificationMethod(URI.create("did:web:compliance.lab.gaia-x.eu"));

      return signer.sign(credential);
    } catch (FileNotFoundException exception) {
      System.out.println("Can't find " + PATH_TO_PRIVATE_KEY + " file in resources: " + exception);
      throw exception;
    } catch (PEMException exception) {
      System.out.println("Can't parse private key data: " + exception);
      throw exception;
    } catch (IOException | GeneralSecurityException | JsonLDException exception) {
      System.out.println("Can't parse sign sd: ");
      throw exception;
    }
  }

  public static void check(JsonLDObject credential, LdProof proof)
      throws IOException, GeneralSecurityException, JsonLDException {
    String certString = Files.readString(Path.of(PATH_TO_PUBLIC_KEY));
    ByteArrayInputStream certStream = new ByteArrayInputStream(certString.getBytes(StandardCharsets.UTF_8));
    CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
    List<X509Certificate> certs = (List<X509Certificate>) certFactory.generateCertificates(certStream);
    for (X509Certificate cert : certs) {
      PublicKey puk = cert.getPublicKey();
      PublicKeyVerifier<?> pkVerifier = new RSA_PS256_PublicKeyVerifier((RSAPublicKey) puk);
      LdVerifier<JsonWebSignature2020SignatureSuite> verifier = new JsonWebSignature2020LdVerifier(pkVerifier);
      verifier.verify(credential, proof);
    }
  }
}
