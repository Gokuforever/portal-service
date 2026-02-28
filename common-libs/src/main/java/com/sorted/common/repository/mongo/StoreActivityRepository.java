package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.StoreActivity;
import com.sorted.common.helper.BaseMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreActivityRepository extends BaseMongoRepository<String, StoreActivity> {

    @Override
    default Class<StoreActivity> getEntityType() {
        return StoreActivity.class;
    }
}
