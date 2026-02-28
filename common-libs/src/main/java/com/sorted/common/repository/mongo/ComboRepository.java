package com.sorted.common.repository.mongo;


import com.sorted.common.entity.mongo.Combo;
import com.sorted.common.helper.BaseMongoRepository;

public interface ComboRepository extends BaseMongoRepository<String, Combo> {

    @Override
    default Class<Combo> getEntityType() {
        return Combo.class;
    }
}
