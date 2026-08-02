package com.mdvcraft.mdvrecetas.service;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;

/**
 * Compatibility bridge for MMOItems Revision ID updates.
 *
 * The project intentionally avoids a compile-time MMOItems dependency, so the
 * finish event is registered and handled through reflection. At that point the
 * old ItemStack still exists inside MMOItemReforger and the fully rebuilt item
 * can be replaced before MMOItems writes it back to the player's inventory.
 */
public final class MMOItemsRevisionCompatibilityService {
    private static final String FINISH_EVENT_CLASS =
            "net.Indyuce.mmoitems.api.event.MMOItemReforgeFinishEvent";

    private final MDVRecetasPlugin plugin;
    private final RecipeSignatureService signatureService;
    private final ForjadorModifierService modifierService;
    private boolean warned;

    public MMOItemsRevisionCompatibilityService(
            MDVRecetasPlugin plugin,
            RecipeSignatureService signatureService,
            ForjadorModifierService modifierService
    ) {
        this.plugin = plugin;
        this.signatureService = signatureService;
        this.modifierService = modifierService;
    }

    @SuppressWarnings("unchecked")
    public void register() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("MMOItems")) {
            return;
        }

        try {
            Class<?> rawClass = Class.forName(FINISH_EVENT_CLASS);
            if (!Event.class.isAssignableFrom(rawClass)) {
                plugin.getLogger().warning("MMOItems revision finish event is not a Bukkit Event.");
                return;
            }

            Class<? extends Event> eventClass = (Class<? extends Event>) rawClass;
            Listener listener = new Listener() { };
            EventExecutor executor = (ignored, event) -> handleFinishEvent(event);

            plugin.getServer().getPluginManager().registerEvent(
                    eventClass,
                    listener,
                    EventPriority.HIGHEST,
                    executor,
                    plugin,
                    true
            );
            plugin.getLogger().info("MMOItems Revision ID compatibility registered.");
        } catch (Throwable throwable) {
            plugin.getLogger().warning(
                    "Could not register MMOItems Revision ID compatibility: " + throwable.getMessage());
        }
    }

    private void handleFinishEvent(Event event) {
        try {
            Method getReforger = event.getClass().getMethod("getReforger");
            Object reforger = getReforger.invoke(event);
            if (reforger == null) {
                return;
            }

            Object oldObject = reforger.getClass().getMethod("getStack").invoke(reforger);
            Object revisedObject = event.getClass().getMethod("getFinishedItem").invoke(event);
            if (!(oldObject instanceof ItemStack oldItem)
                    || !(revisedObject instanceof ItemStack revisedItem)) {
                return;
            }

            ItemStack restored = modifierService.restoreAfterRevision(oldItem, revisedItem);
            restored = signatureService.restoreAfterRevision(oldItem, restored);

            event.getClass().getMethod("setFinishedItem", ItemStack.class).invoke(event, restored);
        } catch (Throwable throwable) {
            if (!warned) {
                warned = true;
                plugin.getLogger().warning(
                        "Could not restore MDVRecetas data after an MMOItems revision: "
                                + throwable.getMessage());
            }
        }
    }
}
