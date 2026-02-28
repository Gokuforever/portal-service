package com.sorted.portal.request.beans;

import com.sorted.common.entity.beans.*;
import com.sorted.common.enums.RecommenderType;
import lombok.Data;

@Data
public class RegisterRecommendationsBean {
    private RecommenderType type;
    private RecommenderDetails details;
    private ProfessorInfo professorInfo;
    private StudentInfo studentInfo;
    private IndustryExpertInfo industryExpertInfo;
    private Consent consent;

}
