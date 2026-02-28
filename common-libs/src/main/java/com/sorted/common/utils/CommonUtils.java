package com.sorted.common.utils;

import com.sorted.common.beans.FeeResult;
import com.sorted.common.entity.mongo.Address;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import com.sorted.common.helper.ReqBaseBean;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class CommonUtils {

    public static boolean isBoolean(Boolean val) {
        return val != null;
    }

    public static long getNanoseconds() {
        LocalDateTime now = LocalDateTime.now();
        // Calculate nanoseconds since midnight
        return now.toLocalTime().toNanoOfDay();
    }

    public static String generateRandomString(int size) {
        Random random = new Random();
        StringBuilder sb = new StringBuilder(size);

        for (int i = 0; i < size; i++) {
            // Generate random char between 'A' (65) and 'Z' (90)
            char randomChar = (char) (random.nextInt(26) + 'A');
            sb.append(randomChar);
        }

        return sb.toString();
    }

    public static String getFormattedDateForId() {
        LocalDate date = LocalDate.now();  // Current date
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd");
        return date.format(formatter);
    }

    public static <T> List<T> convertS2L(Set<T> set) {
        return set.stream().toList();
    }

    public static String generateFixedLengthRandomNumber(int length) {
        if (length > 18) {
            throw new IllegalStateException("To many digits");
        }
        int powInt = length - 1;
        int tLen = (int) Math.pow(10, powInt) * 9;
        int number = (int) (Math.random() * tLen) + (int) Math.pow(10, powInt) * 1;
        String randomNumber = String.valueOf(number);
        if (randomNumber.length() != length) {
            throw new IllegalStateException("The random number '" + randomNumber + "' is not '" + length + "' digits");
        }
        return randomNumber;
    }

    public static Long rupeeToPaise(BigDecimal val) {
        if (val == null) {
            return 0L;
        }
        val = val.setScale(2, RoundingMode.HALF_DOWN);
        return val.multiply(BigDecimal.valueOf(100)).longValue();
    }

    public static BigDecimal paiseToRupee(Long val) {
        if (val == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal bigDecimal = new BigDecimal(val).divide(BigDecimal.valueOf(100));
        return bigDecimal.setScale(2, RoundingMode.HALF_DOWN);
    }

    public static String createCode(@NonNull String prefix) {
        long nanoseconds = CommonUtils.getNanoseconds();
        StringBuilder stringBuffer = new StringBuilder();
        stringBuffer.append(prefix);
        stringBuffer.append(nanoseconds);
        stringBuffer.append("-");
        stringBuffer.append(Year.now());
        return stringBuffer.toString();
    }

    public static <T extends ReqBaseBean> void extractHeaders(HttpServletRequest httpServletRequest, T bean) {
        try {
            String req_user_id = httpServletRequest.getHeader("req_user_id");
            if (!StringUtils.hasText(req_user_id)) {
                throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }
            bean.setReq_user_id(req_user_id);
        } catch (Exception e2) {
            throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
        }
    }

    public static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Trim leading/trailing spaces and replace multiple spaces with a single space
        input = input.trim().replaceAll("\\s+", " ");

        String[] words = input.split(" "); // Split by single space now
        StringBuilder titleCased = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                String firstLetter = word.substring(0, 1).toUpperCase(); // Capitalize first letter
                String remaining = word.substring(1).toLowerCase(); // Lowercase the rest
                titleCased.append(firstLetter).append(remaining).append(" ");
            }
        }

        // Remove the last extra space
        return titleCased.toString().trim();
    }

    public static boolean isImage(MultipartFile file) {
        // Check if the file is empty
        if (file.isEmpty()) {
            return false;
        }

        // Get the content type of the file
        String contentType = file.getContentType();
        if (contentType != null && contentType.startsWith("image/")) {
            return true;
        }

        // Alternatively, you can also check the file extension (optional)
        String fileName = file.getOriginalFilename();
        if (fileName != null) {
            String extension = getFileExtension(fileName);
            return isImageExtension(extension);
        }

        return false;
    }

    // Extract file extension
    private static String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex != -1) ? fileName.substring(dotIndex + 1) : "";
    }

    // Check if the extension belongs to an image type
    private static boolean isImageExtension(String extension) {
        // Add more image extensions as needed
        return extension.equalsIgnoreCase("jpg") || extension.equalsIgnoreCase("jpeg")
                || extension.equalsIgnoreCase("png");
    }

    public static LocalDateTime convertEpochToLocalDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    // Distance calculation logic (Haversine formula)
    private static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS = 6371; // Earth's radius in kilometers
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c; // Distance in kilometers
    }

    public static String findNearestSeller(double customerLat, double customerLon, List<Address> addresses) {
        String nearestSeller = null;
        double minimumDistance = Double.MAX_VALUE;

        for (Address address : addresses) {
            double distance = calculateDistance(customerLat, customerLon, address.getLat().doubleValue(),
                    address.getLng().doubleValue());
            if (distance < minimumDistance) {
                minimumDistance = distance;
                nearestSeller = address.getId();
            }
        }

        return nearestSeller;
    }

    /**
     * Calculates the revenue (fees) and cost (actual amount received) from the total amount.
     *
     * @param totalAmountPaise The original amount in paise (e.g., ₹100 = 10000).
     * @param feePercentage    The fee percentage as a double (e.g., 10.5 for 10.5%).
     * @return A result object containing revenue and cost in rupee.
     */
    public static FeeResult calculateFees(long totalAmountPaise, double feePercentage) {
        long scaledPercentage = Math.round(feePercentage * 100); // e.g., 10.5 → 1050
        long revenue = (totalAmountPaise * scaledPercentage) / 10000;
        long cost = totalAmountPaise - revenue;
        return new FeeResult(paiseToRupee(revenue), paiseToRupee(cost), revenue, cost);
    }

    public static boolean isPdf(byte[] fileBytes, String fileName) {
        // Check file extension
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
            return false;
        }

        // Check PDF magic bytes (PDF files start with "%PDF-")
        if (fileBytes.length < 4) {
            return false;
        }

        String header = new String(fileBytes, 0, 4, StandardCharsets.US_ASCII);
        return header.equals("%PDF");
    }

    public static void main(String[] args) {
        System.out.println(convertEpochToLocalDateTime(1668081552000L));
    }
}
