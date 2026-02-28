package com.sorted.common.porter.res.beans;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GetQuoteResponse {

	private Vehicle vehicle;

	@Data
	@Builder
	public static class Vehicle {
		private String type;
		private Eta eta; // ETA is null in the response but keeping it for future handling
		private Fare fare;
		private Capacity capacity;
		private Size size;

		@Data
		@Builder
		public static class Eta {
			private long value;
			private String unit;
		}

		@Data
		@Builder
		public static class Fare {
			private String currency;
			private long minor_amount;
		}

		@Data
		@Builder
		public static class Capacity {
			private double value;
			private String unit;
		}

		@Data
		@Builder
		public static class Size {
			private Dimension length;
			private Dimension breadth;
			private Dimension height;

			@Data
			@Builder
			public static class Dimension {
				private double value;
				private String unit;
			}
		}
	}
}
