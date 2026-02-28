package com.sorted.common.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record CouponListResponse(
        @JsonProperty("applicable_coupons")
        List<ApplicableCoupon> applicableCoupons,
        @JsonProperty("other_coupons")
        List<OtherCoupon> otherCoupons,
        @JsonProperty("current_cart_value")
        BigDecimal currentCartValue
) {
}
