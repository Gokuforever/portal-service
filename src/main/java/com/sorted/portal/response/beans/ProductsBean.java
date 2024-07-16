package com.sorted.portal.response.beans;

import java.util.List;

import com.sorted.portal.assisting.beans.ProductDetailsBean;

import lombok.Data;

@Data
public class ProductsBean {

	private List<ProductDetailsBean> products;
}
