package com.sorted.common.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.common.enums.DiscountType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record OtherCoupon(
        String code,

        String name,

        String description,

        @JsonProperty("discount_type")
        DiscountType discountType,

        @JsonProperty("discount_value")
        BigDecimal discountValue,

        @JsonProperty("discount_percentage")
        BigDecimal discountPercentage,

        @JsonProperty("min_cart_value")
        BigDecimal minCartValue,

        @JsonProperty("additional_amount_needed")
        BigDecimal additionalAmountNeeded,

        @JsonProperty("potential_discount")
        BigDecimal potentialDiscount,

        @JsonProperty("max_discount")
        BigDecimal maxDiscount,

        @JsonProperty("end_date")
        LocalDateTime endDate,

        @JsonProperty("eligibility_text")
        String eligibilityText,

        @JsonProperty("not_applicable_reason")
        String notApplicableReason,

        @JsonProperty("sort_order")
        int sortOrder
) {
}
