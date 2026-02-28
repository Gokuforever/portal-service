package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record AllCouponsBean(
        String code,
        String name,
        String description,
        @JsonProperty("discount_value")
        BigDecimal discountValue,
        @JsonProperty("total_amount")
        BigDecimal totalAmount,
        @JsonProperty("total_amount_after_discount")
        BigDecimal totalAmountAfterDiscount,
        @JsonProperty("min_cart_value")
        BigDecimal minCartValue,
        boolean isApplicable,
        int order
) {
}
