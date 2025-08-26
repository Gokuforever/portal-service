package com.sorted.portal.response.beans;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record FindOneOrderResBean(
        String id,
        String code,
        String status,
        @JsonProperty("transaction_id")
        String transactionId,
        @JsonProperty("total_amount")
        BigDecimal totalAmount,
        @JsonProperty("delivery_address")
        String deliveryAddress,
        @JsonProperty("payment_mode")
        String paymentMode,
        @JsonProperty("order_items")
        List<OrderItemsResBean> orderItems,
        @JsonProperty("invoice_url")
        String invoiceUrl,
        @JsonProperty("delivery_charge")
        BigDecimal deliveryCharge,
        @JsonProperty("total_item_cost")
        BigDecimal totalItemCost,
        @JsonProperty("order_placed_at")
        String orderPlacedAt,
        @JsonProperty("delivered_at")
        String deliveredAt
) {
}
