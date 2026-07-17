package com.mdvcraft.mdvrecetas.api.event;

import com.mdvcraft.mdvrecetas.model.StationType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Se dispara una vez que MDVRecetas confirmó y procesó un crafteo válido.
 * Permite que plugins como MDVQuest registren progreso sin inspeccionar lore/NBT.
 */
public final class MDVRecipeCraftEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String recipeId;
    private final String category;
    private final StationType station;
    private final ItemStack result;
    private final int craftOperations;
    private final int producedAmount;
    private final boolean cooking;

    public MDVRecipeCraftEvent(Player player, String recipeId, String category, StationType station,
                               ItemStack result, int craftOperations, int producedAmount, boolean cooking) {
        this.player = player;
        this.recipeId = recipeId;
        this.category = category;
        this.station = station;
        this.result = result == null ? null : result.clone();
        this.craftOperations = Math.max(1, craftOperations);
        this.producedAmount = Math.max(1, producedAmount);
        this.cooking = cooking;
    }

    public Player getPlayer() { return player; }
    public String getRecipeId() { return recipeId; }
    public String getCategory() { return category; }
    public StationType getStation() { return station; }
    public ItemStack getResult() { return result == null ? null : result.clone(); }
    public int getCraftOperations() { return craftOperations; }
    public int getProducedAmount() { return producedAmount; }
    public boolean isCooking() { return cooking; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
