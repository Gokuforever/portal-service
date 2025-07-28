package com.sorted.portal.request.beans;

import com.sorted.commons.helper.ReqBaseBean;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class CreateComboBean extends ReqBaseBean {

    private String name;
    private String description;
    private Long price;
    private List<String> item_ids;
}
