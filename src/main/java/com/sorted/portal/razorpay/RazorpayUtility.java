package com.sorted.portal.razorpay;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

import lombok.NonNull;

@RestController
@RequestMapping("/razorpay/order")
public class RazorpayUtility {

	@Value("${se.razorpay.key}")
	private String rzr_key;

	@Value("${se.razorpay.secret}")
	private String rzr_secret;

	public Order createOrder(@NonNull Long amount, @NonNull String order_id) throws RazorpayException {

		RazorpayClient razorpay = new RazorpayClient(rzr_key, rzr_secret);

		JSONObject orderRequest = new JSONObject();
		orderRequest.put("amount", amount);
		orderRequest.put("currency", "INR");
		orderRequest.put("receipt", order_id);

		return razorpay.orders.create(orderRequest);
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
}
