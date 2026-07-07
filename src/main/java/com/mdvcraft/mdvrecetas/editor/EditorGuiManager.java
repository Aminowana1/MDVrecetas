package com.mdvcraft.mdvrecetas.editor;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.hook.MDVSocialHook;
import com.mdvcraft.mdvrecetas.hook.MMOItemsHook;
import com.mdvcraft.mdvrecetas.model.ItemSpec;
import com.mdvcraft.mdvrecetas.model.MdvRecipe;
import com.mdvcraft.mdvrecetas.model.RecipeType;
import com.mdvcraft.mdvrecetas.model.StationType;
import com.mdvcraft.mdvrecetas.recipe.MdvRecipeManager;
import com.mdvcraft.mdvrecetas.service.ItemResolver;
import com.mdvcraft.mdvrecetas.util.ColorUtil;
import com.mdvcraft.mdvrecetas.util.ItemStackSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class EditorGuiManager implements Listener {
    private static final int SIZE = 54;
    private static final int[] SHAPED_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int COOKING_INPUT_SLOT = 20;
    private static final int STATION_SLOT = 23;
    private static final int RESULT_SLOT = 25;
    private static final int OPTIONS_SLOT = 27;
    private static final int SAVE_SLOT = 42;
    private static final int CANCEL_SLOT = 43;
    private static final int RESET_SLOT = 44;
    private static final int BACK_SLOT = 49;

    private final MDVRecetasPlugin plugin;
    private final MdvRecipeManager recipeManager;
    private final ItemResolver itemResolver;
    private final MDVSocialHook socialHook;
    private final Map<UUID, EditorSession> sessions = new HashMap<>();
    private final Map<UUID, PendingInput> pendingInputs = new HashMap<>();
    private final Set<UUID> internalTransitions = new java.util.HashSet<>();

    public EditorGuiManager(MDVRecetasPlugin plugin, MdvRecipeManager recipeManager, ItemResolver itemResolver, MDVSocialHook socialHook) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
        this.itemResolver = itemResolver;
        this.socialHook = socialHook;
    }

    public void openStationSelect(Player player) {
        EditorMenuHolder holder = new EditorMenuHolder(EditorMenuHolder.Screen.STATION_SELECT, null);
        Inventory inv = Bukkit.createInventory(holder, SIZE, color(config("editor.titles.station-select", "&8&lEditor de Recetas")));
        holder.setInventory(inv);
        fill(inv);
        renderStationSelect(inv);
        open(player, inv);
        socialHook.play(player, "open");
    }


    public void openEditRecipe(Player player, MdvRecipe recipe) {
        if (recipe == null) {
            player.sendMessage(prefix() + color("&cNo se pudo abrir la receta."));
            socialHook.play(player, "invalid");
            return;
        }
        EditorSession session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new EditorSession());
        loadRecipeIntoSession(session, recipe);
        openCreator(player, recipe.getStation(), true);
    }

    private void openCreator(Player player, StationType station, boolean keepSession) {
        EditorSession session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new EditorSession());
        if (!keepSession) {
            session.clearItems();
            session.setStation(station);
            session.resetOptions();
        }
        EditorMenuHolder holder = new EditorMenuHolder(EditorMenuHolder.Screen.CREATOR, session.getStation());
        Inventory inv = Bukkit.createInventory(holder, SIZE, color(config("editor.titles.creator", "&8&lCrear Receta")));
        holder.setInventory(inv);
        fill(inv);
        renderCreator(inv, session);
        open(player, inv);
        socialHook.play(player, "open");
    }

    private void openOptions(Player player, Inventory previousInventory) {
        EditorSession session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new EditorSession());
        storeItemsToSession(player, previousInventory);
        EditorMenuHolder holder = new EditorMenuHolder(EditorMenuHolder.Screen.OPTIONS, session.getStation());
        Inventory inv = Bukkit.createInventory(holder, SIZE, color(config("editor.titles.options", "&8&lOpciones de Receta")));
        holder.setInventory(inv);
        fill(inv);
        renderOptions(inv, session);
        open(player, inv);
        socialHook.play(player, "open");
    }

    public void closeAllAndReturnEditorItems() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof EditorMenuHolder holder) {
                EditorSession session = sessions.get(player.getUniqueId());
                if (holder.getScreen() == EditorMenuHolder.Screen.CREATOR) {
                    if (session == null || !session.isEditing()) {
                        returnEditorItems(player, holder.getInventory());
                    }
                } else if (holder.getScreen() == EditorMenuHolder.Screen.OPTIONS) {
                    if (session == null || !session.isEditing()) {
                        returnSessionItems(player);
                    }
                }
                player.closeInventory();
            }
        }
        sessions.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof EditorMenuHolder holder)) {
            return;
        }

        int raw = event.getRawSlot();
        boolean top = raw >= 0 && raw < event.getView().getTopInventory().getSize();
        if (!top) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                socialHook.play(player, "invalid");
            }
            return;
        }

        switch (holder.getScreen()) {
            case STATION_SELECT -> {
                event.setCancelled(true);
                handleStationSelect(player, raw);
            }
            case CREATOR -> handleCreatorClick(player, holder, event, raw);
            case OPTIONS -> {
                event.setCancelled(true);
                handleOptionsClick(player, raw, event.getClick());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof EditorMenuHolder holder)) {
            return;
        }
        if (holder.getScreen() != EditorMenuHolder.Screen.CREATOR) {
            event.setCancelled(true);
            return;
        }
        EditorSession session = sessions.get(((Player) event.getWhoClicked()).getUniqueId());
        for (int raw : event.getRawSlots()) {
            if (raw >= 0 && raw < event.getView().getTopInventory().getSize() && !isEditableCreatorSlot(raw, session)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof EditorMenuHolder holder)) {
            return;
        }
        if (internalTransitions.remove(player.getUniqueId())) {
            return;
        }
        EditorSession session = sessions.get(player.getUniqueId());
        if (holder.getScreen() == EditorMenuHolder.Screen.CREATOR) {
            if (session == null || !session.isEditing()) {
                returnEditorItems(player, holder.getInventory());
            }
        } else if (holder.getScreen() == EditorMenuHolder.Screen.OPTIONS) {
            if (session == null || !session.isEditing()) {
                returnSessionItems(player);
            }
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChatInput(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PendingInput input = pendingInputs.get(player.getUniqueId());
        if (input == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage() == null ? "" : event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> handleChatInput(player, input, message));
    }

    private void handleChatInput(Player player, PendingInput input, String message) {
        pendingInputs.remove(player.getUniqueId());
        EditorSession session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new EditorSession());
        if (message.equalsIgnoreCase("cancelar") || message.equalsIgnoreCase("cancel")) {
            player.sendMessage(prefix() + color("&7Entrada cancelada."));
            openCreator(player, session.getStation(), true);
            return;
        }
        if (input == PendingInput.RECIPE_ID) {
            String id = sanitizeRecipeId(message);
            if (id.isBlank()) {
                player.sendMessage(prefix() + color("&cID inválida. Usa letras, números, guion bajo, punto, barra o guion."));
                openCreator(player, session.getStation(), true);
                return;
            }
            if (recipeManager.recipeIdExists(id) && !id.equalsIgnoreCase(session.getEditingRecipeId())) {
                player.sendMessage(prefix() + color("&cYa existe una receta con la ID &e" + id + "&c."));
                openCreator(player, session.getStation(), true);
                return;
            }
            session.setCustomRecipeId(id);
            player.sendMessage(prefix() + color("&aID asignada: &e" + id));
            openCreator(player, session.getStation(), true);
            return;
        }
        if (input == PendingInput.VANILLA_KEY) {
            String key = sanitizeVanillaKey(message);
            if (key.isBlank() || !key.contains(":")) {
                player.sendMessage(prefix() + color("&cKey inválida. Ejemplo: &eminecraft:golden_apple"));
                openCreator(player, session.getStation(), true);
                return;
            }
            session.setVanillaKey(key);
            session.setReplaceVanilla(true);
            player.sendMessage(prefix() + color("&aReceta vanilla a reemplazar: &e" + key));
            openCreator(player, session.getStation(), true);
        }
    }


    private void startChatInput(Player player, PendingInput input) {
        pendingInputs.put(player.getUniqueId(), input);
        internalTransitions.add(player.getUniqueId());
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> internalTransitions.remove(player.getUniqueId()));
        if (input == PendingInput.RECIPE_ID) {
            player.sendMessage(prefix() + color("&eEscribe una ID para esta receta o &ccancelar &epara volver."));
        } else if (input == PendingInput.VANILLA_KEY) {
            player.sendMessage(prefix() + color("&eEscribe la key vanilla a reemplazar. Ejemplo: &fminecraft:golden_apple&e. Usa &ccancelar &epara volver."));
        }
    }

    private void handleStationSelect(Player player, int slot) {
        StationType station = stationBySlot(slot);
        if (station != null) {
            openCreator(player, station, false);
            return;
        }
        if (slot == stationBackSlot()) {
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            socialHook.play(player, "back");
            return;
        }
        socialHook.play(player, "invalid");
    }

    private void handleCreatorClick(Player player, EditorMenuHolder holder, InventoryClickEvent event, int slot) {
        EditorSession session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new EditorSession());
        if (isEditableCreatorSlot(slot, session)) {
            event.setCancelled(false);
            return;
        }
        event.setCancelled(true);
        if (slot == OPTIONS_SLOT) {
            openOptions(player, holder.getInventory());
            return;
        }
        if (slot == SAVE_SLOT) {
            saveRecipe(player, holder.getInventory(), session);
            return;
        }
        if (slot == RESET_SLOT) {
            if (session.isEditing() && session.getOriginalRecipe() != null) {
                holder.getInventory().clear();
                loadRecipeIntoSession(session, session.getOriginalRecipe());
                openCreator(player, session.getStation(), true);
            } else {
                returnEditorItems(player, holder.getInventory());
                session.resetOptions();
                openCreator(player, session.getStation(), true);
            }
            socialHook.play(player, "back");
            return;
        }
        if (slot == CANCEL_SLOT) {
            if (session.isEditing()) {
                deleteEditingRecipe(player, session);
                return;
            }
            returnEditorItems(player, holder.getInventory());
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            socialHook.play(player, "back");
            return;
        }
        if (slot == BACK_SLOT) {
            if (!session.isEditing()) {
                returnEditorItems(player, holder.getInventory());
            }
            sessions.remove(player.getUniqueId());
            openStationSelect(player);
            return;
        }
        socialHook.play(player, "invalid");
    }

    private void handleOptionsClick(Player player, int slot, ClickType click) {
        EditorSession session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new EditorSession());
        if (slot == 10) {
            cycleCategory(session, click.isRightClick() ? -1 : 1);
        } else if (slot == 12) {
            session.setHidden(!session.isHidden());
        } else if (slot == 14 && !session.getStation().isCookingStation()) {
            session.setRecipeType(session.getRecipeType() == RecipeType.SHAPED ? RecipeType.SHAPELESS : RecipeType.SHAPED);
        } else if (slot == 16) {
            double delta = click.isShiftClick() ? 5.0D : 0.5D;
            session.setForjadorExp(session.getForjadorExp() + (click.isRightClick() ? -delta : delta));
        } else if (slot == 18) {
            session.setSignature(!session.isSignature());
        } else if (slot == 20) {
            session.setModifiers(!session.isModifiers());
        } else if (slot == 28 && session.getStation().isCookingStation()) {
            int delta = click.isShiftClick() ? 100 : 20;
            session.setCookingTime(session.getCookingTime() + (click.isRightClick() ? -delta : delta));
        } else if (slot == 30 && session.getStation().isCookingStation()) {
            float delta = click.isShiftClick() ? 1.0F : 0.1F;
            session.setVanillaExp(session.getVanillaExp() + (click.isRightClick() ? -delta : delta));
        } else if (slot == 32) {
            session.setReplaceVanilla(!session.isReplaceVanilla());
        } else if (slot == 34) {
            startChatInput(player, PendingInput.RECIPE_ID);
            return;
        } else if (slot == 36) {
            startChatInput(player, PendingInput.VANILLA_KEY);
            return;
        } else if (slot == 49) {
            openCreator(player, session.getStation(), true);
            return;
        } else {
            socialHook.play(player, "invalid");
            return;
        }
        socialHook.play(player, "default");
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof EditorMenuHolder h) {
            fill(h.getInventory());
            renderOptions(h.getInventory(), session);
        }
    }

    private void renderStationSelect(Inventory inv) {
        inv.setItem(11, button(Material.CRAFTING_TABLE, "&eMesa de crafteo", List.of("&7Crea recetas SHAPED/SHAPELESS.", "", "&eClick para seleccionar.")));
        inv.setItem(12, button(Material.FURNACE, "&eHorno", List.of("&7Crea recetas de cocción.", "", "&eClick para seleccionar.")));
        inv.setItem(13, button(Material.BLAST_FURNACE, "&eAlto horno", List.of("&7Crea recetas de alto horno.", "", "&eClick para seleccionar.")));
        inv.setItem(14, button(Material.CAMPFIRE, "&eHoguera", List.of("&7Crea recetas de hoguera.", "", "&eClick para seleccionar.")));
        inv.setItem(15, button(Material.SMOKER, "&eAhumador", List.of("&7Crea recetas de ahumador.", "", "&eClick para seleccionar.")));
        inv.setItem(stationBackSlot(), button(Material.BARRIER, "&cCerrar", List.of("&7Cierra el editor.")));
    }

    private void renderCreator(Inventory inv, EditorSession session) {
        if (session.getStation().isCookingStation()) {
            inv.setItem(COOKING_INPUT_SLOT, null);
        } else {
            for (int slot : SHAPED_SLOTS) {
                inv.setItem(slot, null);
            }
        }
        inv.setItem(STATION_SLOT, button(stationMaterial(session.getStation()), "&e" + stationName(session.getStation()), List.of(
                "&7Estación seleccionada.",
                "&7Tipo: &f" + recipeTypeName(session.getRecipeType())
        )));
        inv.setItem(RESULT_SLOT, null);
        inv.setItem(OPTIONS_SLOT, button(Material.SPYGLASS, "&bOpciones de receta", List.of(
                "&7ID: &f" + currentSessionIdPreview(session),
                "&7Categoría: &f" + prettyCategory(session.getCategory()),
                "&7Oculta: " + (session.isHidden() ? "&aSí" : "&cNo"),
                "&7XP Forjador: &e" + format(session.getForjadorExp()),
                "&7Firma: " + (session.isSignature() ? "&aSí" : "&cNo"),
                "&7Modificadores: " + (session.isModifiers() ? "&aSí" : "&cNo"),
                "&7Reemplaza vanilla: " + (session.isReplaceVanilla() ? "&aSí" : "&cNo"),
                "", "&eClick para abrir."
        )));
        inv.setItem(SAVE_SLOT, button(Material.LIME_DYE, "&aGuardar receta", List.of("&7Guarda la receta en YAML.", "&7Si está incompleta no se guardará.")));
        inv.setItem(CANCEL_SLOT, button(Material.RED_DYE, session.isEditing() ? "&cEliminar receta" : "&cCancelar", session.isEditing()
                ? List.of("&7Elimina esta receta del YAML.", "&cNo se puede deshacer fácilmente.")
                : List.of("&7Devuelve los items y cierra el editor.")));
        inv.setItem(RESET_SLOT, button(Material.GRAY_DYE, "&7Resetear", session.isEditing()
                ? List.of("&7Restaura la receta original", "&7antes de guardar cambios.")
                : List.of("&7Devuelve los items y resetea opciones.")));
        inv.setItem(BACK_SLOT, button(Material.BARRIER, "&eVolver", List.of("&7Regresa a seleccionar estación.")));
        restoreSessionItems(inv, session);
    }

    private void renderOptions(Inventory inv, EditorSession session) {
        inv.setItem(10, button(Material.COMPASS, "&eCategoría", List.of(
                "&7Actual: &f" + prettyCategory(session.getCategory()),
                "", "&eClick izquierdo: siguiente", "&eClick derecho: anterior"
        )));
        inv.setItem(12, button(session.isHidden() ? Material.LIME_DYE : Material.RED_DYE, "&eOcultar en guía", List.of(
                "&7Estado: " + (session.isHidden() ? "&aOculta" : "&cVisible"),
                "&7Si está oculta, se puede craftear", "&7pero no aparece en /mdvrecetas.", "", "&eClick para alternar."
        )));
        inv.setItem(14, button(session.getStation().isCookingStation() ? Material.FURNACE : Material.CRAFTING_TABLE, "&eTipo de receta", List.of(
                "&7Actual: &f" + recipeTypeName(session.getRecipeType()),
                session.getStation().isCookingStation() ? "&8Las estaciones de horno usan COOKING." : "&eClick para alternar SHAPED/SHAPELESS."
        )));
        inv.setItem(16, button(Material.EXPERIENCE_BOTTLE, "&eXP de Forjador", List.of(
                "&7Actual: &e" + format(session.getForjadorExp()),
                "", "&eIzq: +0.5", "&eDer: -0.5", "&eShift: +/-5"
        )));
        inv.setItem(18, button(session.isSignature() ? Material.LIME_DYE : Material.RED_DYE, "&aFirma de crafteo", List.of(
                "&7Estado: " + (session.isSignature() ? "&aActivada" : "&cDesactivada"),
                "&7Si está activada, el resultado",
                "&7mostrará quién lo fabricó.",
                "", "&eClick para alternar."
        )));
        inv.setItem(20, button(session.isModifiers() ? Material.LIME_DYE : Material.RED_DYE, "&6Modificadores", List.of(
                "&7Estado: " + (session.isModifiers() ? "&aActivados" : "&cDesactivados"),
                "&7Si está activado, MDVRecetas",
                "&7elige un modificador según",
                "&7el nivel de Forjador.",
                "", "&eClick para alternar."
        )));
        if (session.getStation().isCookingStation()) {
            inv.setItem(28, button(Material.CLOCK, "&eTiempo de cocción", List.of(
                    "&7Actual: &e" + session.getCookingTime() + " ticks", "&8(20 ticks = 1 segundo)",
                    "", "&eIzq: +20", "&eDer: -20", "&eShift: +/-100"
            )));
            inv.setItem(30, button(Material.GOLD_NUGGET, "&eXP vanilla", List.of(
                    "&7Actual: &e" + format(session.getVanillaExp()),
                    "", "&eIzq: +0.1", "&eDer: -0.1", "&eShift: +/-1"
            )));
        }
        inv.setItem(32, button(session.isReplaceVanilla() ? Material.LIME_DYE : Material.RED_DYE, "&eReemplazar receta vanilla", List.of(
                "&7Estado: " + (session.isReplaceVanilla() ? "&aSí" : "&cNo"),
                "&7Key: &f" + (session.getVanillaKey().isBlank() ? "Sin asignar" : session.getVanillaKey()),
                "", "&eClick para alternar.", "&6Usa el botón de key para asignarla."
        )));
        inv.setItem(34, button(Material.NAME_TAG, "&eID de receta", List.of(
                "&7Actual: &f" + currentSessionIdPreview(session),
                "&8Si no asignas una ID, se genera automática.",
                "", "&eClick para escribir una ID."
        )));
        inv.setItem(36, button(Material.PAPER, "&eKey vanilla", List.of(
                "&7Actual: &f" + (session.getVanillaKey().isBlank() ? "Sin asignar" : session.getVanillaKey()),
                "&7Ejemplo: &eminecraft:golden_apple",
                "", "&eClick para escribir la key."
        )));
        inv.setItem(49, button(Material.BARRIER, "&eVolver", List.of("&7Regresa al editor de receta.")));
    }


    private void deleteEditingRecipe(Player player, EditorSession session) {
        String id = session.getEditingRecipeId();
        if (id == null || id.isBlank()) {
            socialHook.play(player, "invalid");
            return;
        }
        boolean deleted = recipeManager.deleteRecipeFromFiles(id);
        int count = recipeManager.reloadRecipes();
        sessions.remove(player.getUniqueId());
        player.closeInventory();
        if (deleted) {
            player.sendMessage(prefix() + color("&aReceta &e" + id + " &aeliminada. Recetas cargadas: &e" + count));
            socialHook.play(player, "confirm");
        } else {
            player.sendMessage(prefix() + color("&cNo se encontró la receta &e" + id + " &cen archivos YAML."));
            socialHook.play(player, "invalid");
        }
    }

    private void loadRecipeIntoSession(EditorSession session, MdvRecipe recipe) {
        session.clearItems();
        session.setStation(recipe.getStation());
        session.setRecipeType(recipe.getType());
        session.setCategory(recipe.getCategory());
        session.setHidden(recipe.isHidden());
        session.setCookingTime(recipe.getCookingTime());
        session.setVanillaExp(recipe.getCookingVanillaExp());
        session.setForjadorExp(recipe.getForjador() == null ? 0.0D : recipe.getForjador().getExp());
        session.setSignature(recipe.getForjador() != null && recipe.getForjador().isSignature());
        session.setModifiers(recipe.getForjador() != null && recipe.getForjador().isModifiers());
        session.setCustomRecipeId(recipe.getId());
        session.setEditingRecipeId(recipe.getId());
        session.setOriginalRecipe(recipe);
        session.setReplaceVanilla(recipe.isReplaceVanilla());
        session.setVanillaKey(recipe.getVanillaKey() == null ? "" : recipe.getVanillaKey().toString());

        if (recipe.getType() == RecipeType.COOKING) {
            putSpecInSession(session, COOKING_INPUT_SLOT, recipe.getCookingIngredient());
        } else if (recipe.getType() == RecipeType.SHAPED) {
            for (int i = 0; i < Math.min(9, SHAPED_SLOTS.length); i++) {
                int row = i / 3;
                int col = i % 3;
                String line = row < recipe.getShape().size() ? recipe.getShape().get(row) : "   ";
                char symbol = col < line.length() ? line.charAt(col) : ' ';
                putSpecInSession(session, SHAPED_SLOTS[i], recipe.getShapedIngredients().get(symbol));
            }
        } else {
            int index = 0;
            for (ItemSpec spec : recipe.getShapelessIngredients().values()) {
                if (index >= SHAPED_SLOTS.length) {
                    break;
                }
                putSpecInSession(session, SHAPED_SLOTS[index++], spec);
            }
        }
        putSpecInSession(session, RESULT_SLOT, recipe.getResult());
    }

    private void putSpecInSession(EditorSession session, int slot, ItemSpec spec) {
        if (spec == null) {
            return;
        }
        ItemStack item = itemResolver.buildItem(spec);
        if (item == null || item.getType().isAir()) {
            return;
        }
        item = item.clone();
        item.setAmount(Math.max(1, spec.getAmount()));
        session.getItems().put(slot, item);
    }

    private void saveRecipe(Player player, Inventory inv, EditorSession session) {
        try {
            ItemStack result = inv.getItem(RESULT_SLOT);
            if (isEmpty(result)) {
                player.sendMessage(prefix() + color("&cDebes colocar un resultado."));
                socialHook.play(player, "invalid");
                return;
            }
            String id = determineRecipeId(session);
            if (recipeManager.recipeIdExists(id) && !id.equalsIgnoreCase(session.getEditingRecipeId())) {
                player.sendMessage(prefix() + color("&cYa existe una receta con la ID &e" + id + "&c."));
                socialHook.play(player, "invalid");
                return;
            }
            if (session.isReplaceVanilla() && session.getVanillaKey().isBlank()) {
                player.sendMessage(prefix() + color("&cActiva reemplazar vanilla solo si asignaste una key. Ejemplo: &eminecraft:golden_apple"));
                socialHook.play(player, "invalid");
                return;
            }
            File file = new File(new File(plugin.getDataFolder(), plugin.getConfig().getString("settings.recipe-folder", "recipes")), "editor.yml");
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String base = "recipes." + id;
            yaml.set(base + ".enabled", true);
            yaml.set(base + ".station", session.getStation().name());
            yaml.set(base + ".category", session.getCategory());
            yaml.set(base + ".hidden", session.isHidden());
            yaml.set(base + ".type", session.getRecipeType().name());
            yaml.set(base + ".replace-vanilla.enabled", session.isReplaceVanilla());
            if (session.isReplaceVanilla()) {
                yaml.set(base + ".replace-vanilla.vanilla-key", session.getVanillaKey());
            } else {
                yaml.set(base + ".replace-vanilla.vanilla-key", null);
            }

            if (session.getRecipeType() == RecipeType.COOKING) {
                ItemStack input = inv.getItem(COOKING_INPUT_SLOT);
                if (isEmpty(input)) {
                    player.sendMessage(prefix() + color("&cDebes colocar un ingrediente de cocción."));
                    socialHook.play(player, "invalid");
                    return;
                }
                saveItemSpec(yaml, base + ".ingredient", input);
                yaml.set(base + ".cooking.time", session.getCookingTime());
                yaml.set(base + ".cooking.vanilla-exp", session.getVanillaExp());
            } else if (session.getRecipeType() == RecipeType.SHAPED) {
                if (!hasAnyIngredient(inv)) {
                    player.sendMessage(prefix() + color("&cDebes colocar al menos un ingrediente."));
                    socialHook.play(player, "invalid");
                    return;
                }
                saveShaped(yaml, base, inv);
            } else {
                if (!hasAnyIngredient(inv)) {
                    player.sendMessage(prefix() + color("&cDebes colocar al menos un ingrediente."));
                    socialHook.play(player, "invalid");
                    return;
                }
                saveShapeless(yaml, base, inv);
            }

            saveItemSpec(yaml, base + ".result", result);
            yaml.set(base + ".forjador.exp", session.getForjadorExp());
            yaml.set(base + ".forjador.signature", session.isSignature());
            yaml.set(base + ".forjador.modifiers", session.isModifiers());
            if (session.isEditing()) {
                if (!id.equalsIgnoreCase(session.getEditingRecipeId())) {
                    yaml.set("recipes." + session.getEditingRecipeId(), null);
                }
                recipeManager.deleteRecipeFromFiles(session.getEditingRecipeId());
            }
            yaml.save(file);

            int count = recipeManager.reloadRecipes();
            player.sendMessage(prefix() + color("&aReceta guardada como &e" + id + "&a. Recetas cargadas: &e" + count + "&a."));
            if (!session.isEditing()) {
                returnEditorItems(player, inv);
            }
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            socialHook.play(player, "confirm");
        } catch (Exception exception) {
            player.sendMessage(prefix() + color("&cNo se pudo guardar: " + exception.getMessage()));
            plugin.getLogger().warning("Could not save editor recipe: " + exception.getMessage());
            exception.printStackTrace();
            socialHook.play(player, "invalid");
        }
    }

    private void saveShaped(YamlConfiguration yaml, String base, Inventory inv) {
        char next = 'A';
        List<String> shape = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < 3; col++) {
                int slot = SHAPED_SLOTS[row * 3 + col];
                ItemStack item = inv.getItem(slot);
                if (isEmpty(item)) {
                    line.append(' ');
                    continue;
                }
                char symbol = next++;
                line.append(symbol);
                saveItemSpec(yaml, base + ".ingredients." + symbol, item);
            }
            shape.add(line.toString());
        }
        yaml.set(base + ".shape", shape);
    }

    private void saveShapeless(YamlConfiguration yaml, String base, Inventory inv) {
        int index = 1;
        for (int slot : SHAPED_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (isEmpty(item)) {
                continue;
            }
            saveItemSpec(yaml, base + ".ingredients.item_" + index, item);
            index++;
        }
    }

    private void saveItemSpec(YamlConfiguration yaml, String path, ItemStack raw) {
        ItemStack item = raw.clone();
        int amount = Math.max(1, item.getAmount());
        Optional<MMOItemsHook.MmoIdentity> identity = itemResolver.getMmoItemsHook().readIdentity(item);
        if (identity.isPresent()) {
            yaml.set(path + ".kind", "MMOITEMS");
            yaml.set(path + ".type", identity.get().type());
            yaml.set(path + ".id", identity.get().id());
            yaml.set(path + ".amount", amount);
            return;
        }
        if (itemResolver.isCleanVanilla(item)) {
            yaml.set(path + ".kind", "VANILLA");
            yaml.set(path + ".material", item.getType().name());
            yaml.set(path + ".amount", amount);
            return;
        }
        try {
            yaml.set(path + ".kind", "ITEMSTACK");
            yaml.set(path + ".amount", amount);
            yaml.set(path + ".match", "SIMILAR");
            yaml.set(path + ".data", ItemStackSerializer.toBase64(item));
            yaml.set(path + ".preview.material", item.getType().name());
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                yaml.set(path + ".preview.name", item.getItemMeta().getDisplayName());
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("No se pudo serializar ITEMSTACK", exception);
        }
    }

    private void returnSessionItems(Player player) {
        EditorSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        for (ItemStack item : session.getItems().values()) {
            if (isEmpty(item)) {
                continue;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        session.clearItems();
        player.updateInventory();
    }

    private void returnEditorItems(Player player, Inventory inv) {
        EditorSession currentSession = sessions.get(player.getUniqueId());
        for (int slot : editableSlots(currentSession)) {
            ItemStack item = inv.getItem(slot);
            if (isEmpty(item)) {
                continue;
            }
            inv.setItem(slot, null);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        EditorSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            for (ItemStack item : session.getItems().values()) {
                if (isEmpty(item)) {
                    continue;
                }
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
                for (ItemStack leftover : leftovers.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
            session.clearItems();
        }
        player.updateInventory();
    }

    private void storeItemsToSession(Player player, Inventory previousInventory) {
        EditorSession session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new EditorSession());
        session.clearItems();
        for (int slot : editableSlots(session)) {
            ItemStack item = previousInventory.getItem(slot);
            if (!isEmpty(item)) {
                session.getItems().put(slot, item.clone());
                previousInventory.setItem(slot, null);
            }
        }
    }

    private void restoreSessionItems(Inventory inv, EditorSession session) {
        for (Map.Entry<Integer, ItemStack> entry : session.getItems().entrySet()) {
            inv.setItem(entry.getKey(), entry.getValue() == null ? null : entry.getValue().clone());
        }
        session.clearItems();
    }

    private void open(Player player, Inventory inv) {
        internalTransitions.add(player.getUniqueId());
        player.openInventory(inv);
        Bukkit.getScheduler().runTask(plugin, () -> internalTransitions.remove(player.getUniqueId()));
    }

    private List<Integer> editableSlots(EditorSession session) {
        List<Integer> slots = new ArrayList<>();
        StationType station = session == null ? StationType.CRAFTING_TABLE : session.getStation();
        if (station != null && station.isCookingStation()) {
            slots.add(COOKING_INPUT_SLOT);
        } else {
            for (int slot : SHAPED_SLOTS) {
                slots.add(slot);
            }
        }
        slots.add(RESULT_SLOT);
        return slots;
    }

    private boolean isEditableCreatorSlot(int slot, EditorSession session) {
        if (slot == RESULT_SLOT) {
            return true;
        }
        StationType station = session == null ? StationType.CRAFTING_TABLE : session.getStation();
        if (station != null && station.isCookingStation()) {
            return slot == COOKING_INPUT_SLOT;
        }
        for (int shapedSlot : SHAPED_SLOTS) {
            if (slot == shapedSlot) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyIngredient(Inventory inv) {
        for (int slot : SHAPED_SLOTS) {
            if (!isEmpty(inv.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private StationType stationBySlot(int slot) {
        return switch (slot) {
            case 11 -> StationType.CRAFTING_TABLE;
            case 12 -> StationType.FURNACE;
            case 13 -> StationType.BLAST_FURNACE;
            case 14 -> StationType.CAMPFIRE;
            case 15 -> StationType.SMOKER;
            default -> null;
        };
    }

    private void cycleCategory(EditorSession session, int direction) {
        List<String> ids = new ArrayList<>(categories().keySet());
        if (ids.isEmpty()) {
            return;
        }
        int index = ids.indexOf(session.getCategory());
        if (index < 0) {
            index = 0;
        }
        int next = Math.floorMod(index + direction, ids.size());
        session.setCategory(ids.get(next));
    }

    private String determineRecipeId(EditorSession session) {
        String custom = session.getCustomRecipeId();
        if (custom != null && !custom.isBlank()) {
            return sanitizeRecipeId(custom);
        }
        String id;
        do {
            id = generateRecipeId(session);
        } while (recipeManager.recipeIdExists(id));
        return id;
    }

    private String currentSessionIdPreview(EditorSession session) {
        String custom = session.getCustomRecipeId();
        if (custom != null && !custom.isBlank()) {
            return sanitizeRecipeId(custom);
        }
        if (session.getEditingRecipeId() != null && !session.getEditingRecipeId().isBlank()) {
            return session.getEditingRecipeId();
        }
        return "Automática";
    }

    private String generateRecipeId(EditorSession session) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return ("editor_" + session.getStation().name() + "_" + time).toLowerCase(Locale.ROOT);
    }

    private String sanitizeRecipeId(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9_./-]", "_").replaceAll("_+", "_");
    }

    private String sanitizeVanillaKey(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9_./:-]", "_");
        if (!value.contains(":")) {
            value = "minecraft:" + value;
        }
        return value;
    }

    private void fill(Inventory inv) {
        ItemStack filler = button(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            List<String> colored = new ArrayList<>();
            for (String line : lore) {
                colored.add(color(line));
            }
            meta.setLore(colored);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Map<String, CategoryInfo> categories() {
        LinkedHashMap<String, CategoryInfo> result = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("gui.categories");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection category = section.getConfigurationSection(id);
                if (category == null) {
                    continue;
                }
                result.put(id.toUpperCase(Locale.ROOT), new CategoryInfo(id.toUpperCase(Locale.ROOT), category.getString("name", prettyCategory(id))));
            }
        }
        if (result.isEmpty()) {
            result.put("MATERIALES", new CategoryInfo("MATERIALES", "Materiales"));
        }
        return result;
    }

    private String stationName(StationType station) {
        return switch (station) {
            case CRAFTING_TABLE -> "Mesa de crafteo";
            case FURNACE -> "Horno";
            case BLAST_FURNACE -> "Alto horno";
            case SMOKER -> "Ahumador";
            case CAMPFIRE -> "Hoguera";
        };
    }

    private Material stationMaterial(StationType station) {
        return switch (station) {
            case CRAFTING_TABLE -> Material.CRAFTING_TABLE;
            case FURNACE -> Material.FURNACE;
            case BLAST_FURNACE -> Material.BLAST_FURNACE;
            case SMOKER -> Material.SMOKER;
            case CAMPFIRE -> Material.CAMPFIRE;
        };
    }

    private String recipeTypeName(RecipeType type) {
        return switch (type) {
            case SHAPED -> "Con forma";
            case SHAPELESS -> "Sin forma";
            case COOKING -> "Cocción";
        };
    }

    private String prettyCategory(String category) {
        if (category == null) {
            return "General";
        }
        String lower = color(category).replace('&', '§').replace('_', ' ');
        if (lower.contains("§")) {
            return lower;
        }
        lower = category.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder builder = new StringBuilder();
        for (String part : lower.split(" ")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private int stationBackSlot() {
        return plugin.getConfig().getInt("editor.station-select.close-slot", 40);
    }

    private String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.00001) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String config(String path, String fallback) {
        return plugin.getConfig().getString(path, fallback);
    }

    private String prefix() {
        return color(plugin.getConfig().getString("messages.prefix", "&8[&6MDVRecetas&8] &r"));
    }

    private String color(String text) {
        return ColorUtil.color(text == null ? "" : text);
    }

    private enum PendingInput {
        RECIPE_ID,
        VANILLA_KEY
    }

    private record CategoryInfo(String id, String displayName) {
    }
}
