package com.sorted.portal.assisting.beans.config;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GroupComponentBean {
    private int groupId;
    private String title;
    private List<ProductBean> products;
}
