package com.sorted.portal.response.beans;

import com.sorted.portal.assisting.beans.ProductDetailsBean;
import lombok.Data;

import java.util.List;

@Data
public class ProductsBean {

    private List<ProductDetailsBean> products;
}
