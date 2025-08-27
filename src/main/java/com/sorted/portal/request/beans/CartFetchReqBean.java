package com.sorted.portal.request.beans;

import com.sorted.commons.helper.ReqBaseBean;
import lombok.Data;

@Data
public class CartFetchReqBean extends ReqBaseBean {

    private String address_id;
}
