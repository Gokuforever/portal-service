package com.sorted.common.helper;

import com.sorted.common.entity.service.WebhookTraceService;
import com.sorted.common.enums.WebhookType;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class WebhookTraceHelper {

    private final WebhookTraceService webhookTraceService;

    public WebhookTraceHelper(WebhookTraceService webhookTraceService) {
        this.webhookTraceService = webhookTraceService;
    }

    public <I, R> R runWithTrace(WebhookType webhookType, I input, String cudBy, Supplier<R> supplier) {
        try {
            R res = supplier.get();
            webhookTraceService.saveToTrace(webhookType, input, cudBy, res);
            return res;
        } catch (Exception e) {
            webhookTraceService.saveToErrorTrace(webhookType, input, e.getMessage(), cudBy);
            throw e;
        }
    }

}
