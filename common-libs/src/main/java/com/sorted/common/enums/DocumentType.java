package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
@AllArgsConstructor
public enum DocumentType {
	PRODUCT_IMAGE(1, Arrays.asList(UserType.SUPER_ADMIN, UserType.SELLER)),
	PROFILE_PICTURE(2, Arrays.asList(UserType.SUPER_ADMIN, UserType.GUEST, UserType.CUSTOMER, UserType.SELLER)),
	LOGO(3, Arrays.asList(UserType.SUPER_ADMIN, UserType.SELLER)),
	INVOICE(4, Arrays.asList(UserType.SUPER_ADMIN, UserType.CUSTOMER)),
	PROMO_BANNER(5, Arrays.asList(UserType.SUPER_ADMIN, UserType.GUEST, UserType.CUSTOMER, UserType.SELLER)),
	;

	private final int id;
	private final List<UserType> allowed_to;

}