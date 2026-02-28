package com.sorted.common.exceptions;

import com.sorted.common.enums.ResponseCode;

public class InvalidDiscountType extends CustomIllegalArgumentsException {
    public InvalidDiscountType() {
        super(ResponseCode.MISSING_COUPON_DISCOUNT_VALUE);
    }
}