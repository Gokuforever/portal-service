package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.AmbassadorDumpEntity;
import com.sorted.common.helper.BaseMongoRepository;

public interface AmbassadorDumpRepository extends BaseMongoRepository<String, AmbassadorDumpEntity> {

    @Override
    default Class<AmbassadorDumpEntity> getEntityType() {
        return AmbassadorDumpEntity.class;
    }
}
