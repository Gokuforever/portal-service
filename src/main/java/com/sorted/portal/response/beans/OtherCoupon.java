package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.commons.enums.DiscountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtherCoupon {
    
    private String code;
    
    private String name;
    
    private String description;
    
    @JsonProperty("discount_type")
    private DiscountType discountType;
    
    @JsonProperty("discount_value")
    private BigDecimal discountValue;
    
    @JsonProperty("discount_percentage")
    private BigDecimal discountPercentage;
    
    @JsonProperty("min_cart_value")
    private BigDecimal minCartValue;
    
    @JsonProperty("additional_amount_needed")
    private BigDecimal additionalAmountNeeded;
    
    @JsonProperty("potential_discount")
    private BigDecimal potentialDiscount;
    
    @JsonProperty("max_discount")
    private BigDecimal maxDiscount;
    
    @JsonProperty("end_date")
    private LocalDateTime endDate;
    
    @JsonProperty("eligibility_text")
    private String eligibilityText;
    
    @JsonProperty("not_applicable_reason")
    private String notApplicableReason;
    
    @JsonProperty("sort_order")
    private int sortOrder;
}
