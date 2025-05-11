package com.sorted.portal.response.beans;

import com.sorted.commons.entity.mongo.Category_Master;
import com.sorted.commons.entity.mongo.Product_Master;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MetaData {

    private List<Category_Master> catagories;
    private List<Product_Master> products;
    private LocalDateTime updated_at;
}
