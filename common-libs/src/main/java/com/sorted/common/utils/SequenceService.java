package com.sorted.common.utils;

import com.sorted.common.entity.service.CounterService;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;

@Service
public class SequenceService {

    private final CounterService counterService;

    public SequenceService(CounterService counterService) {
        this.counterService = counterService;
    }

    private String getNextSequence(String name, String pattern) {
        long sequence = counterService.getNextSequence(name);
        DecimalFormat df = new DecimalFormat(pattern);
        return df.format(sequence);
    }

    public String generateId(String prefix) {
        return prefix + "-" + CommonUtils.getFormattedDateForId() + "-" + CommonUtils.generateRandomString(5) + getNextSequence(prefix, "000000");
    }

    public String generateIdWithoutRandomString(String prefix) {
        return prefix + "-" + CommonUtils.getFormattedDateForId() + "-" + getNextSequence(prefix, "000");
    }
}
