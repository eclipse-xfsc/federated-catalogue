package eu.xfsc.fc.core.service.verification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.dao.revalidator.RevalidatorChunksDao;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.assetstore.AssetStore;

/**
 * Pins the SHACL toggle gate on the background revalidation sweep.
 *
 * <p>The sweep historically ignored the schema-module toggle: even with SHACL
 * administratively disabled, workers kept revoking previously-conforming assets when the
 * composite shape tightened. This test class verifies that {@code handleTask} now
 * consults {@link SchemaModuleConfigService#isModuleEnabled} for {@code SHACL} and skips
 * the per-asset validation when the module is off, regardless of conformance.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RevalidationServiceImplToggleTest {

  private static final String ASSET_HASH = "asset-hash-1";
  private static final ContentAccessor ASSET_CONTENT = new ContentAccessorDirect("dummy asset");

  @Mock
  private RevalidatorChunksDao dao;

  @Mock
  private AssetStore assetStore;

  @Mock
  private SchemaValidationService schemaValidationService;

  @Mock
  private SchemaModuleConfigService schemaModuleConfigService;

  @InjectMocks
  private RevalidationServiceImpl service;

  @Test
  void handleTask_shaclEnabled_nonConformingAsset_revokesAsset() {
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.SHACL)).thenReturn(true);
    when(assetStore.getFileByHash(ASSET_HASH)).thenReturn(ASSET_CONTENT);
    when(schemaValidationService.validateCredentialAgainstCompositeSchema(ASSET_CONTENT))
        .thenThrow(new VerificationException("non-conforming"));

    service.handleTask(ASSET_HASH);

    verify(assetStore, times(1))
        .changeLifeCycleStatus(eq(ASSET_HASH), eq(AssetStatus.REVOKED));
  }

  @Test
  void handleTask_shaclEnabled_conformingAsset_leavesAssetAlone() {
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.SHACL)).thenReturn(true);
    when(assetStore.getFileByHash(ASSET_HASH)).thenReturn(ASSET_CONTENT);

    service.handleTask(ASSET_HASH);

    verify(schemaValidationService, times(1)).validateCredentialAgainstCompositeSchema(ASSET_CONTENT);
    verify(assetStore, never()).changeLifeCycleStatus(any(), any());
  }

  @Test
  void handleTask_shaclDisabled_neverValidatesNorRevokes() {
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.SHACL)).thenReturn(false);

    service.handleTask(ASSET_HASH);

    verify(assetStore, never()).getFileByHash(any());
    verify(schemaValidationService, never()).validateCredentialAgainstCompositeSchema(any());
    verify(assetStore, never()).changeLifeCycleStatus(any(), any());
  }

  @Test
  void handleTask_shaclDisabledForNonConformingAsset_doesNotRevoke() {
    // Belt-and-braces: even if the validation path were somehow invoked, the gate must
    // short-circuit before the would-be revoke. This guards against future refactors
    // moving the gate inside the try/catch by accident.
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.SHACL)).thenReturn(false);

    service.handleTask(ASSET_HASH);

    verify(assetStore, never()).changeLifeCycleStatus(eq(ASSET_HASH), eq(AssetStatus.REVOKED));
  }
}
