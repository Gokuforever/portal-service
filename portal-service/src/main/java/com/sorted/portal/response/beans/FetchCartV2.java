package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record FetchCartV2(
        @JsonProperty("total_count")
        Long totalCount,
        @JsonProperty("total_amount")
        BigDecimal totalAmount,
        @JsonProperty("total_item_cost")
        BigDecimal totalItemCost,
        @JsonProperty("free_delivery_diff")
        BigDecimal freeDeliveryDiff,
        @JsonProperty("is_delivery_free")
        Boolean deliveryFree,
        @JsonProperty("minimum_cart_value")
        BigDecimal minimumCartValue,
        BigDecimal savings
) {
}
