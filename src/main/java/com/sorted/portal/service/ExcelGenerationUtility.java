package com.sorted.portal.service;

import com.sorted.commons.exceptions.ExcelGenerationException;
import com.sorted.portal.enums.ReportType;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

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

    /**
     * Generates an Excel report based on the provided data and report type
     * with memory optimization and enhanced features
     *
     * @param data       The data to include in the report
     * @param reportType The type of report to generate
     * @param response   The HTTP response to write the Excel file to
     * @throws IOException If an error occurs while writing the Excel file
     */
    public static <T> void generateExcelReport(List<T> data, ReportType reportType,
                                               HttpServletResponse response) throws IOException {
        if (CollectionUtils.isEmpty(data)) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        // Set response headers for Excel download
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + reportType.getFileName());

        // Use SXSSFWorkbook for memory optimization with large datasets
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) { // 100 rows in memory, rest flushed to disk
            SXSSFSheet sheet = workbook.createSheet(reportType.getSheetName());

            // Create cell styles cache to avoid creating duplicate styles
            Map<String, CellStyle> styles = createStyles(workbook);

            // Create header row
            Row headerRow = sheet.createRow(0);
            List<String> headers = reportType.getHeaders();

            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(styles.get("header"));

                // Set column width constraints to prevent excessive auto-sizing
                sheet.setColumnWidth(i, 15 * 256); // 15 characters width as default
            }

            // Create data rows with optimized cell value setting
            int rowNum = 1;
            CellStyle dateStyle = styles.get("date");
            CellStyle numberStyle = styles.get("number");

            for (T item : data) {
                Row row = sheet.createRow(rowNum++);
                List<Object> rowData = reportType.getDataExtractor().apply(item);

                for (int i = 0; i < rowData.size(); i++) {
                    Cell cell = row.createCell(i);
                    Object value = rowData.get(i);
                    setCellValue(cell, value, dateStyle, numberStyle);
                }

                // Flush rows to disk every 1000 rows to optimize memory usage
                if (rowNum % 1000 == 0) {
                    sheet.flushRows(1000);
                }
            }

            // Auto-size columns only if data size is reasonable
            if (data.size() <= 5000) {
                for (int i = 0; i < headers.size(); i++) {
                    // Cast to SXSSFSheet to use the tracking method
                    sheet.trackColumnForAutoSizing(i);
                    sheet.autoSizeColumn(i);
                    // Add a little extra width for better readability
                    int currentWidth = sheet.getColumnWidth(i);
                    sheet.setColumnWidth(i, (int) (currentWidth * 1.2));
                }
            }

            // Write to response output stream and clean up temp files
            workbook.write(response.getOutputStream());
            workbook.dispose(); // Important for SXSSFWorkbook to clean up temp files
        } catch (IOException e) {
            // Log the error and throw a more specific exception
            throw new ExcelGenerationException("Failed to generate Excel report", e);
        }
    }

    /**
     * Creates and caches common cell styles for reuse across the workbook
     *
     * @param workbook The workbook to create styles for
     * @return Map of named styles
     */
    private static Map<String, CellStyle> createStyles(Workbook workbook) {
        Map<String, CellStyle> styles = new HashMap<>();

        // Header style
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        styles.put("header", headerStyle);

        // Date style
        CellStyle dateStyle = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("dd-mm-yyyy"));
        styles.put("date", dateStyle);

        // Number style
        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setDataFormat(createHelper.createDataFormat().getFormat("#,##0.00"));
        styles.put("number", numberStyle);

        return styles;
    }

    /**
     * Sets cell value with appropriate formatting based on value type
     *
     * @param cell        The cell to set the value for
     * @param value       The value to set
     * @param dateStyle   Style for date values
     * @param numberStyle Style for numeric values
     */
    private static void setCellValue(Cell cell, Object value, CellStyle dateStyle, CellStyle numberStyle) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number) {
            double numValue = ((Number) value).doubleValue();
            cell.setCellValue(numValue);
            cell.setCellStyle(numberStyle);
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
            cell.setCellStyle(dateStyle);
        } else if (value instanceof LocalDate) {
            cell.setCellValue(Date.from(((LocalDate) value).atStartOfDay(ZoneId.systemDefault()).toInstant()));
            cell.setCellStyle(dateStyle);
        } else if (value instanceof LocalDateTime) {
            cell.setCellValue(Date.from(((LocalDateTime) value).atZone(ZoneId.systemDefault()).toInstant()));
            cell.setCellStyle(dateStyle);
        } else {
            cell.setCellValue(value.toString());
        }
    }


}