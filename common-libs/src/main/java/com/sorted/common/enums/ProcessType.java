package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProcessType {

	SIGN_UP, SIGN_IN, FORGOT_PASS, SELLER_ONBOARDING, UPDATE_PASS, AUTH, PROFILE, AMBASSADOR_ONBOARDING;
}
