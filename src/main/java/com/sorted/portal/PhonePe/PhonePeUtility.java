package com.sorted.portal.PhonePe;

import com.phonepe.sdk.pg.Env;
import com.phonepe.sdk.pg.common.models.response.OrderStatusResponse;
import com.phonepe.sdk.pg.payments.v2.StandardCheckoutClient;
import com.phonepe.sdk.pg.payments.v2.models.request.StandardCheckoutPayRequest;
import com.phonepe.sdk.pg.payments.v2.models.response.StandardCheckoutPayResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PhonePeUtility {


    @Value("${se.front.end.base_url}")
    private String baseUrl;

    String clientId = "TEST-M22E4KYKJXIAV_25050";
    String clientSecret = "NGQxNzFmMWYtMjljYy00MzI2LWIxYjAtNzFjMzIxMTJiNTY1";
    Integer clientVersion = 1;  //insert your client version here
    Env env = Env.SANDBOX;      //change to Env.PRODUCTION when you go live

    StandardCheckoutClient client = StandardCheckoutClient.getInstance(clientId, clientSecret,
            clientVersion, env);

    public StandardCheckoutPayResponse createOrder(String orderId, long amount) {
        StandardCheckoutPayRequest standardCheckoutPayRequest = StandardCheckoutPayRequest.builder()
                .merchantOrderId(orderId)
                .amount(amount)
                .redirectUrl(baseUrl + orderId)
                .build();

        return client.pay(standardCheckoutPayRequest);
    }

    public OrderStatusResponse checkStatus(String orderId) {
        return client.getOrderStatus(orderId);
    }

}
