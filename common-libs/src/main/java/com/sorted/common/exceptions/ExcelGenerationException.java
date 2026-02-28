package com.sorted.common.exceptions;

import java.io.IOException;

/**
 * Custom exception for Excel generation errors
 */
public class ExcelGenerationException extends IOException {
    public ExcelGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
