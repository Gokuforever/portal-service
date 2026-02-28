package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.WalletEntity;
import com.sorted.common.helper.BaseMongoRepository;

public interface WalletRepository extends BaseMongoRepository<String, WalletEntity> {

    @Override
    default Class<WalletEntity> getEntityType() {
        return WalletEntity.class;
    }
}
