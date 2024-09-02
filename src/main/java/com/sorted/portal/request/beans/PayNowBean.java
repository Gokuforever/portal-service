package com.sorted.portal.request.beans;

import java.util.List;

import com.sorted.commons.helper.ReqBaseBean;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class PayNowBean extends ReqBaseBean {

	private String delivery_address_id;
	private List<String> selected_products;
}
