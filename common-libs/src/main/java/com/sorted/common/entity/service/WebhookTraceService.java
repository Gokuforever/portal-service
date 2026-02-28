package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.WebhookTrace;
import com.sorted.common.enums.WebhookType;
import com.sorted.common.repository.mongo.WebhookTrace_Repository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WebhookTraceService extends GenericEntityServiceImpl<String, WebhookTrace, WebhookTrace_Repository> {

    @Override
    protected Class<WebhookTrace_Repository> getRepoClass() {
        return WebhookTrace_Repository.class;
    }

    @Override
    protected void validateBeforeCreate(WebhookTrace inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeUpdate(String id, WebhookTrace inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }

    public <I, R> void saveToTrace(WebhookType webhookType, I input, String cudBy, R res) {
        WebhookTrace webhookTrace = WebhookTrace.builder()
                .request(input)
                .response(res)
                .type(webhookType)
                .build();
        this.create(webhookTrace, cudBy);
    }

    public <I> void saveToErrorTrace(WebhookType webhookType, I input, String errorMessage, String cudBy) {
        Map<String, String> message = Map.of("errorMessage", errorMessage);
        WebhookTrace webhookTrace = WebhookTrace.builder()
                .request(input)
                .response(message)
                .type(webhookType)
                .build();
        this.create(webhookTrace, cudBy);
    }
}
