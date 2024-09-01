package com.sorted.portal.response.beans;

import java.util.List;

import lombok.Data;

@Data
public class FindResBean {

	private List<?> list;
	private long total_count;
}
