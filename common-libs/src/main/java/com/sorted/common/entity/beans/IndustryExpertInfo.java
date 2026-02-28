package com.sorted.common.entity.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Builder
public class IndustryExpertInfo {

    private String company;
    @Field("job_title")
    @JsonProperty("job_title")
    private String jobTitle;
    private int yoe;
    @Field("expertise_areas")
    @JsonProperty("expertise_areas")
    private List<String> expertiseAreas;
}
