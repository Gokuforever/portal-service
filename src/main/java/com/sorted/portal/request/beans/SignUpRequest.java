package com.sorted.portal.request.beans;

import com.sorted.commons.enums.Gender;
import com.sorted.commons.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class SignUpRequest extends ReqBaseBean {

    private String first_name;
    private String last_name;
    private String mobile_no;
    private String email_id;
    private String password;
    private String branch;
    private String desc;
    private String semester;
    private String college;
    private Gender gender;
}
