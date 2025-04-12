package com.sorted.portal.enums;

import com.sorted.portal.assisting.beans.ProductDetailsBean;
import com.sorted.portal.service.ExportFieldMapping;

public enum ProductProperties implements ExportFieldMapping<ProductDetailsBean> {

    ID("Product ID", "code");

    private final String headerName;
    private final String propertyName;

    ProductProperties(String headerName, String propertyName) {
        this.headerName = headerName;
        this.propertyName = propertyName;
    }


    @Override
    public String getHeaderName() {
        return this.headerName;
    }

    @Override
    public String getPropertyName() {
        return this.propertyName;
    }
}
