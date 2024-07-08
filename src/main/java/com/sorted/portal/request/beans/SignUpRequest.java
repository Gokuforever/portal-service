package com.sorted.portal.request.beans;

import lombok.Data;

@Data
public class SignUpRequest {

	private String first_name;
	private String last_name;
	private String mobile_no;
	private String email_id;
	private String password;
}
