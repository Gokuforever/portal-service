package com.sorted.common.utils;

import java.util.ArrayList;
import java.util.List;

public class IndianCurrencyConverter {

    private static final String[] units = {"", "One", "Two", "Three", "Four", "Five",
            "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen",
            "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};

    private static final String[] tens = {"", "", "Twenty", "Thirty", "Forty",
            "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    public static String convertToWords(double amount) {
        if (amount == 0) return "Zero Rupees Only";

        long rupees = (long) amount;
        int paise = (int) Math.round((amount - rupees) * 100);

        String result = "";

        if (rupees > 0) {
            result += convertAmount(rupees) + " Rupees";
        }

        if (paise > 0) {
            if (rupees > 0) {
                result += " and ";
            }
            result += convertAmount(paise) + " Paise";
        }

        if (rupees == 0 && paise == 0) {
            result = "Zero Rupees";
        }

        return result + " Only";
    }

    private static String convertAmount(long amount) {
        if (amount == 0) return "";

        String result = "";

        // Crores
        if (amount >= 10000000) {
            result += convertHundreds((int) (amount / 10000000)) + " Crore ";
            amount %= 10000000;
        }

        // Lakhs
        if (amount >= 100000) {
            result += convertHundreds((int) (amount / 100000)) + " Lakh ";
            amount %= 100000;
        }

        // Thousands
        if (amount >= 1000) {
            result += convertHundreds((int) (amount / 1000)) + " Thousand ";
            amount %= 1000;
        }

        // Hundreds
        if (amount > 0) {
            result += convertHundreds((int) amount);
        }

        return result.trim(); // This removes trailing spaces
    }

    private static String convertHundreds(int num) {
        List<String> parts = new ArrayList<>();

        if (num >= 100) {
            parts.add(units[num / 100]);
            parts.add("Hundred");
            num %= 100;
        }

        if (num >= 20) {
            parts.add(tens[num / 10]);
            num %= 10;
        }

        if (num > 0) {
            parts.add(units[num]);
        }

        return String.join(" ", parts);
    }
}
