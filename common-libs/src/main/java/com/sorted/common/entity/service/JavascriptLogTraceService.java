package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.JavascriptLogTrace;
import com.sorted.common.repository.mongo.JavascriptLogTrace_Repository;
import org.springframework.stereotype.Service;

@Service
public class JavascriptLogTraceService extends GenericEntityServiceImpl<String, JavascriptLogTrace, JavascriptLogTrace_Repository> {
    @Override
    protected Class<JavascriptLogTrace_Repository> getRepoClass() {
        return JavascriptLogTrace_Repository.class;
    }

    @Override
    protected void validateBeforeCreate(JavascriptLogTrace inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeUpdate(String id, JavascriptLogTrace inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }
}
