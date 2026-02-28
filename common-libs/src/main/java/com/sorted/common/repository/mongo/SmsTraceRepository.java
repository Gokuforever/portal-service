package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.SmsTraceEntity;
import com.sorted.common.helper.BaseMongoRepository;

public interface SmsTraceRepository extends BaseMongoRepository<String, SmsTraceEntity> {

    @Override
    default Class<SmsTraceEntity> getEntityType() {
        return SmsTraceEntity.class;
    }
}
