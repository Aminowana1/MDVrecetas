package com.mdvcraft.mdvrecetas.model;

public enum StationType {
    CRAFTING_TABLE,
    FURNACE,
    BLAST_FURNACE,
    SMOKER,
    CAMPFIRE;

    public boolean isCookingStation() {
        return this == FURNACE || this == BLAST_FURNACE || this == SMOKER || this == CAMPFIRE;
    }
}
