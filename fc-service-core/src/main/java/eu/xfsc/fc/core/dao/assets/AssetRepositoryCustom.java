package eu.xfsc.fc.core.dao.assets;

import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;

public interface AssetRepositoryCustom {

  PaginatedResults<AssetRecord> selectByFilter(AssetFilter filter,
      boolean withMeta, boolean withContent);
}
