package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyCouponResponse {
    
    @JsonProperty("success")
    private boolean success;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("coupon_code")
    private String couponCode;
    
    @JsonProperty("discount_applied")
    private BigDecimal discountApplied;
    
    @JsonProperty("original_amount")
    private BigDecimal originalAmount;
    
    @JsonProperty("final_amount")
    private BigDecimal finalAmount;
    
    @JsonProperty("delivery_charge")
    private BigDecimal deliveryCharge;
    
    @JsonProperty("savings_text")
    private String savingsText;
    
    @JsonProperty("error_code")
    private String errorCode;
}
