package com.sorted.portal.request.beans;

import com.sorted.common.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ChangePassword extends ReqBaseBean {

    private String password;
    private String reference_id;
}
