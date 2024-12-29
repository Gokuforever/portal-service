package com.sorted.portal.assisting.beans;

import java.math.BigDecimal;
import java.util.List;

import com.sorted.commons.beans.Media;
import com.sorted.commons.beans.SelectedSubCatagories;

import lombok.Builder;
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
	private List<SelectedSubCatagories> selected_sub_catagories;
	private List<ProductDetailsBean> varients;
	private List<ProductDetailsBean> related_products;
	private List<Media> media;
	private String category_id;
	private String category_name;
	private CartDetails cart_info;
	private Boolean secure;
	
	@Builder
	@Data
	public static class CartDetails{
		private long secure_items;
		private long normal_items;
	}
}
