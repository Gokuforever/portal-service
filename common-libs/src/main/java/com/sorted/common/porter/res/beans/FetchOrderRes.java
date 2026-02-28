package com.sorted.common.porter.res.beans;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
public class FetchOrderRes implements Serializable {

	/**
	 * 
	 */
	@Serial
	private static final long serialVersionUID = 1L;
	private String order_id;
	private Status status;
	private PartnerInfo partner_info;
	private OrderTimings order_timings;
	private FareDetails fare_details;
	private String trackingLink;

	@Getter
	@AllArgsConstructor
	public enum Status {
		open, accepted, live, ended, cancelled, completed
	}

	@Data
	@Builder
	public static class PartnerInfo implements Serializable {
		/**
		 * 
		 */
		@Serial
		private static final long serialVersionUID = 1L;
		private String name;
		private String vehicle_number;
		private String vehicle_type;
		private MobileNo mobile;
		private MobileNo partner_secondary_mobile;
		private Location location;

	}

	@Data
	@Builder
	public static class Location implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		private String lat;
		private String lng;
	}

	@Data
	@Builder
	public static class MobileNo implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		private String country_code;
		private String mobile_number;
	}

	@Data
	@Builder
	public static class OrderTimings implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		private Long pickup_time;
		private Long order_accepted_time;
		private Long order_started_time;
		private Long order_ended_time;
	}

	@Data
	@Builder
	public static class FareDetails implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		private FareAmountDetails estimated_fare_details;
		private FareAmountDetails actual_fare_details;

		@Data
		@Builder
		public static class FareAmountDetails implements Serializable {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;
			private String currency;
			private Long minor_amount;
		}

	}

}
