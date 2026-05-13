package eu.xfsc.fc.core.service.pubsub.ces;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.core.dao.cestracker.CesTrackerDao;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.verification.VerificationService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CesAssetProcessor {

    @Value("${subscriber.verify.semantics:true}")
    private boolean verifySemantics;
    @Value("${subscriber.verify.schema:true}")
    private boolean verifySchema;
    @Value("${subscriber.verify.vp-signature:true}")
    private boolean verifyVPSignature;
    @Value("${subscriber.verify.vc-signature:true}")
    private boolean verifyVCSignature;
    @Value("${subscriber.verify.integrity:true}")
    private boolean verifyIntegrity;

  protected final AssetStore assetStore;
  protected final VerificationService verificationService;
  protected final ObjectMapper jsonMapper;

  private final CesTrackerDao ctDao;
  private final DidDocumentResolver didResolver;
  private final HttpDocumentResolver httpResolver;
  private final TrustFrameworkRegistry trustFrameworkRegistry;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
	public int processCesEvent(CesTracking ctr, Map<String, Object> data) {
	    List<Map<String, Object>> subjects = (List<Map<String, Object>>) data.get("credentialSubject");
	    try {
	    	int cnt = 0;
		    for (Map<String, Object> subject: subjects) {
		    	String sub = jsonMapper.writeValueAsString(subject);
		    	CesSubject ceSub = jsonMapper.readValue(sub, CesSubject.class);
		    	ctr.setCredId(ceSub.getId());
              if (isProcessibleSubject(ceSub)) {
		    		if (processSubject(ceSub.getId(), ceSub.getGxIntegrity())) {
		    			cnt++;
		    		}
		    	}
		    }
		    ctr.setCredProcessed(cnt);
	    	ctr.setCredId(null);
	    } catch (JsonProcessingException ex) {
	    	ctr.setError(ex.getMessage());
	    }
		ctDao.insert(ctr);
		return ctr.getCredProcessed();
	}

  private boolean isProcessibleSubject(CesSubject ceSub) {
    if (ceSub == null || ceSub.getGxType() == null) {
      return false;
		}
		String gxType = ceSub.getGxType();
    if (!trustFrameworkRegistry.resolveRole(gxType).isResolved()) {
      log.debug("isProcessibleSubject; type '{}' not recognized by registry — subject skipped"
          + " (registry requires full URIs; CURIEs will not resolve)", gxType);
      return false;
		}
    return true;
	}
	
	private boolean processSubject(String subId, String subIntegrity) {
		log.debug("processSubject.enter; got subject id: {}, integrity: {}", subId, subIntegrity);
		URI subUri = URI.create(subId);
		String subContent = null;
		if ("https".equals(subUri.getScheme())) {
			subContent = httpResolver.resolveDocumentContent(subId);
		} else if ("did".equals(subUri.getScheme())) {
			subContent = didResolver.resolveDocumentContent(subId);
		} else {
			log.debug("processSubject; unknown scheme: {}", subUri.getScheme());
		}
		
		boolean processed = false;
		if (subContent != null) {
	    	if (verifyIntegrity) {
	    		String[] parts = getIntegrityHash(subIntegrity);
	    		verifySubjectIntegrity(subContent, parts[0], parts[1]);
	    	}
	    	ContentAccessor payload = new ContentAccessorDirect(subContent);
	    	CredentialVerificationResult vr = verificationService.verifyCredential(payload, verifySemantics, verifySchema, verifyVPSignature, verifyVCSignature);
	    	AssetMetadata assetMeta = new AssetMetadata(vr.getId(), vr.getIssuer(), vr.getValidators(), payload);
	    	assetStore.storeCredential(assetMeta, vr);
	    	processed = true;
		}
		return processed;
	}
	
	private String[] getIntegrityHash(String subIntegrity) {
		String[] parts = subIntegrity.split("-");
		if (parts.length == 2) {
			if ("sha256".equals(parts[0])) {
				parts[0] = "SHA-256";
			}
			// process other prefixes, if any..
			return parts;
		}
		return new String[] {"SHA-256", subIntegrity};
	}
	
	private void verifySubjectIntegrity(String json, String algo, String hash) {
		String text;
		try {
			JsonCanonicalizer jc = new JsonCanonicalizer(json);
			text = jc.getEncodedString();
		} catch (IOException ex) {
			log.info("verifySubjectIntegrity.error: {}", ex.getMessage());
			throw new VerificationException(ex);
		}

		byte[] textHash;
		try {
			MessageDigest md = MessageDigest.getInstance(algo);
			md.update(text.getBytes()); //StandardCharsets.UTF_8));
			textHash = md.digest();
		} catch (NoSuchAlgorithmException ex) {
			log.info("verifySubjectIntegrity.error: {}", ex.getMessage());
			throw new VerificationException(ex);
		}
		// could use this one instead, but for SHA-256 only..
		//String digest = HashUtils.calculateSha256AsHex(text);

		BigInteger bi = new BigInteger(1, textHash);
	    String digest = String.format("%0" + (textHash.length << 1) + "x", bi);
		log.debug("verifySubjectIntegrity; got digest: {}, but hash is: {}", digest, hash);
		if (!digest.equals(hash)) { 
			throw new VerificationException("integrity check failed");
		}
	}
	
}
