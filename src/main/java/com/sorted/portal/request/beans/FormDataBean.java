package com.sorted.portal.request.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.commons.beans.EducationCategoryBean;
import com.sorted.commons.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class FormDataBean extends ReqBaseBean {
    private String pincode;
    @JsonProperty("education_details")
    private EducationCategoryBean educationDetails;
    private String college;
}
