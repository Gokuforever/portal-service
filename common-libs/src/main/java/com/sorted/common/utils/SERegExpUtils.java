package com.sorted.common.utils;

import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Component
public class SERegExpUtils {

    @Value("${se.portal.otp_length}")
    private int otp_length;

    private static int otp_length_new;

    @PostConstruct
    private void init() {
        otp_length_new = otp_length;
    }

    public static boolean isAlphaNumeric(@NonNull String val) {
        val = val.trim();
        Pattern pattern = Pattern.compile("[a-zA-Z0-9]*");
        return pattern.matcher(val).matches();
    }

    public static boolean isAlphabeticString(@NonNull String val) {
        val = val.trim();
        Pattern pattern = Pattern.compile("[a-zA-Z]*");
        return pattern.matcher(val).matches();
    }

    public static boolean isAlphabeticStringWithSpaces(@NonNull String val) {
        val = val.trim();
        Pattern pattern = Pattern.compile("^[A-Za-z\\s]+$");
        return !pattern.matcher(val).matches();
    }

    public static boolean isPincode(@NonNull String val) {
        val = val.trim();
        Pattern pattern = Pattern.compile("^[1-9][0-9]{5}$");
        return pattern.matcher(val).matches();
    }

    public static boolean isMobileNo(@NonNull String val) {
        val = val.trim();
        Pattern pattern = Pattern.compile("[6-9]\\d{9}");
        return pattern.matcher(val).matches();
    }

    public static boolean isOtp(@NonNull String val) {
//        val = val.trim();
//        StringBuilder stringBuilder = new StringBuilder();
//        stringBuilder.append("\\d{");
//        stringBuilder.append(otp_length_new);
//        stringBuilder.append("}");
//        String regex = stringBuilder.toString();
//        Pattern pattern = Pattern.compile(regex);
//        return pattern.matcher(val).matches();
        return true;
    }

    public static boolean standardTextValidation(@NonNull String val) {
        val = val.strip();
        if (val.length() > 255) {
            return false;
        }
//		Pattern pattern = Pattern.compile("^(?!.*--)(?!.*&&)[a-zA-Z0-9,.-_()'&\\s]+$");
        Pattern pattern = Pattern.compile("^[A-Za-z0-9\\s\\-&.,':()]+$");
        return pattern.matcher(val).matches();
    }

    public static boolean isQuantity(@NonNull String val) {
        val = val.strip();
        if (val.length() > 10) {
            return false;
        }
        Pattern pattern = Pattern.compile("\\d+");
        return pattern.matcher(val).matches();
    }

    public static boolean isPriceInDecimal(String value) {
        value = value.strip();
        if (!value.contains(".")) {
            value = value.concat(".00");
        } else if (value.substring(value.lastIndexOf(".") + 1).length() == 1) {
            value = value.concat("0");
        }
        Pattern pattern1 = Pattern.compile("[0.]*");
        if (pattern1.matcher(value).matches()) {
            return false;
        }
        if (value.length() > 13) {
            return false;
        }
        Pattern pattern = Pattern.compile("^(?!0*\\.0*$)\\d{1,10}(?:\\.\\d{0,2})?$");
        return pattern.matcher(value).matches();
    }

    public static boolean isPrice(@NonNull String val) {
        val = val.strip();
        if (val.length() > 10) {
            return false;
        }
        Pattern pattern = Pattern.compile("\\d+");
        return pattern.matcher(val).matches();
    }

    public static boolean isPan(@NonNull String val) {
        val = val.strip();
        if (val.length() != 10) {
            return false;
        }
        Pattern pattern = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]{1}");
        return pattern.matcher(val).matches();
    }

    public static boolean isEmail(String email_id) {
        if (email_id == null || email_id.length() > 100) {
            return false;
        }
        Pattern emailRegExp = Pattern.compile("^[a-zA-Z0-9._\\-]{1,64}@[a-zA-Z0-9.\\-]{1,253}\\.[a-zA-Z]{2,4}$");

        return emailRegExp.matcher(email_id).matches();
    }

    public static boolean isIfsc(String ifsc) {
        if (!StringUtils.hasText(ifsc)) {
            return false;
        }
        Pattern ifscRegex = Pattern.compile("^[A-Z]{4}0[A-Z0-9]{6}$");
        return ifscRegex.matcher(ifsc).matches();
    }

    public static boolean isValidGSTIN(String gstin) {
        if (gstin == null) {
            return false;
        }
        return gstin.matches("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[0-9]{1}[A-Z]{1}[0-9A-Z]{1}$");
    }
    
    public static void main(String[] args) {
        // Example usage
        System.out.println(isValidGSTIN("22AABCU9603R1ZM")); // Should print true for a valid GSTIN
        System.out.println(isValidGSTIN("12ABCDE1234F1Z5")); // Should print false for invalid GSTIN
    }
}
