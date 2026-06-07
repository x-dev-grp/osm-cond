package com.osm.conditioning.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryUsageBlockersDto {

    private List<Blocker> blockers = new ArrayList<>();

    public boolean isBlocked() {
        return blockers != null && !blockers.isEmpty();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Blocker {
        private String code;
        private String message;
        private long count;
    }
}
