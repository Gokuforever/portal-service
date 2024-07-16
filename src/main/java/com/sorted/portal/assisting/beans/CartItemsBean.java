package com.sorted.portal.assisting.beans;

import lombok.Data;

@Data
public class CartItemsBean {

	private String product_id;
	private Integer quantity;
	private boolean add;
}
