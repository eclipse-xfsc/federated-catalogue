package eu.xfsc.fc.core.service.provenance;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import eu.xfsc.fc.core.service.assetstore.AssetDeletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvenanceCleanupListenerTest {

  @Mock
  private ProvenanceService provenanceService;

  @InjectMocks
  private ProvenanceCleanupListener listener;

  @Test
  void onAssetDeleted_validEvent_callsDeleteByAssetId() {
    AssetDeletedEvent event = new AssetDeletedEvent("https://example.org/asset/deleted-1");

    listener.onAssetDeleted(event);

    verify(provenanceService).deleteByAssetId("https://example.org/asset/deleted-1");
  }

  @Test
  void onAssetDeleted_serviceThrows_propagatesException() {
    AssetDeletedEvent event = new AssetDeletedEvent("https://example.org/asset/deleted-2");
    doThrow(new RuntimeException("db unavailable"))
        .when(provenanceService).deleteByAssetId("https://example.org/asset/deleted-2");

    assertThrows(RuntimeException.class, () -> listener.onAssetDeleted(event));
  }
}
