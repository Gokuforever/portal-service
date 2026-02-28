package com.sorted.common.beans;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
public class Spoc_Details {
	private String first_name;
	private String last_name;
	private String mobile_no;
	private String email_id;
	private String designation;
	private boolean primary;
}
