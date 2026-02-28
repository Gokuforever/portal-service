package com.sorted.common.exceptions;

import com.sorted.common.enums.ResponseCode;

public class InvalidReferralCodeException extends CustomIllegalArgumentsException {
    public InvalidReferralCodeException() {
        super(ResponseCode.INVALID_REFERRAL_CODE);
    }
}
