package com.sorted.portal.request.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.commons.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.util.MultiValueMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@EqualsAndHashCode(callSuper = true)
public class FindProductBean extends ReqBaseBean {

    private String id;
    private String category_id;
    private Integer group_id;
    private Map<String, List<String>> filters;
    private String name;
    private String pincode;
    private String nearest_seller;
    private String sort_by;
    private long count;
    @JsonProperty
    private boolean fetchRandom;

    public void creatObj(MultiValueMap<String, List<List<String>>> filters, String name, int page, int size) {
        if (filters != null) {
            Map<String, List<Object>> map = new HashMap<>();
            filters.forEach((key, value) -> {
                Object[] array = value.toArray();
                map.put(key, Arrays.asList(array));
            });
            this.filters = map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                    e -> e.getValue().stream().map(Object::toString).collect(Collectors.toList())));
        }
        this.name = name;
        this.page = page;
        this.size = size;
    }

}
