package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponListResponse {
    
    @JsonProperty("applicable_coupons")
    private List<ApplicableCoupon> applicableCoupons;
    
    @JsonProperty("other_coupons")
    private List<OtherCoupon> otherCoupons;
    
    @JsonProperty("current_cart_value")
    private BigDecimal currentCartValue;
    
    @JsonProperty("delivery_charge")
    private BigDecimal deliveryCharge;
    
    @JsonProperty("total_amount")
    private BigDecimal totalAmount;
}
