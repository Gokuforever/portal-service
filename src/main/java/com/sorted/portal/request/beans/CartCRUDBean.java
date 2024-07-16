package com.sorted.portal.request.beans;

import java.util.List;

import com.sorted.portal.assisting.beans.CartItemsBean;

import lombok.Data;

@Data
public class CartCRUDBean {

	private List<CartItemsBean> items;
}
