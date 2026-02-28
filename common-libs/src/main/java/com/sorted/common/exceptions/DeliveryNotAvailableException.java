package com.sorted.common.exceptions;

import java.io.Serial;

import static com.sorted.common.enums.ResponseCode.RESTRICTED_LOCATION;

public class DeliveryNotAvailableException extends BaseException {
    /**
     *
     */
    @Serial
    private static final long serialVersionUID = 1L;

    public DeliveryNotAvailableException() {
        super(RESTRICTED_LOCATION);
    }

}
