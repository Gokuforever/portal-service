package com.sorted.portal.request.beans;

import com.sorted.commons.enums.ProcessType;

import lombok.Data;

@Data
public class ResendOtpBean {

	private ProcessType process_type;
	private String reference_id;
	private String entity_id;
}
