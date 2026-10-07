package com.mdvcraft.mdvrecetas.service;

import com.mdvcraft.mdvrecetas.hook.MMOItemsHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Preserves MDVRecetas metadata when MMOItems replaces an ItemStack after
 * applying a consumable (including repair) or applying/removing a gem.
 * MMOItems performs these item-on-item operations
 * through InventoryClickEvent and writes the rebuilt result into currentItem.
 *
 * The original crafted item is captured before MMOItems handles the click. At
 * MONITOR, after MMOItems has produced the result, only MDVRecetas-owned data
 * is restored: the forged signature PDC/lore and the visible modifier prefix.
 * Gem stats, gem lore and every other MMOItems change remain untouched.
 */
public final class MMOItemsGemCompatibilityService implements Listener {
    private final MMOItemsHook mmoItemsHook;
    private final RecipeSignatureService signatureService;
    private final ForjadorModifierService modifierService;
    private final Map<InventoryClickEvent, Snapshot> pending = new IdentityHashMap<>();

    public MMOItemsGemCompatibilityService(
            MMOItemsHook mmoItemsHook,
            RecipeSignatureService signatureService,
            ForjadorModifierService modifierService
    ) {
        this.mmoItemsHook = mmoItemsHook;
        this.signatureService = signatureService;
        this.modifierService = modifierService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void captureBeforeMmoItems(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)
                || event.getAction() != InventoryAction.SWAP_WITH_CURSOR) {
            return;
        }

        ItemStack target = event.getCurrentItem();
        if (!signatureService.hasSignatureData(target) && !modifierService.hasModifierData(target)) {
            return;
        }

        Optional<MMOItemsHook.MmoIdentity> identity = mmoItemsHook.readIdentity(target);
        if (identity.isEmpty()) {
            return;
        }

        pending.put(event, new Snapshot(target.clone(), identity.get()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void restoreAfterMmoItems(InventoryClickEvent event) {
        Snapshot snapshot = pending.remove(event);
        if (snapshot == null || !event.isCancelled()) {
            return;
        }

        ItemStack rebuilt = event.getCurrentItem();
        if (rebuilt == null || rebuilt.getType().isAir()) {
            return;
        }

        Optional<MMOItemsHook.MmoIdentity> rebuiltIdentity = mmoItemsHook.readIdentity(rebuilt);
        if (rebuiltIdentity.isEmpty() || !snapshot.identity().equals(rebuiltIdentity.get())) {
            return;
        }

        ItemStack restored = modifierService.restoreAfterMmoItemsMutation(snapshot.original(), rebuilt);
        restored = signatureService.restoreAfterMmoItemsMutation(snapshot.original(), restored);
        event.setCurrentItem(restored);
    }

    public void clear() {
        pending.clear();
    }

    private record Snapshot(ItemStack original, MMOItemsHook.MmoIdentity identity) {
    }
}
