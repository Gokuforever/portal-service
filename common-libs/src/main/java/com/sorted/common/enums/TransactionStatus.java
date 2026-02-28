package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum TransactionStatus {

	PENDING(1), PAID(2), FAILED(3), CANCELLED(4);

	private final int id;

}
