package com.sorted.portal.request.beans;

import com.sorted.commons.helper.ReqBaseBean;
import com.sorted.portal.assisting.beans.CartItemsBean;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=false)
public class CartCRUDBean extends ReqBaseBean{

	private CartItemsBean item;
}
