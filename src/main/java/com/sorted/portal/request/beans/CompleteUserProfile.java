package com.sorted.portal.request.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.commons.beans.EducationCategoryBean;
import com.sorted.commons.enums.Gender;
import com.sorted.commons.helper.ReqBaseBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompleteUserProfile extends ReqBaseBean {

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

    private AuthV2Bean auth;

    @JsonProperty("referral_code")
    private String referralCode;
}
