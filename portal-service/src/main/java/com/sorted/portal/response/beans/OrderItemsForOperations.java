package com.sorted.portal.response.beans;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record OrderItemsForOperations(
        String orderItemId,
        String productId,
        String productName,
        String productImage,
        int quantity,
        BigDecimal sellingPrice,
        BigDecimal totalCost
) {
}
