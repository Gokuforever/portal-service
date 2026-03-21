package com.sorted.portal.response.beans;

import com.sorted.common.beans.ProductMasterBean;
import com.sorted.common.entity.mongo.Category_Master;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MetaData {

    private List<Category_Master> catagories;
    private List<ProductMasterBean> products;
    private LocalDateTime updated_at;
}
