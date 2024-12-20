package com.sorted.portal.request.beans;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.util.MultiValueMap;

import com.sorted.commons.helper.ReqBaseBean;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FindProductBean extends ReqBaseBean {

	private String id;
	private String catagory_id;
	private Map<String, List<String>> filters;
	private String name;
	private String pincode;
	private String nearest_seller;

	public void creatObj(MultiValueMap<String, List<List<String>>> filters, String name, int page, int size) {
		if (filters != null) {
			Map<String, List<Object>> map = new HashMap<>();
			filters.forEach((key, value) -> {
				Object[] array = value.toArray();
				map.put(key, Arrays.asList(array));
			});
			this.filters = map.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(),
					e -> e.getValue().stream().map(Object::toString).collect(Collectors.toList())));
		}
		this.name = name;
		this.page = page;
		this.size = size;
	}

}
