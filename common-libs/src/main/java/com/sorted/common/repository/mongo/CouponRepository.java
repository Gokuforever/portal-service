package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.CouponEntity;
import com.sorted.common.helper.BaseMongoRepository;

public interface CouponRepository extends BaseMongoRepository<String, CouponEntity> {

    @Override
    default Class<CouponEntity> getEntityType() {
        return CouponEntity.class;
    }
}
