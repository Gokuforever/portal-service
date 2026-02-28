package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.WalletTransactionEntity;
import com.sorted.common.helper.BaseMongoRepository;

public interface WalletTransactionRepository extends BaseMongoRepository<String, WalletTransactionEntity> {

    @Override
    default Class<WalletTransactionEntity> getEntityType() {
        return WalletTransactionEntity.class;
    }
}
