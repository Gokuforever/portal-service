package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum InputType {
	SELECT(1), RADIO_BUTTON(2), INPUT_FIELD(3), CHECK_BOX(4), MULTI_SELECT(5);

	private final int id;
}