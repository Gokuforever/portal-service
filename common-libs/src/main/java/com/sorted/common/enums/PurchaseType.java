package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PurchaseType {

	BUY("Direct Purchase"), SECURE("SecuRe Buy");

	private final String description;
}
