package com.sorted.portal.response.beans;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record SecureOrderItemDetail(
        String productId,
        String productName,
        int quantity,
        BigDecimal sellingPrice,
        BigDecimal maxExpectedSecureRefund
) {
}
