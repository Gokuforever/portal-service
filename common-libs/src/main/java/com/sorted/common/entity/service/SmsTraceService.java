package com.sorted.common.entity.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sorted.common.entity.mongo.SmsTraceEntity;
import com.sorted.common.enums.SmsTemplate;
import com.sorted.common.repository.mongo.SmsTraceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class SmsTraceService extends GenericEntityServiceImpl<String, SmsTraceEntity, SmsTraceRepository> {

    @Override
    protected Class<SmsTraceRepository> getRepoClass() {
        return SmsTraceRepository.class;
    }

    @Override
    protected void validateBeforeCreate(SmsTraceEntity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeUpdate(String id, SmsTraceEntity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }

    public void saveToTrace(List<String> mobileNumbers, String content, SmsTemplate template, String response, String cudby) {
        SmsTraceEntity traceEntity = SmsTraceEntity.builder()
                .mobile(String.join(",", mobileNumbers))
                .content(content)
                .template(template)
                .build();
        log.info("response: {}", response);
        if (response != null) {
            JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();
            log.info("jsonObject: {}", jsonObject);
            boolean isSent = jsonObject.get("return").getAsBoolean();
            traceEntity.setSent(isSent);
        }
        this.create(traceEntity, cudby);
    }

    public void saveToErrorTrace(List<String> mobileNumbers, String content, SmsTemplate template, Exception exception, String cudby) {
        SmsTraceEntity traceEntity = SmsTraceEntity.builder()
                .mobile(String.join(",", mobileNumbers))
                .content(content)
                .template(template)
                .isSent(false)
                .errorDesc(exception.getMessage())
                .build();
        this.create(traceEntity, cudby);
    }
}
