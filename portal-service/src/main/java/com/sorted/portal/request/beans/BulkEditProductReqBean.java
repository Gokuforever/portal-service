package com.sorted.portal.request.beans;

import com.sorted.common.beans.ProductReqBean;
import com.sorted.common.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class BulkEditProductReqBean extends ReqBaseBean {

    private String seller_id;
    private List<ProductReqBean> products;
}