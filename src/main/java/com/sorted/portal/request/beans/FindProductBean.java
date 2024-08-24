package com.sorted.portal.request.beans;

import java.util.List;
import java.util.Map;

import com.sorted.commons.helper.ReqBaseBean;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class FindProductBean extends ReqBaseBean {

	private String id;
	private String catagory_id;
	private Map<String, List<String>> filters;
	private String name;
	private String pincode;
}
