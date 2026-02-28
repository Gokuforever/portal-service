package com.sorted.common.beans;

import java.util.List;

public record CreateZoneRequest(
        String name,
        String zoneId,
        List<List<List<Double>>> coordinates
) {
}
