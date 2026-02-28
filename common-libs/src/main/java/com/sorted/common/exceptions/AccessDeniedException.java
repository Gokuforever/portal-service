package com.sorted.common.exceptions;

import com.sorted.common.enums.ResponseCode;

public class AccessDeniedException extends CustomIllegalArgumentsException {
    public AccessDeniedException() {
        super(ResponseCode.ACCESS_DENIED);
    }
}
