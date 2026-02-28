package com.sorted.common.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CartBean {

    private BigDecimal total_amount;
    private BigDecimal delivery_charge;
    private BigDecimal item_total;
    private BigDecimal item_total_mrp;
    private boolean is_free_delivery;
    private long total_count;
    private List<CartItems> cart_items;
    private boolean isStoreOperational;
    @JsonProperty("coupon_code")
    private String couponCode;
    @JsonProperty("discount_amount")
    private BigDecimal discountAmount;
    private BigDecimal savings;
}
