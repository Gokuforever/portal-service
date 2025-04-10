package com.sorted.portal.enums;

import com.sorted.portal.response.beans.OrderItemReportsDTO;
import com.sorted.portal.service.ExportFieldMapping;

public enum OrderItemsProperties implements ExportFieldMapping<OrderItemReportsDTO> {
   ORDER_CODE("Order Id","order_code"),
   PRODUCT_CODE("Product Id","product_code"),
   QUANTITY("Quantity","quantity"),
   STATUS("Status","status");

    private final String headerName;
    private final String propertyName;

    OrderItemsProperties(String headerName, String propertyName) {
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
