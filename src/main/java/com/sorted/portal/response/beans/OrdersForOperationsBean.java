package com.sorted.portal.response.beans;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record OrdersForOperationsBean(
        String orderId,
        String orderCode,
        BigDecimal amount,
        String reason,
        String customerName,
        String customerEmail,
        String userPhoneNo,
        String nameForDelivery,
        String phoneForDelivery,
        String customerAddress,
        String status,
        int statusId
) {
}
