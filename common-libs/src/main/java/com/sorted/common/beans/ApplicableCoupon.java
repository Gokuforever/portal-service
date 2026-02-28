package com.sorted.common.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.common.enums.DiscountType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record ApplicableCoupon(
        String code,

        String name,

        String description,

        @JsonProperty("discount_type")
        DiscountType discountType,

        @JsonProperty("discount_value")
        BigDecimal discountValue,

        @JsonProperty("discount_percentage")
        BigDecimal discountPercentage,

        @JsonProperty("calculated_discount")
        BigDecimal calculatedDiscount,

        @JsonProperty("final_cart_value")
        BigDecimal finalCartValue,

        @JsonProperty("max_discount")
        BigDecimal maxDiscount,

        @JsonProperty("min_cart_value")
        BigDecimal minCartValue,

        @JsonProperty("end_date")
        LocalDateTime endDate,

        @JsonProperty("savings_text")
        String savingsText,

        @JsonProperty("is_best_offer")
        boolean isBestOffer,

        @JsonProperty("sort_order")
        int sortOrder,

        @JsonProperty("is_applied")
        boolean isApplied
) {

}
