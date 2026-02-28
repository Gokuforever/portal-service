package com.sorted.common.beans;

import com.sorted.common.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class PayNowBean extends ReqBaseBean {

    private String delivery_address_id;
    private List<String> selected_products;
    private String return_date;
}