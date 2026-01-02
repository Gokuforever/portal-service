package com.sorted.portal.request.beans;

import com.sorted.commons.entity.beans.*;
import com.sorted.commons.enums.RecommenderType;
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
