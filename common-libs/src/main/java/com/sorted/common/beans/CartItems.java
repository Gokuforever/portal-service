package com.sorted.common.beans;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItems {
    private String product_name;
    private String product_code;
    private String product_id;
    private BigDecimal selling_price;
    private BigDecimal mrp;
    private Long quantity;
    private boolean secure_item;
    private Integer current_status;
    private String cdn_url;
    private boolean is_combo;
}
