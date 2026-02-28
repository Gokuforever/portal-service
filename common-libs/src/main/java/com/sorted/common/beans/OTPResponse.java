package com.sorted.common.beans;

import lombok.Data;

@Data
public class OTPResponse {

	private String reference_id;
	private String entity_id;
	private String process_type;
}
