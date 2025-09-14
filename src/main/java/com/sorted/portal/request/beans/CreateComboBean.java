package com.sorted.portal.request.beans;

import com.sorted.commons.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class CreateComboBean extends ReqBaseBean {

    private String name;
    private String description;
    private BigDecimal price;
    private List<String> item_ids;
}
