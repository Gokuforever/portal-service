package com.sorted.common.helper;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Pagination {

	private int page = 0;
	private int size = 21;

	// Parameterized constructor
	public Pagination(int page, int size) {
		this.page = (page >= 0) ? page : this.page;
		this.size = (size > 0) ? size : this.size;
	}
}
