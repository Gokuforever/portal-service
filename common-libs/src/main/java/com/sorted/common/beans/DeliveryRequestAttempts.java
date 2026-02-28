package com.sorted.common.beans;

import lombok.Builder;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Builder
public class DeliveryRequestAttempts implements Serializable {

	/**
	 * 
	 */
	@Serial
	private static final long serialVersionUID = 1L;
	private String type;
	private String message;
	private int count;
	private int response_code;

}
