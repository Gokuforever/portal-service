package com.sorted.portal.request.beans;

import com.sorted.commons.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class VerifyOtpBean extends ReqBaseBean {

    private String otp;
    private String entity_id;
    private String reference_id;
}
