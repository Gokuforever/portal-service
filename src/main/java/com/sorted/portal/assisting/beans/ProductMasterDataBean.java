package com.sorted.portal.assisting.beans;

import com.sorted.commons.entity.mongo.Product_Master;
import lombok.Data;

import java.util.List;

@Data
public class ProductMasterDataBean {
    private List<Product_Master> data;
}
