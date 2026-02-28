package com.sorted.common.utils;

import com.sorted.common.beans.ColumnConfig;
import com.sorted.common.beans.InfoSection;
import com.sorted.common.beans.TableConfig;
import com.sorted.common.beans.TableConfigBuilder;
import com.sorted.common.enums.ColumnType;

import java.util.Map;

public class TemplateProcessorUtil {

    public static String replacePlaceholders(String template, String content, Map<String, TableConfig> tableConfigs) {
        for (Map.Entry<String, TableConfig> entry : tableConfigs.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String tableHtml = buildTableHtml(content, entry.getValue());
            template = template.replace(placeholder, tableHtml);
        }
        return template;
    }

    private static String buildTableHtml(String content, TableConfig config) {
        String[] values = content.split("\\|");

        StringBuilder html = new StringBuilder();

        // Add info sections
        for (InfoSection info : config.getInfoSections()) {
            if (info.getIndex() < values.length) {
                html.append("<div class=\"").append(info.getCssClass()).append("\">")
                        .append("<strong>").append(info.getLabel()).append(":</strong> ")
                        .append(values[info.getIndex()])
                        .append("</div>");
            }
        }

        // Table start
        html.append("<table class=\"").append(config.getTableCssClass()).append("\">");

        // Table header
        html.append("<thead><tr>");
        for (ColumnConfig column : config.getColumns()) {
            html.append("<th>").append(column.getHeader()).append("</th>");
        }
        html.append("</tr></thead>");

        // Table body
        html.append("<tbody>");
        int columnDataCount = (int) config.getColumns().stream().filter(c -> c.getType() == ColumnType.DATA).count();

        int dataIndex = config.getDataStartIndex();
        int rowCount = 0;
        while (dataIndex + columnDataCount - 1 < values.length) {
            html.append("<tr>");
            int dataColIndex = 0;
            for (ColumnConfig column : config.getColumns()) {
                html.append("<td>");
                if (column.getType() == ColumnType.SERIAL_NUMBER) {
                    html.append(++rowCount);
                } else if (column.getType() == ColumnType.DATA) {
                    html.append(values[dataIndex + dataColIndex]);
                    dataColIndex++;
                }
                html.append("</td>");
            }
            dataIndex += columnDataCount;
            html.append("</tr>");
        }

        if (rowCount == 0) {
            html.append("<tr><td colspan=\"").append(config.getColumns().size()).append("\">")
                    .append(config.getNoDataMessage())
                    .append("</td></tr>");
        }

        html.append("</tbody></table>");
        return html.toString();
    }

    public static TableConfigBuilder createTableConfig(int dataStartIndex) {
        return new TableConfigBuilder(dataStartIndex);
    }
}
