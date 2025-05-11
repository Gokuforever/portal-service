package com.sorted.portal.response.beans;

import com.sorted.portal.assisting.beans.CartItems;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CartBean {

    private BigDecimal total_amount = BigDecimal.ZERO;
    private long total_count;
    private List<CartItems> cart_items;
}
