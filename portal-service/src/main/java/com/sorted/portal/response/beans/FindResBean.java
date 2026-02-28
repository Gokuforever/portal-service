package com.sorted.portal.response.beans;

import lombok.Data;

import java.util.List;

@Data
public class FindResBean {

    private List<?> list;
    private long total_count;
}
