package com.sorted.common.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

@Data
public class EducationCategoryField {
    private String alias;
    private int order;
    private String type;
    private List<String> options;
    private boolean mandatory;
    private String description;
    @JsonIgnore
    private boolean filterable;

}
