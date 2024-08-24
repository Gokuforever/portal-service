package com.sorted.portal.razorpay;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

@RestController
@RequestMapping("/razorpay/order")
public class RazorpayUtility {

	@Value("${se.razorpay.key}")
	private String rzr_key;

	@Value("${se.razorpay.secret}")
	private String rzr_secret;

	@PostMapping("/create")
	public void create(Long amount, String order_id, String note) throws RazorpayException {

		if (amount == null) {

		}

		RazorpayClient razorpay = new RazorpayClient(rzr_key, rzr_secret);

		JSONObject orderRequest = new JSONObject();
		orderRequest.put("amount", amount);
		orderRequest.put("currency", "INR");
		orderRequest.put("receipt", order_id);
		JSONObject notes = new JSONObject();
		notes.put("notes_key_1", note);
		orderRequest.put("notes", notes);

		Order order = razorpay.orders.create(orderRequest);
		System.out.println(order);
		System.out.println(order);

	}

}
