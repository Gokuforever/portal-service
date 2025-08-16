package com.sorted.portal.assisting.beans.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class GroupComponentBean {
    private int groupId;
    private String title;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, List<String>> filters;
    private List<ProductBean> products;
}
