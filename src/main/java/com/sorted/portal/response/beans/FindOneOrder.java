package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
public record FindOneOrder(String id, String code, @JsonProperty("payment_mode") String paymentMode,
                           @JsonProperty("total_amount") Long totalAmount, String status,
                           @JsonProperty("transaction_id") String transactionId,
                           @JsonProperty("payment_status") String paymentStatus,
                           @JsonProperty("delivery_address") AddressResponse deliveryAddress,
                           @JsonProperty("order_items") List<OrderItemResponse> orderItems) {
}
