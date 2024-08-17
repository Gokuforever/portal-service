package com.sorted.portal.assisting.beans;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class ProductDetailsBean {

	private String name;
	private String id;
	private String product_code;
	private BigDecimal selling_price;
	private String img_url;
	private String description;
	private List<ProductDetailsBean> varients;
	private List<ProductDetailsBean> related_products;
}
