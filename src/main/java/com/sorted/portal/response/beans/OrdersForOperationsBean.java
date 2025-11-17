package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record OrdersForOperationsBean(
        String orderId,
        String orderCode,
        String rejectedReason,
        String customerName,
        String customerEmail,
        String userPhoneNo,
        String nameForDelivery,
        String phoneForDelivery,
        String customerAddress,
        String status,
        int statusId,
        BigDecimal totalAmount,
        BigDecimal deliveryCharge,
        BigDecimal totalItemCost,
        BigDecimal totalDiscount,
        BigDecimal totalMrp,
        BigDecimal totalSellingPrice,
        String orderPlacedAt,
        String deliveredAt,
        String transactionId,
        String couponCode,
        String deliveryPartnerOrderId,
        int totalItemCount,
        List<OrderItemsForOperations> orderItems
) {
}
