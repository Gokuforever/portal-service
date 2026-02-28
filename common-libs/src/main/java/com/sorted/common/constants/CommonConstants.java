package com.sorted.common.constants;

/**
 * Common constants used across the application
 */
public class CommonConstants {
    
    // Date/Time formats
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String TIME_FORMAT = "HH:mm:ss";
    
    // HTTP Headers
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_ACCEPT = "Accept";
    
    // Content Types
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CONTENT_TYPE_XML = "application/xml";
    
    // Response Messages
    public static final String SUCCESS_MESSAGE = "Operation completed successfully";
    public static final String FAILURE_MESSAGE = "Operation failed";
    
    // Pagination
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    
    private CommonConstants() {
        // Private constructor to prevent instantiation
    }
}
