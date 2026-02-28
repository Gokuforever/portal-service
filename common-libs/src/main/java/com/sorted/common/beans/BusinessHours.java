package com.sorted.common.beans;

import com.sorted.common.enums.WeekDay;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BusinessHours implements Serializable {
    /**
     *
     */
    @Serial
    private static final long serialVersionUID = 1L;
    private Integer start_time;
    private Integer end_time;
    private List<WeekDay> fixed_off_days;
}
