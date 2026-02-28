package com.sorted.common.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BillingSummary {
    @JsonProperty("coupon_code")
    private String couponCode;
    @JsonProperty("to_pay")
    private BigDecimal toPay;
    private BigDecimal savings;
    @JsonProperty("delivery_fee")
    private BigDecimal deliveryFee;
    @JsonProperty("actual_delivery_fee")
    private BigDecimal actualDeliveryFee;
    @JsonProperty("small_cart_fee")
    private BigDecimal smallCartFee;
    @JsonProperty("actual_small_cart_fee")
    private BigDecimal actualSmallCartFee;
    @JsonProperty("handling_fee")
    private BigDecimal handlingFee;
    @JsonProperty("actual_handling_fee")
    private BigDecimal actualHandlingFee;
    @JsonProperty("total_mrp")
    private BigDecimal totalMrp;
    @JsonProperty("total_selling_price")
    private BigDecimal totalSellingPrice;
    @JsonProperty("coupon_discount")
    private BigDecimal couponDiscount;
    private BigDecimal orderTotal;
}
