package com.sorted.common.exceptions;

import com.sorted.common.enums.ResponseCode;

public class InvalidArgumentException extends CustomIllegalArgumentsException {
    public InvalidArgumentException(ResponseCode responseCode) {
        super(responseCode);
    }
}
