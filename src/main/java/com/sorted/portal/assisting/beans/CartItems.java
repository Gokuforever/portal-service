package com.sorted.portal.assisting.beans;

import lombok.Data;

@Data
public class CartItems {

	private String product_name;
	private String product_code;
	private Long selling_price;
	private Long quantity;
	private boolean in_stock;
}
