package com.sorted.portal.response.beans;

import com.sorted.commons.beans.AddressDTO;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class FndOrderResBean {

	private String id;
	private String code;
	private Long total_amount;
	private String status;
	private String transaction_id;
	private AddressDTO delivery_address;
	private AddressDTO pickup_address;

}
