package com.sorted.portal.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Utility class for generating Excel files from data collections.
 * <p>
 * This class provides functionality to create Excel workbooks with multiple sheets
 * using Apache POI. It supports mapping data from objects to Excel cells based on
 * field mappings defined through the ExportFieldMapping interface.
 * </p>
 */
public class ExcelGenerationUtility {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExcelGenerationUtility.class);

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private ExcelGenerationUtility() {
        // Utility class, no instantiation
    }

    /**
     * Creates an Excel file with multiple sheets and writes it to the specified file path.
     *
     * @param <T>            The type of data objects to be exported
     * @param <E>            The enum type implementing ExportFieldMapping that defines the field mapping
     * @param outputFilePath The path where the Excel file will be saved
     * @param sheetsData     Map containing sheet names and their corresponding data configurations
     * @throws IOException If an I/O error occurs during file writing
     */
    public static <T, E extends Enum<E> & ExportFieldMapping<T>> void createExcelFile(
            String outputFilePath,
            Map<String, FileGeneratorUtil.SheetConfig<?, ?>> sheetsData) throws IOException {

        LOGGER.debug("Starting Excel file generation to path: {}", outputFilePath);

        try (Workbook workbook = new XSSFWorkbook()) {
            populateWorkbook(workbook, sheetsData);

            try (FileOutputStream fileOut = new FileOutputStream(outputFilePath)) {
                workbook.write(fileOut);
                LOGGER.info("Successfully created Excel file: {}", outputFilePath);
            } catch (IOException e) {
                LOGGER.error("Failed to write Excel to output file: {}", outputFilePath, e);
                throw e;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create Excel workbook", e);
            throw e;
        }
    }

    /**
     * Creates an Excel file with multiple sheets and returns it as a byte array.
     *
     * @param <T>        The type of data objects to be exported
     * @param <E>        The enum type implementing ExportFieldMapping that defines the field mapping
     * @param sheetsData Map containing sheet names and their corresponding data configurations
     * @return Byte array containing the Excel file data
     * @throws IOException If an I/O error occurs during workbook creation
     */
    public static <T, E extends Enum<E> & ExportFieldMapping<T>> byte[] createExcelFileInMemory(
            Map<String, FileGeneratorUtil.SheetConfig<?, ?>> sheetsData) throws IOException {

        LOGGER.debug("Starting in-memory Excel file generation");

        try (Workbook workbook = new XSSFWorkbook()) {
            populateWorkbook(workbook, sheetsData);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                LOGGER.info("Successfully created in-memory Excel file");
                return out.toByteArray();
            } catch (IOException e) {
                LOGGER.error("Failed to write Excel to byte array", e);
                throw e;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create Excel workbook", e);
            throw e;
        }
    }

    /**
     * Populates the workbook with sheets based on the provided sheet configurations.
     *
     * @param workbook   The workbook to populate
     * @param sheetsData Map containing sheet names and their corresponding data configurations
     */
    private static void populateWorkbook(Workbook workbook, Map<String, FileGeneratorUtil.SheetConfig<?, ?>> sheetsData) {
        for (Map.Entry<String, FileGeneratorUtil.SheetConfig<?, ?>> entry : sheetsData.entrySet()) {
            String sheetName = entry.getKey();
            FileGeneratorUtil.SheetConfig<?, ?> config = entry.getValue();

            if (config.getData() == null || config.getData().isEmpty()) {
                LOGGER.warn("Skipping empty sheet: {}", sheetName);
                continue;
            }

            LOGGER.debug("Creating sheet: {}", sheetName);
            Sheet sheet = workbook.createSheet(sheetName);
            writeSheet(sheet, config, workbook);
        }
    }

    /**
     * Writes data to a sheet based on the provided sheet configuration.
     *
     * @param <T>         The type of data objects
     * @param <E>         The enum type implementing ExportFieldMapping
     * @param sheet       The sheet to write to
     * @param sheetConfig Configuration containing the data and mapping information
     * @param workbook    The parent workbook
     */
    private static <T, E extends Enum<E> & ExportFieldMapping<T>> void writeSheet(
            Sheet sheet,
            FileGeneratorUtil.SheetConfig<T, E> sheetConfig,
            Workbook workbook) {

        LOGGER.debug("Writing data to sheet: {}", sheet.getSheetName());

        List<String> headers = ExportFieldMapping.getHeaders(sheetConfig.getMappingClass());
        List<String> fields = ExportFieldMapping.getFields(sheetConfig.getMappingClass());

        CreationHelper creationHelper = workbook.getCreationHelper();

        // Create cell styles
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle bodyStyle = createBodyStyle(workbook);
        CellStyle numericStyle = createNumericStyle(workbook, bodyStyle, creationHelper);

        // Create header row
        createHeaderRow(sheet, headers, headerStyle);

        // Create data rows
        createDataRows(sheet, sheetConfig.getData(), fields, bodyStyle, numericStyle);

        // Format sheet
        formatSheet(sheet, headers.size());

        LOGGER.debug("Finished writing sheet: {}", sheet.getSheetName());
    }

    /**
     * Creates the header cell style.
     *
     * @param workbook The workbook to create the style in
     * @return The created header cell style
     */
    private static CellStyle createHeaderStyle(Workbook workbook) {
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 11);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerFont.setFontName("Calibri");

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.INDIGO.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(headerStyle);
        headerStyle.setWrapText(true);

        return headerStyle;
    }

    /**
     * Creates the body cell style.
     *
     * @param workbook The workbook to create the style in
     * @return The created body cell style
     */
    private static CellStyle createBodyStyle(Workbook workbook) {
        Font bodyFont = workbook.createFont();
        bodyFont.setFontHeightInPoints((short) 10);
        bodyFont.setFontName("Calibri");

        CellStyle bodyStyle = workbook.createCellStyle();
        bodyStyle.setFont(bodyFont);
        bodyStyle.setAlignment(HorizontalAlignment.LEFT);
        bodyStyle.setVerticalAlignment(VerticalAlignment.TOP);
        setBorders(bodyStyle);
        bodyStyle.setWrapText(true);

        return bodyStyle;
    }

    /**
     * Creates the numeric cell style.
     *
     * @param workbook       The workbook to create the style in
     * @param baseStyle      The base style to clone from
     * @param creationHelper The creation helper to create data format
     * @return The created numeric cell style
     */
    private static CellStyle createNumericStyle(Workbook workbook, CellStyle baseStyle, CreationHelper creationHelper) {
        CellStyle numericStyle = workbook.createCellStyle();
        numericStyle.cloneStyleFrom(baseStyle);
        numericStyle.setDataFormat(creationHelper.createDataFormat().getFormat("0"));

        return numericStyle;
    }

    /**
     * Creates the header row in the sheet.
     *
     * @param sheet       The sheet to create the header row in
     * @param headers     The header texts
     * @param headerStyle The style to apply to header cells
     */
    private static void createHeaderRow(Sheet sheet, List<String> headers, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }
    }

    /**
     * Creates the data rows in the sheet.
     *
     * @param <T>          The type of data objects
     * @param sheet        The sheet to create data rows in
     * @param data         The list of data objects
     * @param fields       The field names to extract from data objects
     * @param bodyStyle    The style for regular cells
     * @param numericStyle The style for numeric cells
     */
    private static <T> void createDataRows(
            Sheet sheet,
            List<T> data,
            List<String> fields,
            CellStyle bodyStyle,
            CellStyle numericStyle) {

        int rowIndex = 1;
        for (T dataObj : data) {
            Row row = sheet.createRow(rowIndex);
            for (int i = 0; i < fields.size(); i++) {
                Cell cell = row.createCell(i);
                try {
                    Optional<Object> valueOpt = getFieldValue(dataObj, fields.get(i));
                    Object value = valueOpt.orElse("");

                    if (value instanceof Number) {
                        cell.setCellValue(((Number) value).doubleValue());
                        cell.setCellStyle(numericStyle);
                    } else {
                        cell.setCellValue(value.toString());
                        cell.setCellStyle(bodyStyle);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Error extracting field '{}' from object of type {}",
                            fields.get(i), dataObj.getClass().getSimpleName(), e);
                    cell.setCellValue("Error");
                    cell.setCellStyle(bodyStyle);
                }
            }
            rowIndex++;
        }
    }

    /**
     * Formats the sheet by auto-sizing columns and freezing the header row.
     *
     * @param sheet       The sheet to format
     * @param columnCount The number of columns to auto-size
     */
    private static void formatSheet(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
        sheet.createFreezePane(0, 1);
    }

    /**
     * Sets border styles for a cell style.
     *
     * @param style The cell style to set borders on
     */
    private static void setBorders(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
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
    private static <T> Optional<Object> getFieldValue(T obj, String fieldName)
            throws IllegalAccessException, InvocationTargetException {

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

        LOGGER.debug("Could not find field or getter for '{}' in class {}",
                fieldName, obj.getClass().getSimpleName());
        return Optional.empty();
    }

    /**
     * Finds a field in a class or its superclasses.
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
     * Finds a getter method for a field in a class or its superclasses.
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
}