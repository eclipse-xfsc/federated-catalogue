package eu.xfsc.fc.core.service.provenance;

import eu.xfsc.fc.core.service.assetstore.AssetDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes provenance credentials when an asset is deleted.
 *
 * <p>Runs inside the same transaction as the asset deletion ({@code BEFORE_COMMIT}) so that
 * provenance cleanup is atomic with the asset row removal.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProvenanceCleanupListener {

  private final ProvenanceService provenanceService;

  /**
   * Deletes all provenance credentials for the deleted asset.
   *
   * <p>Runs at {@link TransactionPhase#BEFORE_COMMIT}, inside the same transaction as the
   * asset deletion. This guarantees atomicity: either both the asset row and its provenance
   * credentials are removed, or neither is. If this method throws, the exception propagates
   * to the caller and the outer transaction is rolled back — the asset row is preserved.</p>
   */
  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onAssetDeleted(AssetDeletedEvent event) {
    log.debug("onAssetDeleted; cleaning up provenance credentials for assetId={}", event.assetId());
    provenanceService.deleteByAssetId(event.assetId());
  }
}
