package com.sorted.portal.assisting.beans;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class CartItems {

	private String product_name;
	private String product_code;
	private BigDecimal selling_price;
	private Long quantity;
}
