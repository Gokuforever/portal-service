package com.sorted.common.beans;

import com.sorted.common.enums.ColumnType;

/**
 * Builder class for easier TableConfig creation
 */
public class TableConfigBuilder {
    private final TableConfig config;

    public TableConfigBuilder(int dataStartIndex) {
        this.config = new TableConfig(dataStartIndex);
    }

    public TableConfigBuilder withTableCssClass(String cssClass) {
        config.setTableCssClass(cssClass);
        return this;
    }

    public TableConfigBuilder withNoDataMessage(String message) {
        config.setNoDataMessage(message);
        return this;
    }

    public TableConfigBuilder addInfoSection(String label, int index) {
        config.addInfoSection(new InfoSection(label, index));
        return this;
    }

    public TableConfigBuilder addInfoSection(String label, int index, String cssClass) {
        config.addInfoSection(new InfoSection(label, index, cssClass));
        return this;
    }

    public TableConfigBuilder addColumn(String header, ColumnType type) {
        config.addColumn(new ColumnConfig(header, type));
        return this;
    }

    public TableConfigBuilder addColumn(String header, ColumnType type, String cssClass) {
        config.addColumn(new ColumnConfig(header, type, cssClass));
        return this;
    }

    public TableConfig build() {
        return config;
    }
}
