package com.sorted.common.beans;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GroupComponent {
    private int id;
    private String title;
    private Map<String, List<String>> filters;
}
