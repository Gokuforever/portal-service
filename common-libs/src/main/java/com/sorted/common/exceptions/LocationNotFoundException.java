package com.sorted.common.exceptions;

import com.sorted.common.enums.ResponseCode;

public class LocationNotFoundException extends CustomIllegalArgumentsException {
    public LocationNotFoundException() {
        super(ResponseCode.LOCATION_NOT_FOUND);
    }
}
