package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.commons.enums.DiscountType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record ActiveCoupon(
        @JsonProperty("coupon_code")
        String couponCode,
        String description,
        @JsonProperty("discount_type")
        DiscountType discountType,
        @JsonProperty("discount_value")
        BigDecimal discountValue,
        @JsonProperty("discount_percentage")
        BigDecimal discountPercentage,
        @JsonProperty("min_order_value")
        LocalDate startDate,
        LocalDate endDate
) {
}
