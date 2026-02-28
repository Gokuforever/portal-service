package com.sorted.common.porter.req.beans;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@Builder
public class CreateOrderBean {

	private String request_id;
	private Instruction_List delivery_instructions;
	private Pickup_Details pickup_details;
	private Drop_Details drop_details;

	@Data
	@Builder
	public static class Instruction_List {
		List<Delivery_Instructions> instructions_list;
	}

	@Data
	@Builder
	public static class Pickup_Details {
		private Address address;
	}

	@Data
	@Builder
	public static class Drop_Details {
		private Address address;
	}

	@Data
	@Builder
	public static class Delivery_Instructions {
		private String type;
		private String description;
	}

	@Data
	@Builder
	public static class Address {
		private String apartment_address;
		private String street_address1;
		private String street_address2;
		private String landmark;
		private String city;
		private String state;
		private String pincode;
		private String country;
		private BigDecimal lat;
		private BigDecimal lng;
		private Contact_Details contact_details;
	}

	@Data
	@Builder
	public static class Contact_Details {
		private String name;
		private String phone_number;
	}

}
