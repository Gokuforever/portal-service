package com.sorted.common.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Utility class for exporting data to Excel and CSV formats.
 * Provides methods for both file storage and direct data return.
 */
public class FileExportUtils {

    private FileExportUtils() {
        // Private constructor to prevent instantiation
    }

    /**
     * Exports a list of objects to an Excel file.
     * Each object represents a row of data with its properties as columns.
     *
     * @param <T>        Type of objects in the list
     * @param data       List of objects to export
     * @param headers    List of column headers
     * @param properties List of property names to extract from each object (must match headers size)
     * @param filePath   Path where the Excel file will be saved
     * @param sheetName  Name of the sheet in the Excel file
     * @throws IOException         If an I/O error occurs
     * @throws ReflectionException If reflection errors occur when accessing object properties
     */
    public static <T> void exportToExcel(List<T> data, List<String> headers, List<String> properties,
                                         String filePath, String sheetName) throws IOException, ReflectionException {
        try (FileOutputStream outputStream = new FileOutputStream(filePath)) {
            byte[] excelBytes = generateExcelBytes(data, headers, properties, sheetName);
            outputStream.write(excelBytes);
        }
    }

    /**
     * Generates Excel data as byte array from a list of objects without storing it to a file.
     *
     * @param <T>        Type of objects in the list
     * @param data       List of objects to export
     * @param headers    List of column headers
     * @param properties List of property names to extract from each object (must match headers size)
     * @param sheetName  Name of the sheet in the Excel file
     * @return Byte array containing the Excel file data
     * @throws IOException         If an I/O error occurs
     * @throws ReflectionException If reflection errors occur when accessing object properties
     */
    public static <T> byte[] generateExcelBytes(List<T> data, List<String> headers, List<String> properties,
                                                String sheetName) throws IOException, ReflectionException {
        if (headers.size() != properties.size()) {
            throw new IllegalArgumentException("Headers and properties lists must have the same size");
        }

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(sheetName);

            // Create header row
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);

            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // Create data rows
            for (int rowIdx = 0; rowIdx < data.size(); rowIdx++) {
                Row row = sheet.createRow(rowIdx + 1);
                T rowObject = data.get(rowIdx);

                for (int colIdx = 0; colIdx < properties.size(); colIdx++) {
                    Cell cell = row.createCell(colIdx);
                    Object value = getPropertyValue(rowObject, properties.get(colIdx));
                    setCellValue(cell, value);
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Exports a list of objects to a CSV file.
     *
     * @param <T>        Type of objects in the list
     * @param data       List of objects to export
     * @param headers    List of column headers
     * @param properties List of property names to extract from each object (must match headers size)
     * @param filePath   Path where the CSV file will be saved
     * @param delimiter  Character used to separate values
     * @throws IOException         If an I/O error occurs
     * @throws ReflectionException If reflection errors occur when accessing object properties
     */
    public static <T> void exportToCsv(List<T> data, List<String> headers, List<String> properties,
                                       String filePath, char delimiter) throws IOException, ReflectionException {
        try (FileWriter writer = new FileWriter(filePath)) {
            String csvContent = generateCsvString(data, headers, properties, delimiter);
            writer.write(csvContent);
        }
    }

    /**
     * Overloaded method for CSV export with comma as default delimiter.
     */
    public static <T> void exportToCsv(List<T> data, List<String> headers, List<String> properties,
                                       String filePath) throws IOException, ReflectionException {
        exportToCsv(data, headers, properties, filePath, ',');
    }

    /**
     * Generates CSV data as a string from a list of objects without storing it to a file.
     *
     * @param <T>        Type of objects in the list
     * @param data       List of objects to export
     * @param headers    List of column headers
     * @param properties List of property names to extract from each object (must match headers size)
     * @param delimiter  Character used to separate values
     * @return String containing the CSV data
     * @throws IOException         If an I/O error occurs
     * @throws ReflectionException If reflection errors occur when accessing object properties
     */
    public static <T> String generateCsvString(List<T> data, List<String> headers, List<String> properties,
                                               char delimiter) throws IOException, ReflectionException {
        if (headers.size() != properties.size()) {
            throw new IllegalArgumentException("Headers and properties lists must have the same size");
        }

        try (StringWriter writer = new StringWriter()) {
            // Write header row
            writer.write(String.join(String.valueOf(delimiter), headers));
            writer.write("\n");

            // Write data rows
            for (T rowObject : data) {
                StringBuilder line = new StringBuilder();

                for (int i = 0; i < properties.size(); i++) {
                    if (i > 0) {
                        line.append(delimiter);
                    }

                    Object value = getPropertyValue(rowObject, properties.get(i));
                    String stringValue = convertToString(value);

                    // Escape values containing delimiter or newlines
                    if (stringValue.contains(String.valueOf(delimiter)) || stringValue.contains("\n") || stringValue.contains("\"")) {
                        stringValue = "\"" + stringValue.replace("\"", "\"\"") + "\"";
                    }

                    line.append(stringValue);
                }

                writer.write(line.toString());
                writer.write("\n");
            }

            return writer.toString();
        }
    }

    /**
     * Overloaded method for generating CSV string with comma as default delimiter.
     */
    public static <T> String generateCsvString(List<T> data, List<String> headers, List<String> properties)
            throws IOException, ReflectionException {
        return generateCsvString(data, headers, properties, ',');
    }

    /**
     * Helper method to get a property value from an object using reflection.
     * Tries first using a getter method, then direct field access.
     *
     * @param obj      The object to get the property from
     * @param property The name of the property to get
     * @return The property value
     * @throws ReflectionException If reflection errors occur
     */
    private static Object getPropertyValue(Object obj, String property) throws ReflectionException {
        try {
            // Try to use getter method first
            String getterName = "get" + property.substring(0, 1).toUpperCase() + property.substring(1);
            try {
                Method getter = obj.getClass().getMethod(getterName);
                return getter.invoke(obj);
            } catch (NoSuchMethodException e) {
                // No getter found, try direct field access
                try {
                    Field field = obj.getClass().getDeclaredField(property);
                    field.setAccessible(true);
                    return field.get(obj);
                } catch (NoSuchFieldException ex) {
                    throw new ReflectionException("Cannot find property " + property + " in class " + obj.getClass().getName());
                }
            }
        } catch (Exception e) {
            if (e instanceof ReflectionException) {
                throw (ReflectionException) e;
            }
            throw new ReflectionException("Error accessing property " + property, e);
        }
    }

    /**
     * Sets the cell value based on the object type.
     *
     * @param cell  The cell to set the value for
     * @param value The value to set
     */
    private static void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof String) {
            cell.setCellValue((String) value);
        } else if (value instanceof Integer) {
            cell.setCellValue((Integer) value);
        } else if (value instanceof Long) {
            cell.setCellValue((Long) value);
        } else if (value instanceof Double) {
            cell.setCellValue((Double) value);
        } else if (value instanceof Float) {
            cell.setCellValue((Float) value);
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof java.util.Date) {
            cell.setCellValue((java.util.Date) value);

            CellStyle dateStyle = cell.getSheet().getWorkbook().createCellStyle();
            CreationHelper createHelper = cell.getSheet().getWorkbook().getCreationHelper();
            dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd"));
            cell.setCellStyle(dateStyle);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    /**
     * Creates a cell style for headers.
     *
     * @param workbook The workbook to create the style in
     * @return A CellStyle for headers
     */
    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        return headerStyle;
    }

    /**
     * Converts an object to a string representation suitable for CSV.
     *
     * @param value The object to convert
     * @return String representation
     */
    private static String convertToString(Object value) {
        if (value == null) {
            return "";
        } else if (value instanceof java.util.Date date) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd").format(date);
        } else {
            return value.toString();
        }
    }

