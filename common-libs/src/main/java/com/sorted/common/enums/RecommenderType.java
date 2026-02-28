package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RecommenderType {

    PROFESSOR("PRFSSR"),
    STUDENT("STUDNT"),
    INDUSTRY_EXPERT("INXPRT");

    private final String value;

    public static RecommenderType fromValue(String value) {
        return switch (value) {
            case "PRFSSR" -> PROFESSOR;
            case "STUDNT" -> STUDENT;
            case "INXPRT" -> INDUSTRY_EXPERT;
            default -> null;
        };
    }
}
