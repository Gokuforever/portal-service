package com.sorted.common.beans;

import com.sorted.common.enums.ColumnType;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ColumnConfig {
    // Getters and setters
    private String header;
    private ColumnType type;
    private String cssClass;

    public ColumnConfig(String header, ColumnType type) {
        this.header = header;
        this.type = type;
    }

    public ColumnConfig(String header, ColumnType type, String cssClass) {
        this.header = header;
        this.type = type;
        this.cssClass = cssClass;
    }

}
