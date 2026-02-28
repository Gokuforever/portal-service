package com.sorted.common.beans;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

// Configuration classes remain the same but now with builder pattern support
@Setter
@Getter
public class TableConfig {
    // Getters and setters
    private List<InfoSection> infoSections = new ArrayList<>();
    private List<ColumnConfig> columns = new ArrayList<>();
    private int dataStartIndex;
    private String tableCssClass = "generic-table";
    private String noDataMessage = "No data available";

    public TableConfig() {}

    public TableConfig(int dataStartIndex) {
        this.dataStartIndex = dataStartIndex;
    }

    public void addInfoSection(InfoSection infoSection) { this.infoSections.add(infoSection); }

    public void addColumn(ColumnConfig column) { this.columns.add(column); }

}
