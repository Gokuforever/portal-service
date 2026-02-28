package com.sorted.portal.enums;

import com.sorted.portal.response.beans.OrderReportDTO;
import com.sorted.portal.service.ExportFieldMapping;

public enum OrderProperties implements ExportFieldMapping<OrderReportDTO> {
    ID("Order Id", "code"),
    STATUS("Status", "status"),
    CREATION_DATE("Order Date", "creation_date");

    private final String headerName;
    private final String propertyName;

    OrderProperties(String headerName, String propertyName) {
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
