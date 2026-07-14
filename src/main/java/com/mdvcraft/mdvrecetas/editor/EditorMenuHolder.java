package com.mdvcraft.mdvrecetas.editor;

import com.mdvcraft.mdvrecetas.model.StationType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class EditorMenuHolder implements InventoryHolder {
    public enum Screen {
        STATION_SELECT,
        CREATOR,
        OPTIONS,
        INGREDIENT_MATCH
    }

    private Inventory inventory;
    private final Screen screen;
    private final StationType station;

    public EditorMenuHolder(Screen screen, StationType station) {
        this.screen = screen;
        this.station = station;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Screen getScreen() {
        return screen;
    }

    public StationType getStation() {
        return station;
    }
}
