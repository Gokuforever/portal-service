package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.ZoneEntity;
import com.sorted.common.helper.BaseMongoRepository;

public interface ZoneRepository extends BaseMongoRepository<String, ZoneEntity> {

    @Override
    default Class<ZoneEntity> getEntityType() {
        return ZoneEntity.class;
    }
}
