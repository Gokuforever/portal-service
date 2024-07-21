package com.sorted.portal.request.beans;

import lombok.Data;

@Data
public class ForgotPassBean {

	private String mobile_no;
	private String password;
	private String entity_id;
	private String reference_id;
}
