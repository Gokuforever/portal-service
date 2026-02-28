package com.sorted.common.beans;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class InfoSection {
    // Getters and setters
    private String label;
    private int index;
    private String cssClass = "info-section";

    public InfoSection(String label, int index) {
        this.label = label;
        this.index = index;
    }

    public InfoSection(String label, int index, String cssClass) {
        this.label = label;
        this.index = index;
        this.cssClass = cssClass;
    }

}
