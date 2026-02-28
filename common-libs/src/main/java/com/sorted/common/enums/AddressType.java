package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;

@Getter
@AllArgsConstructor
public enum AddressType {

	HOME(1, "Home"), HOSTEL(2, "Hostel"), STORE(3, "Store"), OTHER(10, "Other");

	private final int id;
	private final String type;
	
	private static final Map<String, AddressType> ByName = new HashMap<>();
	static {
		for(AddressType a: values()) {
			ByName.put(a.name(), a);
		}
	}
	
	public static AddressType getByName(@NonNull String s) {
		try {
			return ByName.get(s.toUpperCase());
		} catch (Exception e) {
			return null;
		}
	}
}
