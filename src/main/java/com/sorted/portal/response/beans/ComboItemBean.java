package com.sorted.portal.response.beans;

import com.sorted.commons.beans.Media;
import lombok.Builder;

import java.util.List;


@Builder
public class ComboItemBean {
    private String id;
    private String name;
    private String description;
    private List<Media> media;
}
