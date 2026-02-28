package com.sorted.common.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.common.entity.beans.*;
import com.sorted.common.enums.RecommenderType;
import lombok.Data;

import java.util.List;

@Data
public class RegisterRecommendationsBean {
    private RecommenderType type;
    private RecommenderDetails details;
    @JsonProperty("professor_info")
    private ProfessorInfo professorInfo;
    @JsonProperty("student_info")
    private StudentInfo studentInfo;
    @JsonProperty("industry_expert_info")
    private IndustryExpertInfo industryExpertInfo;
    private Consent consent;
    @JsonProperty("recommender_id")
    private String recommenderId;
    private List<Recommendations> recommendations;

}
