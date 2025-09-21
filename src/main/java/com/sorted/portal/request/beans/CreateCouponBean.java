package com.sorted.portal.request.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.commons.enums.CouponScope;
import com.sorted.commons.enums.DiscountType;

import java.math.BigDecimal;
import java.util.List;

public record CreateCouponBean(
        String name,
        String code,
        String description,
        @JsonProperty("discount_type")
        DiscountType discountType,
        @JsonProperty("discount_value")
        BigDecimal discountValue,
        @JsonProperty("discount_percentage")
        BigDecimal discountPercentage,
        @JsonProperty("max_usage")
        Integer maxUsage,
        @JsonProperty("start_date")
        String startDate,
        @JsonProperty("end_date")
        String endDate,
        @JsonProperty("coupon_scope")
        CouponScope couponScope,
        @JsonProperty("once_per_user")
        boolean oncePerUser,
        @JsonProperty("assigned_to_users")
        List<String> assignedToUsers,
//        @JsonProperty("eligible_user_ids")
//        List<String> eligibleUserIds,
        @JsonProperty("max_discount")
        BigDecimal maxDiscount

) {
}
