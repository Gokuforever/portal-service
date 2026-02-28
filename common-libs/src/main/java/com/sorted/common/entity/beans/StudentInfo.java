package com.sorted.common.entity.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import org.springframework.data.mongodb.core.mapping.Field;

@Builder
public class StudentInfo {

    private String degree;
    private String college;
    @Field("current_year")
    @JsonProperty("current_year")
    private String currentYear;
    @Field("graduation_year")
    @JsonProperty("graduation_year")
    private String graduationYear;
    private String cgpa;
}
