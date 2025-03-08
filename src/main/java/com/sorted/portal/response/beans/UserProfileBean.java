package com.sorted.portal.response.beans;

import java.util.Map;

import com.sorted.commons.enums.Gender;
import com.sorted.commons.enums.UserType;
import lombok.Data;

@Data
public class UserProfileBean {

	private String first_name;
	private String last_name;
	private String mobile_no;
	private String email_id;
	private String status;
	private Map<String, String> properties;
	private Gender gender;
	private UserType user_type;
	private int user_type_id;

}
