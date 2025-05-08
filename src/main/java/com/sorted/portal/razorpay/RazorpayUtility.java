package com.sorted.portal.razorpay;

import com.razorpay.*;
import com.sorted.commons.entity.mongo.Third_Party_Api;
import com.sorted.portal.enums.RequestType;
import com.sorted.portal.service.ThirdPartyRequestResponseService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/razorpay/order")
@RequiredArgsConstructor
public class RazorpayUtility {

    @Value("${se.razorpay.key}")
    private String rzr_key;

    @Value("${se.razorpay.secret}")
    private String rzr_secret;

    private final ThirdPartyRequestResponseService thirdPartyRequestResponseService;


    public Order createOrder(@NonNull Long amount, @NonNull String order_id) throws RazorpayException {

        RazorpayClient razorpay = new RazorpayClient(rzr_key, rzr_secret);

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amount);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", order_id);
        Third_Party_Api register = thirdPartyRequestResponseService.register(orderRequest, RequestType.RZ_CREATE_ORDER);
        Order order;
        try {
            order = razorpay.orders.create(orderRequest);
        } catch (Exception ex) {
            thirdPartyRequestResponseService.registerException(register, ex.getMessage());
            return null;
        }
        thirdPartyRequestResponseService.updateResponse(register, order);
        return order;
    }

    public CheckoutReqbean createCheckoutPayload(Order order) {
        CheckoutReqbean checkoutReqbean = new CheckoutReqbean();
        checkoutReqbean.setKey(rzr_key);
        JSONObject json = order.toJson();
        String pg_order_id = json.get("id").toString();
        int amount = Integer.parseInt(json.get("amount").toString());
        checkoutReqbean.setAmount(amount);
        checkoutReqbean.setCurrency("INR");
        checkoutReqbean.setName("StudEaze");
        checkoutReqbean.setDescription("Testing purpose transaction.");
        checkoutReqbean.setImage("https://drive.usercontent.google.com/download?id=1n065SpmGA2eRRwvaVTpRb9P1ciPF-SiZ");
        checkoutReqbean.setOrder_id(pg_order_id);

        CheckoutReqbean.CustomerDetails cus = new CheckoutReqbean.CustomerDetails();
        cus.setContact("9867292392");
        cus.setEmail("yogeshkhaire288@gmail.com");
        cus.setName("Yogesh Khaire");
        return checkoutReqbean;

    }

    public boolean verifySignature(@NonNull String order_id, @NonNull String payment_id, @NonNull String signature)
            throws RazorpayException {

        JSONObject options = new JSONObject();
        options.put("razorpay_order_id", order_id);
        options.put("razorpay_payment_id", payment_id);
        options.put("razorpay_signature", signature);
        return Utils.verifyPaymentSignature(options, rzr_secret);

    }

    public Refund refund(String paymentId, Long amount) throws RazorpayException {
        RazorpayClient razorpay = new RazorpayClient(rzr_key, rzr_secret);
        JSONObject instantRefundRequest = new JSONObject();
        instantRefundRequest.put("amount", amount);

        Third_Party_Api register = thirdPartyRequestResponseService.register(instantRefundRequest, RequestType.RZ_REFUND);

        Refund refund;
        try {
            refund = razorpay.payments.refund(paymentId, instantRefundRequest);
        } catch (Exception ex) {
            thirdPartyRequestResponseService.registerException(register, ex.getMessage());
            return null;
        }
        thirdPartyRequestResponseService.updateResponse(register, refund);
        return refund;
    }

}
