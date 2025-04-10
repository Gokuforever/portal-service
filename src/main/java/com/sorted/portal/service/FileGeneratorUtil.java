package com.sorted.portal.service;

import lombok.Getter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utility class for generating Excel and CSV files from lists of objects
 * with custom header mappings defined by enums implementing ExportFieldMapping.
 * <p>
 * This class supports:
 * <ul>
 *   <li>Multi-sheet Excel workbooks</li>
 *   <li>Multiple CSV files (one per logical sheet)</li>
 *   <li>Custom field-to-header mapping</li>
 *   <li>Automatic resizing of Excel columns</li>
 * </ul>
 *
 * @author Sorted Portal Team
 * @since 1.0
 */
public final class FileGeneratorUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileGeneratorUtil.class);

    // Private constructor to prevent instantiation of utility class
    private FileGeneratorUtil() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    /**
     * Creates an Excel file with multiple sheets based on the provided data and column mappings.
     *
     * @param <T>        The type of entity being exported
     * @param <E>        The enum type implementing ExportFieldMapping
     * @param filePath   Path where the Excel file will be saved
     * @param sheetsData Map containing sheet configuration for each sheet
     * @throws IOException If there's an error writing the file
     */
    public static <T, E extends Enum<E> & ExportFieldMapping<T>> void createExcelFile(String filePath, Map<String, SheetConfig<T, E>> sheetsData) throws IOException {

        LOGGER.info("Creating Excel file at: {}", filePath);

        try (Workbook workbook = new XSSFWorkbook()) {
            // Create cell style for headers
            CellStyle headerStyle = createHeaderStyle(workbook);

            // Process each sheet
            for (Map.Entry<String, SheetConfig<T, E>> entry : sheetsData.entrySet()) {
                String sheetName = entry.getKey();
                SheetConfig<T, E> sheetConfig = entry.getValue();

                if (sheetConfig.getData() == null || sheetConfig.getData().isEmpty()) {
                    LOGGER.warn("Skipping empty sheet: {}", sheetName);
                    continue;
                }

                LOGGER.debug("Processing sheet: {} with {} records", sheetName, sheetConfig.getData().size());

                // Create sheet with given name
                Sheet sheet = workbook.createSheet(sheetName);
                processSheet(sheet, sheetConfig, headerStyle);
            }

            // Write to file
            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
                LOGGER.info("Successfully wrote Excel file: {}", filePath);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create Excel file: {}", filePath, e);
            throw e;
        }
    }

    /**
     * Creates a header cell style with bold font.
     *
     * @param workbook The workbook to create the style for
     * @return The created cell style
     */
    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);
        return headerStyle;
    }

    /**
     * Processes each sheet in the Excel workbook.
     *
     * @param <T>         The type of entity being exported
     * @param <E>         The enum type implementing ExportFieldMapping
     * @param sheet       The sheet to process
     * @param sheetConfig Configuration for this sheet
     * @param headerStyle Cell style for headers
     */
    private static <T, E extends Enum<E> & ExportFieldMapping<T>> void processSheet(Sheet sheet, SheetConfig<T, E> sheetConfig, CellStyle headerStyle) {

        List<T> data = sheetConfig.getData();
        Class<E> mappingClass = sheetConfig.getMappingClass();
        List<String> headers = ExportFieldMapping.getHeaders(mappingClass);
        List<String> fields = ExportFieldMapping.getFields(mappingClass);

        // Create header row
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }

        // Create data rows
        int rowNum = 1;
        for (T obj : data) {
            Row row = sheet.createRow(rowNum++);
            for (int i = 0; i < fields.size(); i++) {
                Cell cell = row.createCell(i);
                String fieldName = fields.get(i);

                try {
                    Optional<Object> value = getFieldValue(obj, fieldName);
                    value.ifPresent(v -> cell.setCellValue(v.toString()));
                } catch (Exception e) {
                    LOGGER.warn("Error accessing field '{}' in object of type {}", fieldName, obj.getClass().getSimpleName(), e);
                    cell.setCellValue("Error");
                }
            }
        }

        // Auto-size columns
        for (int i = 0; i < fields.size(); i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Creates CSV files based on the provided data (one file per sheet).
     *
     * @param <T>          The type of entity being exported
     * @param <E>          The enum type implementing ExportFieldMapping
     * @param baseFilePath Base path for the CSV files (without extension)
     * @param sheetsData   Map containing sheet configuration for each sheet
     * @throws IOException If there's an error writing the files
     */
    public static <T, E extends Enum<E> & ExportFieldMapping<T>> void createCsvFiles(String baseFilePath, Map<String, SheetConfig<?, ?>> sheetsData) throws IOException {

        LOGGER.info("Creating CSV files with base path: {}", baseFilePath);

        for (Map.Entry<String, SheetConfig<?, ?>> entry : sheetsData.entrySet()) {
            String sheetName = entry.getKey();
            SheetConfig<?, ?> sheetConfig = entry.getValue();

            if (sheetConfig.getData() == null || sheetConfig.getData().isEmpty()) {
                LOGGER.warn("Skipping empty sheet: {}", sheetName);
                continue;
            }

            String filePath = baseFilePath + "_" + sheetName + ".csv";
            LOGGER.debug("Processing CSV file: {} with {} records", filePath, sheetConfig.getData().size());

            try (FileWriter writer = new FileWriter(filePath)) {
                processCSV(writer, sheetConfig);
                LOGGER.info("Successfully wrote CSV file: {}", filePath);
            } catch (IOException e) {
                LOGGER.error("Failed to create CSV file: {}", filePath, e);
                throw e;
            }
        }
    }

    /**
     * Processes each CSV file.
     *
     * @param <T>         The type of entity being exported
     * @param <E>         The enum type implementing ExportFieldMapping
     * @param writer      The FileWriter to write CSV data
     * @param sheetConfig Configuration for this sheet
     * @throws IOException If there's an error writing the file
     */
    private static <T, E extends Enum<E> & ExportFieldMapping<T>> void processCSV(FileWriter writer, SheetConfig<?, E> sheetConfig) throws IOException {

        List<?> data = sheetConfig.getData();
        List<String> headers = ExportFieldMapping.getHeaders(sheetConfig.getMappingClass());
        List<String> fields = ExportFieldMapping.getFields(sheetConfig.getMappingClass());

        // Write headers
        String headersStr = headers.stream().map(FileGeneratorUtil::escapeCsv).collect(Collectors.joining(","));
        writer.append(headersStr).append("\n");

        // Write data rows
        for (Object obj : data) {
            List<String> values = new ArrayList<>();

            for (String fieldName : fields) {
                try {
                    Optional<Object> value = getFieldValue(obj, fieldName);
                    values.add(escapeCsv(value.isPresent() ? value.get().toString() : ""));
                } catch (Exception e) {
                    LOGGER.warn("Error accessing field '{}' in object of type {}", fieldName, obj.getClass().getSimpleName(), e);
                    values.add("Error");
                }
            }

            writer.append(String.join(",", values)).append("\n");
        }
    }

    /**
     * Gets a field value from an object, trying both direct field access and getter methods.
     *
     * @param <T>       The type of object
     * @param obj       The object to extract value from
     * @param fieldName The name of the field
     * @return Optional containing the field value if found, empty otherwise
     * @throws IllegalAccessException    If field access is denied
     * @throws InvocationTargetException If method invocation fails
     */
    private static <T> Optional<Object> getFieldValue(T obj, String fieldName) throws IllegalAccessException, InvocationTargetException {

        // Try direct field access first
        Optional<Field> field = findField(obj.getClass(), fieldName);
        if (field.isPresent()) {
            field.get().setAccessible(true);
            return Optional.ofNullable(field.get().get(obj));
        }

        // Try getter method if field not found
        Optional<Method> getter = findGetter(obj.getClass(), fieldName);
        if (getter.isPresent()) {
            return Optional.ofNullable(getter.get().invoke(obj));
        }

        LOGGER.debug("Could not find field or getter for '{}' in class {}", fieldName, obj.getClass().getSimpleName());
        return Optional.empty();
    }

    /**
     * Find a field in a class or its superclasses.
     *
     * @param clazz     The class to search in
     * @param fieldName The name of the field to find
     * @return Optional containing the field if found, empty otherwise
     */
    private static Optional<Field> findField(Class<?> clazz, String fieldName) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                return Optional.of(currentClass.getDeclaredField(fieldName));
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return Optional.empty();
    }

    /**
     * Find a getter method for a field in a class or its superclasses.
     *
     * @param clazz     The class to search in
     * @param fieldName The name of the field to find a getter for
     * @return Optional containing the getter method if found, empty otherwise
     */
    private static Optional<Method> findGetter(Class<?> clazz, String fieldName) {
        String capitalizedFieldName = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        String getterName = "get" + capitalizedFieldName;
        String booleanGetterName = "is" + capitalizedFieldName;

        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                return Optional.of(currentClass.getMethod(getterName));
            } catch (NoSuchMethodException e1) {
                try {
                    return Optional.of(currentClass.getMethod(booleanGetterName));
                } catch (NoSuchMethodException e2) {
                    currentClass = currentClass.getSuperclass();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Escapes a string for CSV format.
     * Handles special characters like commas, quotes, and newlines.
     *
     * @param value The string value to escape
     * @return The escaped string
     */
    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n");

        if (needsQuoting) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }

        return value;
    }

    /**
     * Configuration class for a sheet in Excel or a separate CSV file.
     * Holds the data and mapping information for export.
     *
     * @param <T> The type of entity being exported
     * @param <E> The enum type implementing ExportFieldMapping
     */
    @Getter
    public static class SheetConfig<T, E extends Enum<E> & ExportFieldMapping<T>> {
        private final List<T> data;
        private final Class<E> mappingClass;

        /**
         * Creates a new SheetConfig.
         *
         * @param data         The list of data objects to export
         * @param mappingClass The enum class that defines field mappings
         */
        public SheetConfig(List<T> data, Class<E> mappingClass) {
            this.data = data;
            this.mappingClass = mappingClass;
        }
    }

    public static <T, E extends Enum<E> & ExportFieldMapping<T>> void createSingleCsvFileWithMultipleSections(String filePath, Map<String, SheetConfig<?, ?>> sheetsData) throws IOException {

        LOGGER.info("Creating a single CSV file with multiple sections: {}", filePath);

        try (FileWriter writer = new FileWriter(filePath)) {
            for (Map.Entry<String, SheetConfig<?, ?>> entry : sheetsData.entrySet()) {
                String sheetName = entry.getKey();
                SheetConfig<?, ?> sheetConfig = entry.getValue();

                if (sheetConfig.getData() == null || sheetConfig.getData().isEmpty()) {
                    LOGGER.warn("Skipping empty section: {}", sheetName);
                    continue;
                }

                writer.append("Sheet: ").append(sheetName).append("\n");

                processCSV(writer, sheetConfig);

                writer.append("\n"); // Empty line between sheets
            }

            LOGGER.info("Successfully created single CSV file: {}", filePath);
        } catch (IOException e) {
            LOGGER.error("Failed to create single CSV file: {}", filePath, e);
            throw e;
        }
    }


}