package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SmsTemplate {

    OTP( "197876"),
    NEW_ORDER( "197875"),
    ORDER_DISPATCHED( "198167"),
    SECURE_CONFIRMATION( "198166"),
    SECURE_DUE( "198165"),
    DELIVERED( "198164"),
    ORDER_CONFIRMED( "198163"),
    ;

    private final String templateId;
}
