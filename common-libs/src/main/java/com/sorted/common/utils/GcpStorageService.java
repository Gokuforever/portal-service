package com.sorted.common.utils;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.sorted.common.beans.UsersBean;
import com.sorted.common.entity.mongo.File_Upload_Details;
import com.sorted.common.entity.service.File_Upload_Details_Service;
import com.sorted.common.enums.DocumentType;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.enums.UserType;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class GcpStorageService {

    private final File_Upload_Details_Service fileUploadDetailsService;

    @Value("${gcp.project-id:project-28856987-837d-4ca3-a16}")
    private String projectId;

    @Value("${gcp.bucket.name:studeaze-assets}")
    private String bucketName;

    @Value("${gcp.cdn.domain:cdn.studeaze.in}") // optional (Cloud CDN / LB)
    private String cdnDomain;

    private Storage storage;

    private static final String BASE_FOLDER = "product-images/engineering-books/";
    private static final String REPORTS_FOLDER = "reports/orders/";
    private static final String INVOICE_FOLDER = "b2c/invoices/";

    public GcpStorageService(File_Upload_Details_Service fileUploadDetailsService) {
        this.fileUploadDetailsService = fileUploadDetailsService;
    }

    @PostConstruct
    private void initialize() {
        log.info("Initializing GCP Storage for project: {}", projectId);
        this.storage = StorageOptions.newBuilder()
                .setProjectId(projectId)
                .build()
                .getService();
    }

    /* ===================== EXCEL ===================== */

    public String uploadExcelReport(byte[] excelBytes, String fileName) {
        log.info("Uploading Excel report: {}", fileName);

        String objectName = REPORTS_FOLDER + fileName;

        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName)
                .setContentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .build();

        storage.create(blobInfo, excelBytes);

        return getFileUrl(objectName);
    }

    /* ===================== IMAGE (BYTE[]) ===================== */

    public String uploadPhoto(byte[] bytes, String contentType, String fileName) {
        String objectName = generateFileName(fileName);

        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName)
                .setContentType(contentType)
                .build();

        storage.create(blobInfo, bytes);

        return getFileUrl(objectName);
    }

    /* ===================== IMAGE (MULTIPART) ===================== */

    public File_Upload_Details uploadPhoto(
            MultipartFile multipartFile,
            UsersBean usersBean,
            DocumentType documentType) throws IOException {

        if (!CommonUtils.isImage(multipartFile)) {
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_FILE_TYPE);
        }

        UserType userType = usersBean.getRole().getUser_type();
        if (!documentType.getAllowed_to().contains(userType)) {
            throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
        }

        String objectName = generateFileName(multipartFile);

        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName)
                .setContentType(multipartFile.getContentType())
                .build();

        storage.create(blobInfo, multipartFile.getBytes());

        File_Upload_Details details = new File_Upload_Details();
        populateUploadDetails(details, usersBean, userType, documentType);
        details.setFile_url(getFileUrl(objectName));
        details.setFile_extension(
                getExtension(Objects.requireNonNull(multipartFile.getOriginalFilename())));

        double sizeKB = multipartFile.getSize() / 1024.0;
        details.setSize(String.format("%.2fkb", sizeKB));

        return fileUploadDetailsService.create(details, usersBean.getId());
    }

    /* ===================== PDF ===================== */

    public File_Upload_Details uploadPdf(
            byte[] pdfBytes,
            String originalFileName,
            UsersBean usersBean,
            DocumentType documentType) {

        if (!CommonUtils.isPdf(pdfBytes, originalFileName)) {
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_FILE_TYPE);
        }

        UserType userType = usersBean.getRole().getUser_type();
        if (!documentType.getAllowed_to().contains(userType)) {
            throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
        }

        String objectName = generatePdfFileName(originalFileName);

        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName)
                .setContentType("application/pdf")
                .build();

        storage.create(blobInfo, pdfBytes);

        File_Upload_Details details = new File_Upload_Details();
        populateUploadDetails(details, usersBean, userType, documentType);
        details.setFile_url(getFileUrl(objectName));
        details.setFile_extension(getExtension(originalFileName));

        double sizeKB = pdfBytes.length / 1024.0;
        details.setSize(String.format("%.2fkb", sizeKB));

        return fileUploadDetailsService.create(details, usersBean.getId());
    }

    /* ===================== HELPERS ===================== */

    private String generatePdfFileName(String originalFileName) {
        String base = originalFileName.contains(".")
                ? originalFileName.substring(0, originalFileName.lastIndexOf('.'))
                : originalFileName;

        return INVOICE_FOLDER + base + "_" + System.currentTimeMillis() + ".pdf";
    }

    private String getFileUrl(String objectName) {
        if (cdnDomain != null && !cdnDomain.isBlank()) {
            return "https://" + cdnDomain + "/" + objectName;
        }
        return "https://storage.googleapis.com/" + bucketName + "/" + objectName;
    }

    private String generateFileName(MultipartFile file) {
        String original = Objects.requireNonNull(file.getOriginalFilename()).replace(" ", "_");
        return BASE_FOLDER + UUID.randomUUID() + "-" + original
                + CommonUtils.generateFixedLengthRandomNumber(3);
    }

    private String generateFileName(String fileName) {
        String original = fileName.replace(" ", "_");
        return BASE_FOLDER + UUID.randomUUID() + "-" + original
                + CommonUtils.generateFixedLengthRandomNumber(3);
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf(".");
        return dot >= 0 ? fileName.substring(dot + 1) : "";
    }

    private void populateUploadDetails(
            File_Upload_Details details,
            UsersBean user,
            UserType userType,
            DocumentType docType) {

        if (userType == UserType.SELLER) {
            details.setEntity_id(user.getRole().getSeller_id());
        } else {
            details.setEntity_id(user.getId());
        }
        details.setUser_type(userType);
        details.setDocument_type_id(docType.getId());
    }
}