    /**
     * Custom exception for reflection-related errors.
     */
    public static class ReflectionException extends Exception {
        public ReflectionException(String message) {
            super(message);
        }

        public ReflectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Keep the original methods for backward compatibility
     */

    public static void exportToExcel(List<String> headers, List<List<Object>> data, String filePath, String sheetName) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(filePath)) {
            byte[] excelBytes = generateExcelBytesLegacy(headers, data, sheetName);
            outputStream.write(excelBytes);
        }
    }

    public static byte[] generateExcelBytesLegacy(List<String> headers, List<List<Object>> data, String sheetName) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(sheetName);

            // Create header row
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);

            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // Create data rows
            for (int rowIdx = 0; rowIdx < data.size(); rowIdx++) {
                Row row = sheet.createRow(rowIdx + 1);
                List<Object> rowData = data.get(rowIdx);

                for (int colIdx = 0; colIdx < rowData.size(); colIdx++) {
                    Cell cell = row.createCell(colIdx);
                    setCellValue(cell, rowData.get(colIdx));
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    public static void exportToCsv(List<String> headers, List<List<Object>> data, String filePath, char delimiter) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            String csvContent = generateCsvStringLegacy(headers, data, delimiter);
            writer.write(csvContent);
        }
    }

    public static void exportToCsv(List<String> headers, List<List<Object>> data, String filePath) throws IOException {
        exportToCsv(headers, data, filePath, ',');
    }

    public static String generateCsvStringLegacy(List<String> headers, List<List<Object>> data, char delimiter) throws IOException {
        try (StringWriter writer = new StringWriter()) {
            // Write header row
            writer.write(String.join(String.valueOf(delimiter), headers));
            writer.write("\n");

            // Write data rows
            for (List<Object> row : data) {
                StringBuilder line = new StringBuilder();

                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) {
                        line.append(delimiter);
                    }

                    String value = convertToString(row.get(i));

                    // Escape values containing delimiter or newlines
                    if (value.contains(String.valueOf(delimiter)) || value.contains("\n") || value.contains("\"")) {
                        value = "\"" + value.replace("\"", "\"\"") + "\"";
                    }

                    line.append(value);
                }

                writer.write(line.toString());
                writer.write("\n");
            }

            return writer.toString();
        }
    }

    public static String generateCsvStringLegacy(List<String> headers, List<List<Object>> data) throws IOException {
        return generateCsvStringLegacy(headers, data, ',');
    }
}