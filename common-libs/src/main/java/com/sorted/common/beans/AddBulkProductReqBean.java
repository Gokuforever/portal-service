package com.sorted.common.beans;

import com.sorted.common.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class AddBulkProductReqBean extends ReqBaseBean {

    private String seller_id;
    private List<ProductReqBean> products;
}
