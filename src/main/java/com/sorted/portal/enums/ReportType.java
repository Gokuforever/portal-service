package com.sorted.portal.enums;

import com.sorted.portal.assisting.beans.ProductDetailsBean;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Enum defining different types of reports with their headers and field mappings
 */
@Getter
public enum ReportType {
    
    PRODUCT_BASIC("products_basic_report.xlsx", "Products", 
            Arrays.asList("Product ID", "Name", "Category", "MRP", "Selling Price", "Quantity"),
            (ProductDetailsBean product) -> Arrays.asList(
                product.getId(),
                product.getName(),
                product.getCategory_name(),
                product.getMrp(),
                product.getSelling_price(),
                product.getQuantity()
            )),
            
    PRODUCT_DETAILED("products_detailed_report.xlsx", "Products", 
            Arrays.asList("Product ID", "Name", "Category", "MRP", "Selling Price", 
                    "Quantity", "Description", "Seller Code", "Seller Name"),
            (ProductDetailsBean product) -> Arrays.asList(
                product.getId(),
                product.getName(),
                product.getCategory_name(),
                product.getMrp(),
                product.getSelling_price(),
                product.getQuantity(),
                product.getDescription(),
                product.getSeller_code(),
                product.getSeller_name()
            )),
            
    PRODUCT_INVENTORY("products_inventory_report.xlsx", "Inventory", 
            Arrays.asList("Product ID", "Name", "Category", "Quantity", "Last Updated"),
            (ProductDetailsBean product) -> Arrays.asList(
                product.getId(),
                product.getName(),
                product.getCategory_name(),
                product.getQuantity(),
                product.getModification_date()
            ));
    
    private final String fileName;
    private final String sheetName;
    private final List<String> headers;
    private final Function<Object, List<Object>> dataExtractor;
    
    /**
     * Constructor for ReportType enum
     * 
     * @param fileName The default file name for this report type
     * @param sheetName The default sheet name for this report type
     * @param headers The column headers for this report type
     * @param dataExtractor Function to extract data from objects for this report type
     */
    <T> ReportType(String fileName, String sheetName, List<String> headers, 
                  Function<T, List<Object>> dataExtractor) {
        this.fileName = fileName;
        this.sheetName = sheetName;
        this.headers = headers;
        this.dataExtractor = (Function<Object, List<Object>>) dataExtractor;
    }

}