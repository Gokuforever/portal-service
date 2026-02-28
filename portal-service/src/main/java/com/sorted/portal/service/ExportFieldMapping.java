package com.sorted.portal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Interface for mapping export fields to headers.
 * This provides type safety and eliminates string literals when specifying export fields.
 * Implementations of this interface are typically enums that define which entity fields
 * should be exported and how they should be presented in reports.
 *
 * @param <T> The type of entity being exported
 * @author Sorted Portal Team
 * @since 1.0
 */
public interface ExportFieldMapping<T> {

    /**
     * Logger instance for this class
     */
    Logger LOGGER = LoggerFactory.getLogger(ExportFieldMapping.class);

    /**
     * Gets the header name for this field (to be displayed in Excel/CSV).
     *
     * @return The header display name
     */
    String getHeaderName();

    /**
     * Gets the property name in the entity class.
     * This should match the field name or getter method (without 'get'/'is' prefix).
     *
     * @return The property name
     */
    String getPropertyName();

    /**
     * Utility to extract all headers from a given enum class.
     *
     * @param <E>       The enum type implementing ExportFieldMapping
     * @param enumClass The class object of the enum
     * @return List of header names in the order defined in the enum
     */
    static <E extends Enum<E> & ExportFieldMapping<?>> List<String> getHeaders(Class<E> enumClass) {
        LOGGER.debug("Extracting headers from enum: {}", enumClass.getSimpleName());
        return Arrays.stream(enumClass.getEnumConstants())
                .map(ExportFieldMapping::getHeaderName)
                .collect(Collectors.toList());
    }

    /**
     * Utility to extract all fields/properties from a given enum class.
     *
     * @param <E>       The enum type implementing ExportFieldMapping
     * @param enumClass The class object of the enum
     * @return List of property names in the order defined in the enum
     */
    static <E extends Enum<E> & ExportFieldMapping<?>> List<String> getFields(Class<E> enumClass) {
        LOGGER.debug("Extracting property names from enum: {}", enumClass.getSimpleName());
        return Arrays.stream(enumClass.getEnumConstants())
                .map(ExportFieldMapping::getPropertyName)
                .collect(Collectors.toList());
    }
}