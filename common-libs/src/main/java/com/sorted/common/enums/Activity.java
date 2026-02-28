package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Activity {

	// @formatter:off
	HOME(100,"Home"),
	PRODUCTS(101,"Products"),
	INVENTORY_MANAGEMENT(102,"Inventory Management"),
	CART_MANAGEMENT(103,"Cart Management"),
	PURCHASE(104,"Purchase"),
	ORDER_MANAGEMENT(105,"Order Management"),
	SUBSCRIBE(106,"Subscribe"),
	USER_MANAGEMENT(107,"User Management"),
	MANAGE_ADDRESS(108,"Manage Address"),
	SELLER_ONBOARDING(109,"Seller Onboarding"),
	SIGN_UP(110,"Sign Up"),
	SELLER_MANAGEMENT(111,"Seller Management"),
	STORE_MANAGEMENT(112,"Store Management"),
	USER_PROFILE(113,"User Profile"),
	SECURE_RETURN(114,"Secure Return"),
	SETTLEMENT(115,"Settlement"),
	OPEN_STORE(116,"Open Store"),
	CLOSE_STORE(117,"Close Store"),
	AUTO_OPEN_STORE(118,"Auto Open Store"),
	AUTO_CLOSE_STORE(119,"Auto Close Store"),
	APPRAISE_SECURE_RETURN(120,"Appraise Secure Return"),
	MANAGE_COMBO(121,"Manage Combo"),
	;
	// @formatter:on

	private final int id;
	private final String name;
}
