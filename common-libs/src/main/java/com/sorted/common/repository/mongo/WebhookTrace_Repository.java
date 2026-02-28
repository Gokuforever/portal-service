package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.WebhookTrace;
import com.sorted.common.helper.BaseMongoRepository;

public interface WebhookTrace_Repository extends BaseMongoRepository<String, WebhookTrace> {

    @Override
    default Class<WebhookTrace> getEntityType() {
        return WebhookTrace.class;
    }
}
