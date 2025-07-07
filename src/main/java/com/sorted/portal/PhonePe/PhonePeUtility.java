package com.sorted.portal.PhonePe;

import com.phonepe.sdk.pg.Env;
import com.phonepe.sdk.pg.common.exception.PhonePeException;
import com.phonepe.sdk.pg.common.models.request.RefundRequest;
import com.phonepe.sdk.pg.common.models.response.OrderStatusResponse;
import com.phonepe.sdk.pg.common.models.response.RefundResponse;
import com.phonepe.sdk.pg.payments.v2.StandardCheckoutClient;
import com.phonepe.sdk.pg.payments.v2.models.request.StandardCheckoutPayRequest;
import com.phonepe.sdk.pg.payments.v2.models.response.StandardCheckoutPayResponse;
import com.sorted.commons.entity.mongo.Third_Party_Api;
import com.sorted.portal.enums.RequestType;
import com.sorted.portal.service.ThirdPartyRequestResponseService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PhonePeUtility {

    private static final Logger logger = LoggerFactory.getLogger(PhonePeUtility.class);

    @Value("${se.front.end.base_url}")
    private String baseUrl;

    String clientId = "TEST-M22E4KYKJXIAV_25050";
    String clientSecret = "NGQxNzFmMWYtMjljYy00MzI2LWIxYjAtNzFjMzIxMTJiNTY1";
    Integer clientVersion = 1;  //insert your client version here
    Env env = Env.SANDBOX;      //change to Env.PRODUCTION when you go live

    StandardCheckoutClient client = StandardCheckoutClient.getInstance(clientId, clientSecret,
            clientVersion, env);

    private final ThirdPartyRequestResponseService thirdPartyRequestResponseService;

    public Optional<StandardCheckoutPayResponse> createOrder(String orderId, long amount) {
        StandardCheckoutPayRequest standardCheckoutPayRequest = StandardCheckoutPayRequest.builder()
                .merchantOrderId(orderId)
                .amount(amount)
                .redirectUrl(baseUrl + orderId)
                .build();

        Third_Party_Api register = thirdPartyRequestResponseService.register(standardCheckoutPayRequest, RequestType.PP_CREATE_ORDER);

        try {
            StandardCheckoutPayResponse response = client.pay(standardCheckoutPayRequest);
            thirdPartyRequestResponseService.updateResponse(register, response);
            return Optional.of(response);
        } catch (PhonePeException phonePeException) {
            Integer httpStatusCode = phonePeException.getHttpStatusCode();
            String message = phonePeException.getMessage();
            Map<String, Object> data = phonePeException.getData();
            String code = phonePeException.getCode();

            logger.error("PhonePe order creation failed - Code: {}, Message: {}, Status: {}, OrderId: {}, Data: {}",
                    code, message, httpStatusCode, orderId, data);

            thirdPartyRequestResponseService.registerException(register, message);
            return Optional.empty();
        }
    }

    public Optional<RefundResponse> refund(String merchantRefundId, String originalMerchantOrderId, long amount) {
        RefundRequest refundRequest = RefundRequest.builder()
                .merchantRefundId(merchantRefundId)
                .originalMerchantOrderId(originalMerchantOrderId)
                .amount(amount)
                .build();
        Third_Party_Api register = thirdPartyRequestResponseService.register(refundRequest, RequestType.PP_REFUND);
        try {
            RefundResponse refundResponse = client.refund(refundRequest);
            thirdPartyRequestResponseService.updateResponse(register, refundResponse);
            return Optional.of(refundResponse);
        } catch (PhonePeException phonePeException) {
            Integer httpStatusCode = phonePeException.getHttpStatusCode();
            String message = phonePeException.getMessage();
            Map<String, Object> data = phonePeException.getData();
            String code = phonePeException.getCode();

            logger.error("PhonePe refund failed - Code: {}, Message: {}, Status: {}, OrderId: {}, Data: {}",
                    code, message, httpStatusCode, originalMerchantOrderId, data);

            thirdPartyRequestResponseService.registerException(register, message);
            return Optional.empty();
        }
    }

    public Optional<OrderStatusResponse> checkStatus(String orderId) {
        Third_Party_Api register = thirdPartyRequestResponseService.register(orderId, RequestType.PP_CHECK_STATUS);

        try {
            OrderStatusResponse response = client.getOrderStatus(orderId);
            thirdPartyRequestResponseService.updateResponse(register, response);
            return Optional.of(response);
        } catch (PhonePeException phonePeException) {
            Integer httpStatusCode = phonePeException.getHttpStatusCode();
            String message = phonePeException.getMessage();
            Map<String, Object> data = phonePeException.getData();
            String code = phonePeException.getCode();

            logger.error("PhonePe status check failed - Code: {}, Message: {}, Status: {}, OrderId: {}",
                    code, message, httpStatusCode, orderId);

            thirdPartyRequestResponseService.registerException(register, message);
            return Optional.empty();
        } catch (Exception e) {
            logger.error("PhonePe status check failed - Code: {}, Message: {}, Status: {}, OrderId: {}",
                    "500", e.getMessage(), 500, orderId);
            throw new RuntimeException(e);
        }
    }
}
