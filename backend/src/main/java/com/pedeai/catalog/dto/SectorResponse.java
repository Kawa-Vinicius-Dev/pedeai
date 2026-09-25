package com.pedeai.catalog.dto;

import com.pedeai.catalog.domain.Sector;

import java.util.UUID;

public record SectorResponse(UUID id, String name, boolean defaultSector, boolean active) {
    public static SectorResponse from(Sector sector) {
        return new SectorResponse(sector.getId(), sector.getName(), sector.isDefaultSector(), sector.isActive());
    }
}
