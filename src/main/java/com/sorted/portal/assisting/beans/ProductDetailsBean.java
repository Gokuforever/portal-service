package com.sorted.portal.assisting.beans;

import java.math.BigDecimal;
import java.util.List;

import com.sorted.commons.beans.Media;

import lombok.Data;

@Data
public class ProductDetailsBean {

	private String name;
	private String id;
	private String product_code;
	private BigDecimal mrp;
	private BigDecimal selling_price;
	private String description;
	private int quantity;
	private List<ProductDetailsBean> varients;
	private List<ProductDetailsBean> related_products;
	private List<Media> media;
	private boolean is_deliverable;
}
