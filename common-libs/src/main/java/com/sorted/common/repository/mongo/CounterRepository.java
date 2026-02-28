package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Counter;
import com.sorted.common.helper.BaseMongoRepository;

public interface CounterRepository extends BaseMongoRepository<String, Counter> {
    @Override
    default Class<Counter> getEntityType() {
        return Counter.class;
    }
}
