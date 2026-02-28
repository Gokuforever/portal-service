package com.sorted.common.entity.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.common.beans.EducationCategoryBean;
import com.sorted.common.enums.Gender;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class AmbassadorDetails {

    @JsonProperty("first_name")
    private String firstname;

    @JsonProperty("last_name")
    private String lastname;

    @JsonProperty("mobile_no")
    private String mobile;

    @JsonProperty("email_id")
    private String email;

    private Gender gender;

    @JsonProperty("education_details")
    private EducationCategoryBean educationDetails;
}
