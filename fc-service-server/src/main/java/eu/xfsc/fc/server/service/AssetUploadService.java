package eu.xfsc.fc.server.service;

import static eu.xfsc.fc.core.util.HashUtils.calculateSha256AsHex;
import static eu.xfsc.fc.server.util.SessionUtils.checkParticipantAccess;
import static eu.xfsc.fc.server.util.SessionUtils.getSessionParticipantId;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.pojo.ContentAccessorBinary;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.assetstore.RdfDetector;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.verification.VerificationService;
import eu.xfsc.fc.core.exception.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service layer for asset uploads. "Asset" is the umbrella term for anything
 * stored in the catalogue. Two paths exist:
 * <ul>
 *   <li><b>RDF-Data - currently only credential data</b> (RDF) — verified via {@link VerificationService}, indexed in the graph store.</li>
 *   <li><b>Non-RDF Asset</b> ("unstructured" data) — stored as-is in the file store, no verification.</li>
 * </ul>
 *
 * @see eu.xfsc.fc.core.service.assetstore.RdfDetector
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetUploadService {

    private final VerificationService verificationService;
    private final AssetStore assetStorePublisher;
    private final RdfDetector rdfDetector;

    public AssetMetadata processUpload(byte[] content, String contentType, String originalFilename) {
        if (content == null || content.length == 0) {
            throw new ClientException("Upload content must not be empty");
        }

        if (rdfDetector.isRdf(contentType, content)) {
            return handleCredential(content, contentType);
        }
        return handleNonRdfAsset(content, contentType, originalFilename);
    }

    private AssetMetadata handleCredential(byte[] content, String contentType) {
        log.debug("handleCredential; detected RDF content, delegating to verification pipeline");
        String text = new String(content, StandardCharsets.UTF_8);
        ContentAccessorDirect contentAccessor = new ContentAccessorDirect(text);

        CredentialVerificationResult verificationResult = verificationService.verifyCredential(contentAccessor);

        AssetMetadata assetMetadata = new AssetMetadata(verificationResult.getId(),
                verificationResult.getIssuer(), verificationResult.getValidators(), contentAccessor);
        assetMetadata.setContentType(contentType);
        assetMetadata.setFileSize((long) content.length);

        checkParticipantAccess(assetMetadata.getIssuer());
        assetStorePublisher.storeCredential(assetMetadata, verificationResult);

        log.debug("handleCredential.exit; stored credential with hash: {}", assetMetadata.getAssetHash());
        return assetMetadata;
    }

    private AssetMetadata handleNonRdfAsset(byte[] content, String contentType, String originalFilename) {
        log.debug("handleNonRdfAsset; non-RDF asset, skipping verification");
        String assetHash = calculateSha256AsHex(content);
        String participantId = getSessionParticipantId();
        Instant now = Instant.now();

        ContentAccessorBinary contentAccessor = new ContentAccessorBinary(content);
        AssetMetadata assetMetadata = new AssetMetadata(assetHash, null, AssetStatus.ACTIVE,
                participantId, null, now, now, contentAccessor);
        assetMetadata.setContentType(contentType);
        assetMetadata.setFileSize((long) content.length);

        return assetStorePublisher.storeUnverified(assetMetadata, originalFilename);
    }
}