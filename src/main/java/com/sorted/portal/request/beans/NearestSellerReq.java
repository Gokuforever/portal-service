package com.sorted.portal.request.beans;

import java.math.BigDecimal;

import com.sorted.commons.helper.ReqBaseBean;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NearestSellerReq extends ReqBaseBean {

	private String pincode;
	private BigDecimal lat;
	private BigDecimal lng;
	private String branch;
	private String desc;
	private String semester;
}
