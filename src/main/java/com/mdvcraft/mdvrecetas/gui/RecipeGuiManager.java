package com.mdvcraft.mdvrecetas.gui;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.hook.MDVSocialHook;
import com.mdvcraft.mdvrecetas.model.ItemSpec;
import com.mdvcraft.mdvrecetas.model.MdvRecipe;
import com.mdvcraft.mdvrecetas.model.RecipeType;
import com.mdvcraft.mdvrecetas.model.StationType;
import com.mdvcraft.mdvrecetas.recipe.MdvRecipeManager;
import com.mdvcraft.mdvrecetas.service.ItemResolver;
import com.mdvcraft.mdvrecetas.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RecipeGuiManager implements Listener {
    private static final int DEFAULT_SIZE = 54;
    private static final int DEFAULT_SEARCH_CENTER = 37;

    private final MDVRecetasPlugin plugin;
    private final MdvRecipeManager recipeManager;
    private final ItemResolver itemResolver;
    private final MDVSocialHook socialHook;

    public RecipeGuiManager(MDVRecetasPlugin plugin, MdvRecipeManager recipeManager, ItemResolver itemResolver, MDVSocialHook socialHook) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
        this.itemResolver = itemResolver;
        this.socialHook = socialHook;
    }

    public void openMain(Player player) {
        openMain(player, firstCategory(), 0);
    }

    public void openMain(Player player, String category, int page) {
        RecipeMenuHolder holder = new RecipeMenuHolder(RecipeMenuHolder.Screen.MAIN, normalizeCategory(category), Math.max(0, page), null);
        Inventory inventory = Bukkit.createInventory(holder, DEFAULT_SIZE, color(configString("gui.title", "&8&lGuía de Recetas")));
        holder.setInventory(inventory);
        renderMain(holder, null);
        player.openInventory(inventory);
        socialHook.play(player, "open");
    }

    public void openRecipe(Player player, MdvRecipe recipe) {
        RecipeMenuHolder holder = new RecipeMenuHolder(RecipeMenuHolder.Screen.RECIPE, recipe.getCategory(), 0, recipe);
        Inventory inventory = Bukkit.createInventory(holder, DEFAULT_SIZE, color(configString("gui.recipe-title", "&8&lVista de Receta")));
        holder.setInventory(inventory);
        renderRecipe(holder);
        player.openInventory(inventory);
        socialHook.play(player, "open");
    }

    public void closeAllAndReturnSearchItems() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof RecipeMenuHolder holder) {
                returnSearchItem(player, holder.getInventory());
                player.closeInventory();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof RecipeMenuHolder holder)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        boolean topClick = rawSlot >= 0 && rawSlot < event.getView().getTopInventory().getSize();

        if (!topClick) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        if (holder.getScreen() == RecipeMenuHolder.Screen.MAIN && rawSlot == searchCenterSlot()) {
            event.setCancelled(false);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof RecipeMenuHolder currentHolder
                        && currentHolder.getScreen() == RecipeMenuHolder.Screen.MAIN) {
                    currentHolder.setPage(0);
                    renderMain(currentHolder, currentHolder.getInventory().getItem(searchCenterSlot()));
                    socialHook.play(player, "open");
                }
            });
            return;
        }

        event.setCancelled(true);

        if (holder.getScreen() == RecipeMenuHolder.Screen.MAIN) {
            handleMainClick(player, holder, rawSlot);
        } else if (holder.getScreen() == RecipeMenuHolder.Screen.RECIPE) {
            handleRecipeClick(player, holder, rawSlot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof RecipeMenuHolder holder)) {
            return;
        }
        if (holder.getScreen() != RecipeMenuHolder.Screen.MAIN) {
            event.setCancelled(true);
            return;
        }
        int center = searchCenterSlot();
        boolean touchesTop = false;
        boolean onlyCenter = true;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < event.getView().getTopInventory().getSize()) {
                touchesTop = true;
                if (rawSlot != center) {
                    onlyCenter = false;
                    break;
                }
            }
        }
        if (touchesTop && !onlyCenter) {
            event.setCancelled(true);
            return;
        }
        if (touchesTop) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof RecipeMenuHolder currentHolder
                        && currentHolder.getScreen() == RecipeMenuHolder.Screen.MAIN) {
                    currentHolder.setPage(0);
                    renderMain(currentHolder, currentHolder.getInventory().getItem(center));
                    socialHook.play(player, "open");
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof RecipeMenuHolder holder && holder.getScreen() == RecipeMenuHolder.Screen.MAIN) {
            returnSearchItem(player, event.getInventory());
        }
    }

    private void handleMainClick(Player player, RecipeMenuHolder holder, int slot) {
        if (slot == closeSlot()) {
            socialHook.play(player, "close");
            player.closeInventory();
            return;
        }
        if (slot == previousSlot()) {
            if (holder.getPage() > 0) {
                holder.setPage(holder.getPage() - 1);
                renderMain(holder, holder.getInventory().getItem(searchCenterSlot()));
                socialHook.play(player, "page");
            } else {
                socialHook.play(player, "invalid");
            }
            return;
        }
        if (slot == nextSlot()) {
            List<MdvRecipe> list = currentRecipeList(holder, holder.getInventory().getItem(searchCenterSlot()));
            int maxPage = maxPage(list.size());
            if (holder.getPage() < maxPage) {
                holder.setPage(holder.getPage() + 1);
                renderMain(holder, holder.getInventory().getItem(searchCenterSlot()));
                socialHook.play(player, "page");
            } else {
                socialHook.play(player, "invalid");
            }
            return;
        }

        String category = categoryBySlot(slot);
        if (category != null) {
            returnSearchItem(player, holder.getInventory());
            holder.getInventory().setItem(searchCenterSlot(), null);
            holder.setCategory(category);
            holder.setPage(0);
            renderMain(holder, null);
            socialHook.play(player, "open");
            return;
        }

        MdvRecipe recipe = holder.getRecipeSlots().get(slot);
        if (recipe != null) {
            openRecipe(player, recipe);
        }
    }

    private void handleRecipeClick(Player player, RecipeMenuHolder holder, int slot) {
        if (slot == closeSlot()) {
            socialHook.play(player, "close");
            player.closeInventory();
            return;
        }
        if (slot == backSlot()) {
            openMain(player, holder.getCategory(), 0);
            socialHook.play(player, "back");
        }
    }

    private void renderMain(RecipeMenuHolder holder, ItemStack searchItem) {
        Inventory inventory = holder.getInventory();
        inventory.clear();
        holder.clearRecipeSlots();

        drawCategories(inventory, holder.getCategory());
        drawSearchBox(inventory, searchItem);

        List<MdvRecipe> recipes = currentRecipeList(holder, searchItem);
        recipes.sort(Comparator.comparing(MdvRecipe::getId));
        List<Integer> resultSlots = resultSlots();
        int pageSize = resultSlots.size();
        int maxPage = maxPage(recipes.size());
        if (holder.getPage() > maxPage) {
            holder.setPage(maxPage);
        }
        int start = holder.getPage() * pageSize;
        for (int i = 0; i < pageSize; i++) {
            int index = start + i;
            if (index >= recipes.size()) {
                break;
            }
            MdvRecipe recipe = recipes.get(index);
            int slot = resultSlots.get(i);
            inventory.setItem(slot, displayResult(recipe));
            holder.getRecipeSlots().put(slot, recipe);
        }

        boolean searchMode = searchItem != null && !searchItem.getType().isAir();
        inventory.setItem(infoSlot(), infoItem(holder, recipes.size(), searchMode));
        inventory.setItem(previousSlot(), button(Material.ARROW, "&eAnterior", List.of("&7Página " + (holder.getPage() + 1) + " / " + (maxPage + 1))));
        inventory.setItem(nextSlot(), button(Material.ARROW, "&eSiguiente", List.of("&7Página " + (holder.getPage() + 1) + " / " + (maxPage + 1))));
        inventory.setItem(closeSlot(), button(Material.BARRIER, "&cCerrar", List.of("&7Cierra este menú.")));
    }

    private void renderRecipe(RecipeMenuHolder holder) {
        Inventory inventory = holder.getInventory();
        inventory.clear();
        MdvRecipe recipe = holder.getRecipe();

        fillBorders(inventory);
        inventory.setItem(4, stationItem(recipe.getStation(), recipe));
        inventory.setItem(22, button(Material.SPECTRAL_ARROW, "&eResultado", List.of("&7Los ingredientes crean este objeto.")));
        inventory.setItem(24, displayResult(recipe));

        if (recipe.getType() == RecipeType.SHAPED) {
            renderShapedRecipe(inventory, recipe);
        } else if (recipe.getType() == RecipeType.SHAPELESS) {
            renderShapelessRecipe(inventory, recipe);
        } else {
            renderCookingRecipe(inventory, recipe);
        }

        inventory.setItem(backSlot(), button(Material.ARROW, "&6Volver", List.of("&7Regresa a la lista de recetas.")));
        inventory.setItem(closeSlot(), button(Material.BARRIER, "&cCerrar", List.of("&7Cierra este menú.")));
    }

    private void renderShapedRecipe(Inventory inventory, MdvRecipe recipe) {
        int[][] grid = {{10, 11, 12}, {19, 20, 21}, {28, 29, 30}};
        for (int row = 0; row < 3; row++) {
            String line = row < recipe.getShape().size() ? recipe.getShape().get(row) : "   ";
            for (int col = 0; col < 3; col++) {
                char symbol = col < line.length() ? line.charAt(col) : ' ';
                ItemSpec spec = recipe.getShapedIngredients().get(symbol);
                inventory.setItem(grid[row][col], spec == null ? emptySlot() : ingredientDisplay(spec));
            }
        }
        inventory.setItem(14, button(Material.CRAFTING_TABLE, "&eReceta con forma", List.of("&7La posición de los ingredientes importa.")));
    }

    private void renderShapelessRecipe(Inventory inventory, MdvRecipe recipe) {
        int[] slots = {10, 11, 12, 19, 20, 21, 28, 29, 30};
        int index = 0;
        for (ItemSpec spec : recipe.getShapelessIngredients().values()) {
            if (index >= slots.length) {
                break;
            }
            inventory.setItem(slots[index++], ingredientDisplay(spec));
        }
        while (index < slots.length) {
            inventory.setItem(slots[index++], emptySlot());
        }
        inventory.setItem(14, button(Material.CRAFTING_TABLE, "&eReceta sin forma", List.of("&7Solo importan los ingredientes.", "&7El orden no importa.")));
    }

    private void renderCookingRecipe(Inventory inventory, MdvRecipe recipe) {
        inventory.setItem(20, ingredientDisplay(recipe.getCookingIngredient()));
        inventory.setItem(14, button(Material.COAL, "&6Cocción", List.of("&7Tiempo: &e" + recipe.getCookingTime() + " ticks", "&7EXP vanilla: &e" + recipe.getCookingVanillaExp())));
    }

    private void drawCategories(Inventory inventory, String selectedCategory) {
        for (CategoryInfo category : categories().values()) {
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(category.id().equalsIgnoreCase(selectedCategory) ? "&aSeleccionada" : "&eClick para ver recetas.");
            inventory.setItem(category.slot(), button(category.icon(), category.name(), lore));
        }
    }

    private void drawSearchBox(Inventory inventory, ItemStack searchItem) {
        ItemStack border = button(Material.GRAY_STAINED_GLASS_PANE, "&8Buscador", List.of("&7Pon un objeto en el centro", "&7para ver qué puedes fabricar."));
        for (int slot : searchBorderSlots()) {
            inventory.setItem(slot, border);
        }
        if (searchItem == null || searchItem.getType().isAir()) {
            inventory.setItem(searchCenterSlot(), null);
        } else {
            inventory.setItem(searchCenterSlot(), searchItem);
        }
    }

    private List<MdvRecipe> currentRecipeList(RecipeMenuHolder holder, ItemStack searchItem) {
        if (searchItem != null && !searchItem.getType().isAir()) {
            return new ArrayList<>(recipeManager.findRecipesUsing(searchItem));
        }
        return recipeManager.getByCategory(holder.getCategory());
    }

    private ItemStack displayResult(MdvRecipe recipe) {
        ItemStack item = itemResolver.buildItem(recipe.getResult());
        if (item == null || item.getType().isAir()) {
            item = new ItemStack(Material.BARRIER);
        }
        item = item.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(color(""));
            lore.add(color("&8ID: &7" + recipe.getId()));
            lore.add(color("&7Estación: &e" + stationName(recipe.getStation())));
            lore.add(color("&7Categoría: &f" + prettyCategory(recipe.getCategory())));
            if (recipe.getForjador().getExp() > 0) {
                lore.add(color("&7Forjador: &e+" + formatDouble(recipe.getForjador().getExp()) + " EXP"));
            }
            lore.add(color(""));
            lore.add(color("&eClick para ver la receta."));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack ingredientDisplay(ItemSpec spec) {
        ItemStack item = itemResolver.buildItem(spec);
        if (item == null || item.getType().isAir()) {
            return button(Material.BARRIER, "&cIngrediente inválido", List.of("&7Revisa el YAML de la receta."));
        }
        item = item.clone();
        item.setAmount(Math.max(1, spec.getAmount()));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(color(""));
            lore.add(color("&7Cantidad: &e" + spec.getAmount()));
            lore.add(color("&8Tipo interno: &7" + spec.getKind().name()));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack stationItem(StationType station, MdvRecipe recipe) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7Dónde se fabrica:");
        lore.add("&e" + stationName(station));
        lore.add("");
        lore.add("&7Tipo: &f" + recipe.getType().name());
        return button(stationMaterial(station), "&6&l" + stationName(station), lore);
    }

    private ItemStack infoItem(RecipeMenuHolder holder, int count, boolean searchMode) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (searchMode) {
            lore.add("&7Mostrando recetas que usan");
            lore.add("&7el objeto del buscador.");
        } else {
            lore.add("&7Categoría actual:");
            lore.add("&e" + prettyCategory(holder.getCategory()));
        }
        lore.add("");
        lore.add("&7Recetas encontradas: &e" + count);
        return button(Material.BOOK, "&6Información", lore);
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            List<String> coloredLore = new ArrayList<>();
            if (lore != null) {
                for (String line : lore) {
                    coloredLore.add(color(line));
                }
            }
            meta.setLore(coloredLore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack emptySlot() {
        return button(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&8Vacío", List.of());
    }

    private void fillBorders(Inventory inventory) {
        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private void returnSearchItem(Player player, Inventory inventory) {
        if (inventory == null) {
            return;
        }
        ItemStack item = inventory.getItem(searchCenterSlot());
        if (item == null || item.getType().isAir()) {
            return;
        }
        inventory.setItem(searchCenterSlot(), null);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
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
                Material icon = Material.matchMaterial(category.getString("icon", "BOOK"));
                result.put(normalizeCategory(id), new CategoryInfo(
                        normalizeCategory(id),
                        category.getString("name", "&f" + prettyCategory(id)),
                        icon == null ? Material.BOOK : icon,
                        category.getInt("slot", result.size())
                ));
            }
        }
        if (!result.isEmpty()) {
            return result;
        }
        String[] defaults = {"ARMAS_CUERPO_A_CUERPO", "ARMAS_A_DISTANCIA", "ARMAS_MAGICAS", "SOPORTE_MAGICO", "ARMADURAS", "HERRAMIENTAS", "AGRICULTURA", "CONSUMIBLES", "MATERIALES", "REPARACIONES", "UTILITARIOS"};
        Material[] icons = {Material.IRON_SWORD, Material.BOW, Material.BLAZE_ROD, Material.ENCHANTED_BOOK, Material.IRON_CHESTPLATE, Material.IRON_PICKAXE, Material.WHEAT, Material.HONEY_BOTTLE, Material.IRON_INGOT, Material.ANVIL, Material.COMPASS};
        int[] slots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17};
        for (int i = 0; i < defaults.length; i++) {
            result.put(defaults[i], new CategoryInfo(defaults[i], "&f" + prettyCategory(defaults[i]), icons[i], slots[i]));
        }
        return result;
    }

    private String firstCategory() {
        Map<String, CategoryInfo> categories = categories();
        if (!categories.isEmpty()) {
            return categories.keySet().iterator().next();
        }
        Collection<MdvRecipe> recipes = recipeManager.getRecipes();
        if (!recipes.isEmpty()) {
            return recipes.iterator().next().getCategory();
        }
        return "GENERAL";
    }

    private String categoryBySlot(int slot) {
        for (CategoryInfo category : categories().values()) {
            if (category.slot() == slot) {
                return category.id();
            }
        }
        return null;
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return firstCategory();
        }
        return category.toUpperCase(Locale.ROOT);
    }

    private String prettyCategory(String category) {
        if (category == null) {
            return "General";
        }
        String lower = category.toLowerCase(Locale.ROOT).replace('_', ' ');
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

    private int maxPage(int size) {
        int pageSize = Math.max(1, resultSlots().size());
        return Math.max(0, (int) Math.ceil(size / (double) pageSize) - 1);
    }

    private List<Integer> resultSlots() {
        return plugin.getConfig().getIntegerList("gui.recipe-list-slots").isEmpty()
                ? List.of(18, 19, 20, 21, 22, 23, 24, 25, 26, 30, 31, 32, 33, 34, 35, 39, 40, 41, 42, 43, 44)
                : plugin.getConfig().getIntegerList("gui.recipe-list-slots");
    }

    private List<Integer> searchBorderSlots() {
        return plugin.getConfig().getIntegerList("gui.search.border-slots").isEmpty()
                ? List.of(27, 28, 29, 36, 38, 45, 46, 47)
                : plugin.getConfig().getIntegerList("gui.search.border-slots");
    }

    private int searchCenterSlot() {
        return plugin.getConfig().getInt("gui.search.center-slot", DEFAULT_SEARCH_CENTER);
    }

    private int previousSlot() {
        return plugin.getConfig().getInt("gui.buttons.previous-slot", 48);
    }

    private int infoSlot() {
        return plugin.getConfig().getInt("gui.buttons.info-slot", 49);
    }

    private int nextSlot() {
        return plugin.getConfig().getInt("gui.buttons.next-slot", 50);
    }

    private int backSlot() {
        return plugin.getConfig().getInt("gui.buttons.back-slot", 49);
    }

    private int closeSlot() {
        return plugin.getConfig().getInt("gui.buttons.close-slot", 53);
    }

    private String configString(String path, String fallback) {
        return plugin.getConfig().getString(path, fallback);
    }

    private String color(String text) {
        return ColorUtil.color(text);
    }

    private String formatDouble(double value) {
        if (Math.abs(value - Math.round(value)) < 0.0001D) {
            return String.valueOf((long) Math.round(value));
        }
        return String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private record CategoryInfo(String id, String name, Material icon, int slot) {
    }
}
