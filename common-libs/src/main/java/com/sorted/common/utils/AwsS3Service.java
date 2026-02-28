//package com.sorted.commons.utils;
//
//import com.amazonaws.auth.AWSStaticCredentialsProvider;
//import com.amazonaws.auth.BasicAWSCredentials;
//import com.amazonaws.regions.Regions;
//import com.amazonaws.services.s3.AmazonS3;
//import com.amazonaws.services.s3.AmazonS3ClientBuilder;
//import com.amazonaws.services.s3.model.ObjectMetadata;
//import com.amazonaws.services.s3.model.PutObjectRequest;
//import com.sorted.commons.beans.UsersBean;
//import com.sorted.commons.entity.mongo.File_Upload_Details;
//import com.sorted.commons.entity.service.File_Upload_Details_Service;
//import com.sorted.commons.enums.DocumentType;
//import com.sorted.commons.enums.ResponseCode;
//import com.sorted.commons.enums.UserType;
//import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
//import jakarta.annotation.PostConstruct;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.ByteArrayInputStream;
//import java.io.IOException;
//import java.util.Objects;
//import java.util.UUID;
//
//@Slf4j
//@Service
//public class AwsS3Service {
//
//    private final File_Upload_Details_Service fileUploadDetailsService;
//
//    private final String accessKey;
//    private final String secretKey;
//    private final String region;
//    private final String bucketName;
//    private final String cloudFrontDomain;
//
//    private AmazonS3 s3Client;
//
//    private static final String BASE_FOLDER = "product-images/engineering-books/";
//    private static final String REPORTS_FOLDER = "reports/orders/";
//    private static final String INVOICE_FOLDER = "b2c/invoices";
//
//    public AwsS3Service(
//            File_Upload_Details_Service fileUploadDetailsService,
//            @Value("${aws.access.key}") String accessKey,
//            @Value("${aws.secret.key}") String secretKey,
//            @Value("${aws.region}") String region,
//            @Value("${aws.s3.bucket.name}") String bucketName,
//            @Value("${aws.cloudfront.distribution.domain}") String cloudFrontDomain
//    ) {
//        this.fileUploadDetailsService = fileUploadDetailsService;
//        this.accessKey = accessKey;
//        this.secretKey = secretKey;
//        this.region = region;
//        this.bucketName = bucketName;
//        this.cloudFrontDomain = cloudFrontDomain;
//    }
//
//    @PostConstruct
//    private void initializeAmazon() {
//        log.info("Initializing AWS S3 Client with region: {}", region);
//        BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
//        this.s3Client = AmazonS3ClientBuilder.standard()
//                .withRegion(Regions.fromName(region))
//                .withCredentials(new AWSStaticCredentialsProvider(credentials))
//                .build();
//    }
//
//    public String uploadExcelReport(byte[] excelBytes, String fileName) throws IOException {
//        log.info("Starting Excel report upload: {}", fileName);
//
//        ObjectMetadata metadata = new ObjectMetadata();
//        metadata.setContentLength(excelBytes.length);
//        metadata.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
//
//        String s3Key = REPORTS_FOLDER + fileName;
//
//        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(excelBytes)) {
//            s3Client.putObject(new PutObjectRequest(bucketName, s3Key, inputStream, metadata));
//            log.info("Successfully uploaded Excel report to S3: bucket={}, key={}", bucketName, s3Key);
//        }
//
//        String fileUrl = getFileUrl(s3Key);
//        log.info("Excel report URL: {}", fileUrl);
//        return fileUrl;
//    }
//
//    public String uploadPhoto(byte[] bytes, String contentType, String fileName) throws IOException {
//
//        ObjectMetadata metadata = new ObjectMetadata();
//        metadata.setContentLength(bytes.length);
//        metadata.setContentType(contentType);
//
//        fileName = generateFileName(fileName);
//
//        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
//            s3Client.putObject(new PutObjectRequest(bucketName, fileName, inputStream, metadata));
//            log.info("Successfully uploaded image to S3: bucket={}, key={}", bucketName, fileName);
//        }
//
//        return getFileUrl(fileName);
//    }
//
//    public File_Upload_Details uploadPhoto(MultipartFile multipartFile, UsersBean usersBean, DocumentType documentType) throws IOException {
//        log.info("Starting upload for user ID: {}, documentType: {}", usersBean.getId(), documentType);
//
//        // 1. Validate image type
//        if (!CommonUtils.isImage(multipartFile)) {
//            log.warn("Invalid file type uploaded by user ID: {}", usersBean.getId());
//            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_FILE_TYPE);
//        }
//
//        // 2. Check access rights
//        UserType userType = usersBean.getRole().getUser_type();
//        if (!documentType.getAllowed_to().contains(userType)) {
//            log.error("Access denied: UserType {} is not allowed to upload document type {}", userType, documentType);
//            throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
//        }
//
//        // 3. Prepare file metadata
//        String fileName = generateFileName(multipartFile);
//        ObjectMetadata metadata = new ObjectMetadata();
//        metadata.setContentLength(multipartFile.getSize());
//        metadata.setContentType(multipartFile.getContentType());
//
//        log.info("Generated filename: {}", fileName);
//
//        // 4. Upload to S3
//        s3Client.putObject(new PutObjectRequest(bucketName, fileName, multipartFile.getInputStream(), metadata));
//        log.info("Successfully uploaded file to S3: bucket={}, key={}", bucketName, fileName);
//
//        // 5. Save file metadata in DB
//        File_Upload_Details details = new File_Upload_Details();
//        populateUploadDetails(details, usersBean, userType, documentType);
//        details.setFile_url(getFileUrl(fileName));
//        details.setFile_extension(getExtension(Objects.requireNonNull(multipartFile.getOriginalFilename())));
//        double sizeKB = multipartFile.getSize() / 1024.0;
//        details.setSize(String.format("%.2fkb", sizeKB));
//
//        File_Upload_Details savedDetails = fileUploadDetailsService.create(details, usersBean.getId());
//        log.info("Metadata stored in DB with ID: {}", savedDetails.getId());
//
//        return savedDetails;
//    }
//
//    public File_Upload_Details uploadPdf(byte[] pdfBytes, String originalFileName, UsersBean usersBean, DocumentType documentType) throws IOException {
//        log.info("Starting PDF upload for user ID: {}, documentType: {}", usersBean.getId(), documentType);
//
//        // 1. Validate PDF type
//        if (!CommonUtils.isPdf(pdfBytes, originalFileName)) {
//            log.warn("Invalid PDF file uploaded by user ID: {}", usersBean.getId());
//            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_FILE_TYPE);
//        }
//
//        // 2. Check access rights
//        UserType userType = usersBean.getRole().getUser_type();
//        if (!documentType.getAllowed_to().contains(userType)) {
//            log.error("Access denied: UserType {} is not allowed to upload document type {}", userType, documentType);
//            throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
//        }
//
//        // 3. Prepare file metadata
//        String fileName = generatePdfFileName(originalFileName);
//        ObjectMetadata metadata = new ObjectMetadata();
//        metadata.setContentLength(pdfBytes.length);
//        metadata.setContentType("application/pdf");
//
//        log.info("Generated PDF filename: {}", fileName);
//
//        // 4. Upload to S3 using byte array
//        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfBytes)) {
//            s3Client.putObject(new PutObjectRequest(bucketName, fileName, inputStream, metadata));
//            log.info("Successfully uploaded PDF to S3: bucket={}, key={}", bucketName, fileName);
//        }
//
//        // 5. Save file metadata in DB
//        File_Upload_Details details = new File_Upload_Details();
//        populateUploadDetails(details, usersBean, userType, documentType);
//        details.setFile_url(getFileUrl(fileName));
//        details.setFile_extension(getExtension(Objects.requireNonNull(originalFileName)));
//        double sizeKB = pdfBytes.length / 1024.0;
//        details.setSize(String.format("%.2fkb", sizeKB));
//
//        File_Upload_Details savedDetails = fileUploadDetailsService.create(details, usersBean.getId());
//        log.info("PDF metadata stored in DB with ID: {}", savedDetails.getId());
//
//        return savedDetails;
//    }
//
//    // Helper method to generate PDF-specific filename
//    private String generatePdfFileName(String originalFileName) {
//        // Extract base name without extension
//        String baseName = originalFileName;
//        if (originalFileName.contains(".")) {
//            baseName = originalFileName.substring(0, originalFileName.lastIndexOf("."));
//        }
//
//        // Generate unique filename with timestamp
//        String timestamp = String.valueOf(System.currentTimeMillis());
//        return INVOICE_FOLDER + baseName + "_" + timestamp + ".pdf";
//    }
//
//    private String getFileUrl(String fileName) {
//        return "https://" + cloudFrontDomain + "/" + fileName;
//    }
//
//    private String generateFileName(MultipartFile file) {
//        String original = Objects.requireNonNull(file.getOriginalFilename()).replace(" ", "_");
//        return BASE_FOLDER + UUID.randomUUID() + "-" + original + CommonUtils.generateFixedLengthRandomNumber(3);
//    }
//
//    private String generateFileName(String fileName) {
//        String original = Objects.requireNonNull(fileName).replace(" ", "_");
//        return BASE_FOLDER + UUID.randomUUID() + "-" + original + CommonUtils.generateFixedLengthRandomNumber(3);
//    }
//
//    private String getExtension(String fileName) {
//        int dotIndex = fileName.lastIndexOf(".");
//        return (dotIndex >= 0) ? fileName.substring(dotIndex + 1) : "";
//    }
//
//    private void populateUploadDetails(File_Upload_Details details, UsersBean user, UserType userType, DocumentType docType) {
//        if (userType == UserType.SELLER) {
//            details.setEntity_id(user.getRole().getSeller_id());
//        } else {
//            details.setEntity_id(user.getId());
//        }
//        details.setUser_type(userType);
//        details.setDocument_type_id(docType.getId());
//    }
//}
