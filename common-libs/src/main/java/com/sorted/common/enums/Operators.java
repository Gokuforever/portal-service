package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Operators {

	EQUALS, NOT_EQUALS, LIKE, IN, NIN, NOT_LIKE, ALL, ELEMMATCH_IN, GTE, LTE, GT, LT, IS_NULL, IS_NOT_NULL, IS_EMPTY, IS_NOT_EMPTY;
}
