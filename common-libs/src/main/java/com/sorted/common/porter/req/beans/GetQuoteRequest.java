package com.sorted.common.porter.req.beans;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetQuoteRequest {

	private PickupDetails pickup_details;
	private DropDetails drop_details;
	private Customer customer;

	@Data
	@Builder
	public static class PickupDetails {
		private double lat;
		private double lng;
	}

	@Data
	@Builder
	public static class DropDetails {
		private double lat;
		private double lng;
	}

	@Data
	@Builder
	public static class Customer {
		private String name;
		private Mobile mobile;

		@Data
		@Builder
		public static class Mobile {
			private String country_code;
			private String number;
		}
	}
}
