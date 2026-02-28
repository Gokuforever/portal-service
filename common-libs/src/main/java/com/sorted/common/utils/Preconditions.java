package com.sorted.common.utils;

import com.sorted.common.enums.ResponseCode;
import com.sorted.common.exceptions.InvalidArgumentException;
import org.springframework.stereotype.Service;

@Service
public class Preconditions {

    public static void check(boolean condition, ResponseCode responseCode) {
        if (!condition) {
            throw new InvalidArgumentException(responseCode);
        }
    }

    public static <T extends RuntimeException> void check(boolean condition, T exception) {
        if (!condition) {
            throw exception;
        }
    }

}
