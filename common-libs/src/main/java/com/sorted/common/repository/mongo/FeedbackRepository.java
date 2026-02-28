package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.Feedback;
import com.sorted.common.helper.BaseMongoRepository;

public interface FeedbackRepository extends BaseMongoRepository<String, Feedback> {

    @Override
    default Class<Feedback> getEntityType() {
        return Feedback.class;
    }
}
