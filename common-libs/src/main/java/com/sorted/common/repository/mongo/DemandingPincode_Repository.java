package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.DemandingPincode;
import com.sorted.common.helper.BaseMongoRepository;

public interface DemandingPincode_Repository extends BaseMongoRepository<String, DemandingPincode> {
    @Override
    default Class<DemandingPincode> getEntityType() {
        return DemandingPincode.class;
    }
}
