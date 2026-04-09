package eu.xfsc.fc.core.dao.assets;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for AssetMapper null-safety.
 */
class AssetMapperTest {

  @Test
  void toEntity_nullRecord_returnsNull() {
    assertNull(AssetMapper.toEntity(null));
  }

  @Test
  void toRecord_nullEntity_returnsNull() {
    assertNull(AssetMapper.toRecord(null));
  }
}
