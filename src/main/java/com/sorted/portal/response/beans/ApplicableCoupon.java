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
public class ApplicableCoupon {
    
    private String code;
    
    private String name;
    
    private String description;
    
    @JsonProperty("discount_type")
    private DiscountType discountType;
    
    @JsonProperty("discount_value")
    private BigDecimal discountValue;
    
    @JsonProperty("discount_percentage")
    private BigDecimal discountPercentage;
    
    @JsonProperty("calculated_discount")
    private BigDecimal calculatedDiscount;
    
    @JsonProperty("final_cart_value")
    private BigDecimal finalCartValue;
    
    @JsonProperty("max_discount")
    private BigDecimal maxDiscount;
    
    @JsonProperty("min_cart_value")
    private BigDecimal minCartValue;
    
    @JsonProperty("end_date")
    private LocalDateTime endDate;
    
    @JsonProperty("savings_text")
    private String savingsText;
    
    @JsonProperty("is_best_offer")
    private boolean isBestOffer;
    
    @JsonProperty("sort_order")
    private int sortOrder;
}
