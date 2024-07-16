package com.sorted.portal.request.beans;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class FindProductBean {

	private String id;
	private Map<String, List<String>> filters;
}
