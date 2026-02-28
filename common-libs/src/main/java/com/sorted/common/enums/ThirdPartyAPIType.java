package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ThirdPartyAPIType {

    PORTER_GET_QUOTE, PORTER_CREATE_ORDER, PORTER_GET_ORDER_STATUS, PHONEPE_CREATE_ORDER, PHONEPE_GET_ORDER_STATUS, PHONEPE_INITIATE_REFUND, PHONEPE_REFUND_STATUS;
}
