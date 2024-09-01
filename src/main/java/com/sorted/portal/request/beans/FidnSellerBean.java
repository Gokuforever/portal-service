package com.sorted.portal.request.beans;

import com.sorted.commons.helper.ReqBaseBean;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class FidnSellerBean extends ReqBaseBean {

	private String name;
}
