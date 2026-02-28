package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.LaunchingEmail;
import com.sorted.common.helper.BaseMongoRepository;

public interface LaunchingEmailRepository extends BaseMongoRepository<String, LaunchingEmail> {

    @Override
    default Class<LaunchingEmail> getEntityType() {
        return LaunchingEmail.class;
    }
}
