package com.sorted.common.utils;

import lombok.experimental.UtilityClass;

/**
 * Common string utility functions
 */
@UtilityClass
public class StringUtils {
    
    /**
     * Check if a string is null or empty
     */
    public static boolean isNullOrEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * Check if a string is not null or empty
     */
    public static boolean isNotNullOrEmpty(String str) {
        return !isNullOrEmpty(str);
    }
    
    /**
     * Get default value if string is null or empty
     */
    public static String getOrDefault(String str, String defaultValue) {
        return isNullOrEmpty(str) ? defaultValue : str;
    }
    
    /**
     * Truncate string to specified length
     */
    public static String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength);
    }
}
