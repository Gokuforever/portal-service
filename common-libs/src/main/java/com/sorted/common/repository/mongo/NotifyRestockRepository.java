package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.NotifyRestockEntity;
import com.sorted.common.helper.BaseMongoRepository;

public interface NotifyRestockRepository extends BaseMongoRepository<String, NotifyRestockEntity> {

    @Override
    default Class<NotifyRestockEntity> getEntityType() {
        return NotifyRestockEntity.class;
    }
}
