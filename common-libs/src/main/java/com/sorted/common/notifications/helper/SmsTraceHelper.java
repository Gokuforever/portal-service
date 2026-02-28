package com.sorted.common.notifications.helper;

import com.sorted.common.entity.service.SmsTraceService;
import com.sorted.common.enums.SmsTemplate;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Service
public class SmsTraceHelper {

    private final SmsTraceService smsTraceService;

    public SmsTraceHelper(SmsTraceService smsTraceService) {
        this.smsTraceService = smsTraceService;
    }

    @Async
    public void runWithTrace(List<String> mobileNumber, @NonNull String content, SmsTemplate smsTemplate, String createdBy, Supplier<String> supplier) {
        try {
            String res = supplier.get();
            smsTraceService.saveToTrace(mobileNumber, content, smsTemplate, res, createdBy);
        } catch (Exception e) {
            log.error("Error while sending SMS", e);
            smsTraceService.saveToErrorTrace(mobileNumber, content, smsTemplate, e, createdBy);
            throw e;
        }
    }
}
