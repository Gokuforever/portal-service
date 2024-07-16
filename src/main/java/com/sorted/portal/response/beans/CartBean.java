package com.sorted.portal.response.beans;

import java.math.BigDecimal;
import java.util.List;

import com.sorted.portal.assisting.beans.CartItems;

import lombok.Data;

@Data
public class CartBean {

	private BigDecimal total_amount = BigDecimal.ZERO;
	private List<CartItems> cart_items;
}
