package com.sorted.common.repository.mongo;

import com.sorted.common.entity.mongo.JavascriptLogTrace;
import com.sorted.common.helper.BaseMongoRepository;

public interface JavascriptLogTrace_Repository extends BaseMongoRepository<String, JavascriptLogTrace> {
    @Override
    default Class<JavascriptLogTrace> getEntityType() {
        return JavascriptLogTrace.class;
    }
}
