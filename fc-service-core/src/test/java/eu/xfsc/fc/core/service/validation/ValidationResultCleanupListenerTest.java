package eu.xfsc.fc.core.service.validation;

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
class ValidationResultCleanupListenerTest {

  @Mock
  private ValidationResultStore validationResultStore;

  @InjectMocks
  private ValidationResultCleanupListener listener;

  @Test
  void onAssetDeleted_validEvent_callsDeleteByAssetId() {
    AssetDeletedEvent event = new AssetDeletedEvent("https://example.org/asset/deleted-1");

    listener.onAssetDeleted(event);

    verify(validationResultStore).deleteByAssetId("https://example.org/asset/deleted-1");
  }

  @Test
  void onAssetDeleted_storeThrows_propagatesException() {
    AssetDeletedEvent event = new AssetDeletedEvent("https://example.org/asset/deleted-2");
    doThrow(new RuntimeException("db unavailable"))
        .when(validationResultStore).deleteByAssetId("https://example.org/asset/deleted-2");

    assertThrows(RuntimeException.class, () -> listener.onAssetDeleted(event));
  }
}
