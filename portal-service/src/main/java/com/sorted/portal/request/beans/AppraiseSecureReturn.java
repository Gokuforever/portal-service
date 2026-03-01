package com.sorted.portal.request.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.common.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;


@Data
@EqualsAndHashCode(callSuper = false)
public class AppraiseSecureReturn extends ReqBaseBean {

    @JsonProperty("secure_return_id")
    private String secureReturnId;
    private List<SecureItemAppraisalDetails> items;
    private String remark;
}
