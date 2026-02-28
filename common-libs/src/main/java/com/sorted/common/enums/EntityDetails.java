package com.sorted.common.enums;

import com.sorted.common.entity.mongo.*;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;

@Getter
@AllArgsConstructor
public enum EntityDetails {

	// @formatter:off
	CART(Cart.class),
	CATEGORY_MASTER(Category_Master.class),
	ORDER_DETAILS(Order_Details.class),
	OTP(Otp.class),
	PRODUCTS(Products.class),
	ROLE(Role.class),
	SMSPOOL(SmsPool.class),
	SELLER(Seller.class),
	THIRD_PARTY_API(Third_Party_Api.class),
	TRANSACTION_REQ_RESPONSE(Transaction_Req_Response.class),
	USERS(Users.class),
	VARIENT_MAPPING(Varient_Mapping.class);
	// @formatter:on

	private final Class<?> class_name;

	public static final Map<Class<?>, EntityDetails> byValue = new HashMap<>();

	static {
		for (EntityDetails ed : values()) {
			byValue.put(ed.getClass_name(), ed);
		}
	}

	public static void assertExists(@NonNull Class<?> clazz) {
		if (!byValue.containsKey(clazz)) {
			throw new CustomIllegalArgumentsException(ResponseCode.ENTITY_DEFINATION_INCOMPLETE);
		}
	}
}
