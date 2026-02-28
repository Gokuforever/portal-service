package com.sorted.common.entity.beans;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ProfessorInfo {

    private String degree;
    private String department;
    private int yoe;
}
