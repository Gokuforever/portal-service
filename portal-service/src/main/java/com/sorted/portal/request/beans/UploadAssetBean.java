package com.sorted.portal.request.beans;

import lombok.Data;

@Data
public class UploadAssetBean {

    private byte[] bytes;
    private String fileName;
    private String contentType;
    private String altText;
    private boolean mobileView;
}
