package com.sorted.portal.request.beans;

import com.sorted.commons.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class InitiateSecureReturn extends ReqBaseBean {
    private String order_id;

}
