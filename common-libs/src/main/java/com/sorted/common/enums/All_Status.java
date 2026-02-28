package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class All_Status {

	@Getter
	@AllArgsConstructor
	public enum Role_Status {
		ACTIVE(1), INACTIVE(2), BLOCK(3);

		private final int id;
	}

	@Getter
	@AllArgsConstructor
	public enum User_Status {
		ACTIVE(1), INACTIVE(2), BLOCK(3);

		private final int id;
	}

	@Getter
	@AllArgsConstructor
	public enum Seller_Status {
		VERIFICATION_PENDING(1, "Verification Pending"), ACTIVE(2, "Active"), INACTIVE(3, "Inactive"),
		BLOCKED(4, "Blocked");

		private final int id;
		private final String status;

		private static final Map<Integer, Seller_Status> valMap = new HashMap<>();

		static {
			for (Seller_Status s : values()) {
				valMap.put(s.getId(), s);
			}
		}

		public static Seller_Status getById(int id) {
			try {
				return valMap.get(id);
			} catch (Exception e) {
				return null;
			}
		}
	}

	@Getter
	@AllArgsConstructor
	public enum ProductCurrentStatus {
		IN_STOCK(1, "In stock"), OUT_OF_STOCK(2, "Out of stock"), CURRENTLY_UNAVAILABLE(3, "Currently unawailable");

		private final int status_id;
		private final String status;
	}
}
