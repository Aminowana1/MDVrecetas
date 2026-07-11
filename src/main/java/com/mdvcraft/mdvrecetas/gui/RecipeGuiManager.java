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
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RecipeGuiManager implements Listener {
    private static final int DEFAULT_SIZE = 54;
    private static final int DEFAULT_SEARCH_CENTER = 40;

    private final MDVRecetasPlugin plugin;
    private final MdvRecipeManager recipeManager;
    private final ItemResolver itemResolver;
    private final MDVSocialHook socialHook;
    private final NamespacedKey guiItemKey;

    private final Map<UUID, SearchSession> searchSessions = new HashMap<>();
    private final Map<UUID, Integer> lastBottomClickSlot = new HashMap<>();
    private final Set<UUID> internalTransitions = new HashSet<>();

    public RecipeGuiManager(MDVRecetasPlugin plugin, MdvRecipeManager recipeManager, ItemResolver itemResolver, MDVSocialHook socialHook) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
        this.itemResolver = itemResolver;
        this.socialHook = socialHook;
        this.guiItemKey = new NamespacedKey(plugin, "gui_item");

        long linkedCycleTicks = Math.max(5L, plugin.getConfig().getLong("gui.recipe.linked-cycle-ticks", 30L));
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickLinkedRecipeViews, linkedCycleTicks, linkedCycleTicks);
    }

    public void openMain(Player player) {
        openMain(player, 0);
    }

    public void openMain(Player player, int categoryPage) {
        RecipeMenuHolder holder = new RecipeMenuHolder(RecipeMenuHolder.Screen.MAIN, firstCategory(), Math.max(0, categoryPage), null, RecipeMenuHolder.BackTarget.MDVSOCIAL);
        Inventory inventory = Bukkit.createInventory(holder, DEFAULT_SIZE, color(configString("gui.titles.main", "&8&lGuía de Recetas")));
        holder.setInventory(inventory);
        renderMain(holder);
        openInventory(player, inventory);
        socialHook.play(player, "open");
    }

    public void openAdminMain(Player player) {
        openAdminMain(player, 0);
    }

    public void openAdminMain(Player player, int categoryPage) {
        RecipeMenuHolder holder = new RecipeMenuHolder(RecipeMenuHolder.Screen.MAIN, firstCategory(), Math.max(0, categoryPage), null, RecipeMenuHolder.BackTarget.MAIN);
        holder.setAdminMode(true);
        Inventory inventory = Bukkit.createInventory(holder, DEFAULT_SIZE, color(configString("gui.titles.admin-main", "&8&lAdmin Recetas")));
        holder.setInventory(inventory);
        renderMain(holder);
        openInventory(player, inventory);
        socialHook.play(player, "open");
    }

    public void openCategory(Player player, String category, int page) {
        RecipeMenuHolder holder = new RecipeMenuHolder(RecipeMenuHolder.Screen.CATEGORY, normalizeCategory(category), Math.max(0, page), null, RecipeMenuHolder.BackTarget.MAIN);
        Inventory inventory = Bukkit.createInventory(holder, DEFAULT_SIZE, categoryTitle(normalizeCategory(category)));
        holder.setInventory(inventory);
        renderCategory(holder);
        openInventory(player, inventory);
        socialHook.play(player, "open");
    }

    private void openAdminCategory(Player player, String category, int page) {
        RecipeMenuHolder holder = new RecipeMenuHolder(RecipeMenuHolder.Screen.CATEGORY, normalizeCategory(category), Math.max(0, page), null, RecipeMenuHolder.BackTarget.MAIN);
        holder.setAdminMode(true);
        Inventory inventory = Bukkit.createInventory(holder, DEFAULT_SIZE, color(configString("gui.titles.admin-category", "&8&lAdmin %category%").replace("%category%", prettyCategory(normalizeCategory(category)))));
        holder.setInventory(inventory);
        renderCategory(holder);
        openInventory(player, inventory);
        socialHook.play(player, "open");
    }

    public void openRecipe(Player player, MdvRecipe recipe) {
        openRecipeFromCategory(player, recipe, recipe.getCategory(), 0);
    }

    private void openRecipeFromCategory(Player player, MdvRecipe recipe, String category, int page) {
        RecipeMenuHolder holder = new RecipeMenuHolder(RecipeMenuHolder.Screen.RECIPE, normalizeCategory(category), Math.max(0, page), recipe, RecipeMenuHolder.BackTarget.CATEGORY);
        Inventory inventory = Bukkit.createInventory(holder, DEFAULT_SIZE, color(configString("gui.titles.recipe", "&8&lVista de Receta")));
        holder.setInventory(inventory);
        renderRecipe(holder);
        openInventory(player, inventory);
        socialHook.play(player, "open");
    }

    private void openRecipeFromSearch(Player player, MdvRecipe recipe, int searchPage) {
        RecipeMenuHolder holder = new RecipeMenuHolder(RecipeMenuHolder.Screen.RECIPE, recipe.getCategory(), searchPage, recipe, RecipeMenuHolder.BackTarget.SEARCH);
        Inventory inventory = Bukkit.createInventory(holder, DEFAULT_SIZE, color(configString("gui.titles.recipe", "&8&lVista de Receta")));
        holder.setInventory(inventory);
        renderRecipe(holder);
        openInventory(player, inventory);
        socialHook.play(player, "open");
    }

    private void openRecipeFromRecipe(Player player, MdvRecipe recipe, RecipeMenuHolder currentHolder) {
        RecipeMenuHolder holder = new RecipeMenuHolder(RecipeMenuHolder.Screen.RECIPE, currentHolder.getCategory(), currentHolder.getPage(), recipe, RecipeMenuHolder.BackTarget.RECIPE);
        List<RecipeMenuHolder.RecipeBackState> stack = new ArrayList<>(currentHolder.getRecipeBackStack());
        if (currentHolder.getRecipe() != null) {
            stack.add(new RecipeMenuHolder.RecipeBackState(
                    currentHolder.getRecipe(),
                    currentHolder.getCategory(),
                    currentHolder.getPage(),
                    currentHolder.getBackTarget()
            ));
        }
        holder.setRecipeBackStack(stack);
        Inventory inventory = Bukkit.createInventory(holder, DEFAULT_SIZE, color(configString("gui.titles.recipe", "&8&lVista de Receta")));
        holder.setInventory(inventory);
        renderRecipe(holder);
        openInventory(player, inventory);
        socialHook.play(player, "open");
    }

    public void closeAllAndReturnSearchItems() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof RecipeMenuHolder holder) {
                returnSearchItem(player, holder);
                // Remove every preview before closing. During plugin disable/reload,
                // scheduled cleanup tasks may never run because Bukkit cancels them.
                holder.getInventory().clear();
                player.closeInventory();
                cleanupEscapedGuiItems(player);
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

        // Safety net: Bukkit/Minecraft can briefly put a decorative GUI item on the
        // cursor when a custom inventory is closed or swapped very quickly. If that
        // happens, never let the player place/drop it into their real inventory.
        if (isTaggedGuiItem(event.getCursor()) || isTaggedGuiItem(player.getItemOnCursor())) {
            event.setCancelled(true);
            cleanupCursorIfGuiItem(player);
            Bukkit.getScheduler().runTask(plugin, () -> cleanupEscapedGuiItems(player));
            return;
        }

        if (rawSlot < 0) {
            cleanupCursorIfGuiItem(player);
            return;
        }

        if (!topClick) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                socialHook.play(player, "invalid");
                return;
            }
            if (event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()) {
                lastBottomClickSlot.put(player.getUniqueId(), event.getSlot());
            }
            return;
        }

        if (isSearchCenter(holder, rawSlot)) {
            event.setCancelled(false);
            Bukkit.getScheduler().runTask(plugin, () -> handleSearchCenterChanged(player));
            return;
        }

        event.setCancelled(true);

        switch (holder.getScreen()) {
            case MAIN -> handleMainClick(player, holder, rawSlot);
            case CATEGORY -> handleCategoryClick(player, holder, rawSlot, event.getClick());
            case SEARCH -> handleSearchClick(player, holder, rawSlot);
            case RECIPE -> handleRecipeClick(player, holder, rawSlot);
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
        int center = searchCenterSlot();
        if (holder.getScreen() != RecipeMenuHolder.Screen.MAIN && holder.getScreen() != RecipeMenuHolder.Screen.SEARCH) {
            event.setCancelled(true);
            return;
        }
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
            Bukkit.getScheduler().runTask(plugin, () -> handleSearchCenterChanged(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof RecipeMenuHolder holder)) {
            return;
        }
        if (internalTransitions.contains(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> cleanupEscapedGuiItems(player));
            return;
        }
        returnSearchItem(player, holder);
        Bukkit.getScheduler().runTask(plugin, () -> cleanupEscapedGuiItems(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        if (!isTaggedGuiItem(event.getItemDrop().getItemStack())) {
            return;
        }
        // Do not cancel: cancelling may try to return the fake item to a full
        // inventory/cursor. Removing the entity makes the decorative item vanish.
        event.getItemDrop().remove();
        cleanupCursorIfGuiItem(event.getPlayer());
        Bukkit.getScheduler().runTask(plugin, () -> cleanupEscapedGuiItems(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPickup(EntityPickupItemEvent event) {
        if (!isTaggedGuiItem(event.getItem().getItemStack())) {
            return;
        }
        event.setCancelled(true);
        event.getItem().remove();
    }

    private void handleMainClick(Player player, RecipeMenuHolder holder, int slot) {
        if (slot == mainBackSlot()) {
            returnSearchItem(player, holder);
            socialHook.play(player, "back");
            if (holder.isAdminMode()) {
                player.closeInventory();
            } else {
                openMdvSocial(player);
            }
            return;
        }
        List<CategoryInfo> categoryList = new ArrayList<>(categories().values());
        int pageSize = categorySlots().size();
        int maxPage = maxPage(categoryList.size(), pageSize);
        if (slot == mainPreviousSlot() && maxPage > 0) {
            if (holder.getPage() > 0) {
                holder.setPage(holder.getPage() - 1);
                renderMain(holder);
                socialHook.play(player, "page");
            } else {
                socialHook.play(player, "invalid");
            }
            return;
        }
        if (slot == mainNextSlot() && maxPage > 0) {
            if (holder.getPage() < maxPage) {
                holder.setPage(holder.getPage() + 1);
                renderMain(holder);
                socialHook.play(player, "page");
            } else {
                socialHook.play(player, "invalid");
            }
            return;
        }
        String category = categoryByVisibleSlot(slot, holder.getPage());
        if (category != null) {
            if (holder.isAdminMode()) {
                openAdminCategory(player, category, 0);
            } else {
                openCategory(player, category, 0);
            }
            return;
        }
    }

    private void handleCategoryClick(Player player, RecipeMenuHolder holder, int slot, org.bukkit.event.inventory.ClickType click) {
        if (slot == categoryBackSlot()) {
            if (holder.isAdminMode()) {
                openAdminMain(player, 0);
            } else {
                openMain(player, 0);
            }
            socialHook.play(player, "back");
            return;
        }
        CategoryLayout layout = layoutCategoryRecipes(currentCategoryDisplayRecipes(holder.getCategory(), holder.isAdminMode()), categoryRecipeSlots());
        int maxPage = layout.maxPage();
        if (slot == categoryPreviousSlot() && maxPage > 0) {
            if (holder.getPage() > 0) {
                holder.setPage(holder.getPage() - 1);
                renderCategory(holder);
                socialHook.play(player, "page");
            } else {
                socialHook.play(player, "invalid");
            }
            return;
        }
        if (slot == categoryNextSlot() && maxPage > 0) {
            if (holder.getPage() < maxPage) {
                holder.setPage(holder.getPage() + 1);
                renderCategory(holder);
                socialHook.play(player, "page");
            } else {
                socialHook.play(player, "invalid");
            }
            return;
        }
        MdvRecipe recipe = holder.getRecipeSlots().get(slot);
        if (recipe != null) {
            if (holder.isAdminMode() && click.isRightClick()) {
                plugin.getEditorGuiManager().openEditRecipe(player, recipe);
            } else {
                openRecipeFromCategory(player, recipe, holder.getCategory(), holder.getPage());
            }
        }
    }

    private void handleSearchClick(Player player, RecipeMenuHolder holder, int slot) {
        if (slot == searchBackSlot()) {
            returnSearchItem(player, holder);
            openMain(player, 0);
            socialHook.play(player, "back");
            return;
        }
        SearchSession session = searchSessions.get(player.getUniqueId());
        List<MdvRecipe> recipes = session == null ? List.of() : currentSearchRecipes(session.item());
        int maxPage = maxPage(recipes.size(), searchRecipeSlots().size());
        if (slot == searchPreviousSlot() && maxPage > 0) {
            if (holder.getPage() > 0) {
                holder.setPage(holder.getPage() - 1);
                renderSearch(holder, session == null ? null : session.item());
                socialHook.play(player, "page");
            } else {
                socialHook.play(player, "invalid");
            }
            return;
        }
        if (slot == searchNextSlot() && maxPage > 0) {
            if (holder.getPage() < maxPage) {
                holder.setPage(holder.getPage() + 1);
                renderSearch(holder, session == null ? null : session.item());
                socialHook.play(player, "page");
            } else {
                socialHook.play(player, "invalid");
            }
            return;
        }
        MdvRecipe recipe = holder.getRecipeSlots().get(slot);
        if (recipe != null) {
            openRecipeFromSearch(player, recipe, holder.getPage());
        }
    }

    private void handleRecipeClick(Player player, RecipeMenuHolder holder, int slot) {
        if (slot == recipeBackSlot()) {
            handleRecipeBack(player, holder);
            socialHook.play(player, "back");
            return;
        }
        ItemSpec ingredient = holder.getIngredientSlots().get(slot);
        if (ingredient != null) {
            Optional<MdvRecipe> target = recipeManager.findVisibleRecipeProducing(ingredient);
            if (target.isPresent()) {
                openRecipeFromRecipe(player, target.get(), holder);
            } else {
                socialHook.play(player, "invalid");
            }
        }
    }

    private void handleRecipeBack(Player player, RecipeMenuHolder holder) {
        RecipeMenuHolder.BackTarget target = holder.getBackTarget();
        if (target == RecipeMenuHolder.BackTarget.RECIPE && !holder.getRecipeBackStack().isEmpty()) {
            List<RecipeMenuHolder.RecipeBackState> stack = new ArrayList<>(holder.getRecipeBackStack());
            RecipeMenuHolder.RecipeBackState previous = stack.remove(stack.size() - 1);
            RecipeMenuHolder parentHolder = new RecipeMenuHolder(
                    RecipeMenuHolder.Screen.RECIPE,
                    previous.category(),
                    previous.page(),
                    previous.recipe(),
                    previous.backTarget()
            );
            parentHolder.setRecipeBackStack(stack);
            Inventory inventory = Bukkit.createInventory(parentHolder, DEFAULT_SIZE, color(configString("gui.titles.recipe", "&8&lVista de Receta")));
            parentHolder.setInventory(inventory);
            renderRecipe(parentHolder);
            openInventory(player, inventory);
            return;
        }
        if (target == RecipeMenuHolder.BackTarget.SEARCH) {
            SearchSession session = searchSessions.get(player.getUniqueId());
            if (session != null) {
                RecipeMenuHolder searchHolder = new RecipeMenuHolder(RecipeMenuHolder.Screen.SEARCH, firstCategory(), holder.getPage(), null, RecipeMenuHolder.BackTarget.MAIN);
                Inventory inventory = Bukkit.createInventory(searchHolder, DEFAULT_SIZE, color(configString("gui.titles.search", "&8&lBúsqueda de Recetas")));
                searchHolder.setInventory(inventory);
                renderSearch(searchHolder, session.item());
                openInventory(player, inventory);
                return;
            }
            openMain(player, 0);
            return;
        }
        openCategory(player, holder.getCategory(), holder.getPage());
    }

    private void renderMain(RecipeMenuHolder holder) {
        Inventory inventory = holder.getInventory();
        inventory.clear();
        holder.clearRecipeSlots();
        holder.clearIngredientSlots();
        fillAll(inventory, Material.BLACK_STAINED_GLASS_PANE, " ");

        drawCategories(inventory, holder.getPage());
        drawSearchBox(inventory, null, false);
        inventory.setItem(searchInfoSlot(), button(Material.OAK_SIGN, configString("gui.search.info-name", "&eBuscador"), configStringList("gui.search.info-lore", List.of("&7Pon un objeto en el centro", "&7para ver qué puedes fabricar."))));
        inventory.setItem(mainBackSlot(), backHead("&6&lVolver", List.of("", "&7Regresa al menú social.", "", "&eClick para volver.")));
        List<CategoryInfo> categoryList = new ArrayList<>(categories().values());
        int maxPage = maxPage(categoryList.size(), categorySlots().size());
        if (maxPage > 0) {
            inventory.setItem(mainPreviousSlot(), arrowLeft("&eAnterior", holder.getPage(), maxPage));
            inventory.setItem(mainNextSlot(), arrowRight("&eSiguiente", holder.getPage(), maxPage));
        }
    }

    private void renderCategory(RecipeMenuHolder holder) {
        Inventory inventory = holder.getInventory();
        inventory.clear();
        holder.clearRecipeSlots();
        holder.clearIngredientSlots();
        fillAll(inventory, Material.BLACK_STAINED_GLASS_PANE, " ");

        CategoryLayout layout = layoutCategoryRecipes(currentCategoryDisplayRecipes(holder.getCategory(), holder.isAdminMode()), categoryRecipeSlots());
        if (holder.getPage() > layout.maxPage()) {
            holder.setPage(layout.maxPage());
        }
        drawRecipeResults(inventory, holder, layout.recipesForPage(holder.getPage()));
        inventory.setItem(categoryBackSlot(), backHead("&6&lVolver", List.of("", "&7Regresa al menú principal", "&7de categorías.", "", "&eClick para volver.")));
        int maxPage = layout.maxPage();
        if (maxPage > 0) {
            inventory.setItem(categoryPreviousSlot(), arrowLeft("&eAnterior", holder.getPage(), maxPage));
            inventory.setItem(categoryNextSlot(), arrowRight("&eSiguiente", holder.getPage(), maxPage));
        }
    }

    private void renderSearch(RecipeMenuHolder holder, ItemStack searchItem) {
        Inventory inventory = holder.getInventory();
        inventory.clear();
        holder.clearRecipeSlots();
        holder.clearIngredientSlots();
        fillAll(inventory, Material.BLACK_STAINED_GLASS_PANE, " ");

        List<MdvRecipe> recipes = searchItem == null || searchItem.getType().isAir() ? List.of() : currentSearchRecipes(searchItem);
        drawRecipeResults(inventory, holder, recipes, searchRecipeSlots());
        inventory.setItem(searchInfoSlot(), searchInfoItem(searchItem, recipes.size()));
        drawSearchBox(inventory, searchItem, true);
        inventory.setItem(searchBackSlot(), backHead("&6&lVolver", List.of("", "&7Regresa al menú principal", "&7y recupera tu objeto.", "", "&eClick para volver.")));
        int maxPage = maxPage(recipes.size(), searchRecipeSlots().size());
        if (maxPage > 0) {
            inventory.setItem(searchPreviousSlot(), arrowLeft("&eAnterior", holder.getPage(), maxPage));
            inventory.setItem(searchNextSlot(), arrowRight("&eSiguiente", holder.getPage(), maxPage));
        }
    }

    private void renderRecipe(RecipeMenuHolder holder) {
        Inventory inventory = holder.getInventory();
        inventory.clear();
        holder.clearRecipeSlots();
        holder.clearIngredientSlots();
        fillAll(inventory, Material.BLACK_STAINED_GLASS_PANE, " ");

        MdvRecipe recipe = holder.getRecipe();
        if (recipe == null) {
            inventory.setItem(22, button(Material.BARRIER, "&cReceta inválida", List.of("&7No se pudo mostrar esta receta.")));
            return;
        }

        List<MdvRecipe> linkedRecipes = recipeManager.getLinkedRecipes(recipe, true);
        int variantCount = Math.max(1, linkedRecipes.size());
        int variantIndex = variantCount <= 1 ? 0 : holder.getLinkedCycleIndex() % variantCount;
        MdvRecipe displayRecipe = linkedRecipes.get(variantIndex);

        drawRecipeIngredients(inventory, holder, displayRecipe);
        inventory.setItem(recipeStationSlot(), stationItem(displayRecipe.getStation(), displayRecipe, variantIndex, variantCount));
        inventory.setItem(recipeResultSlot(), displayResult(displayRecipe));
        inventory.setItem(recipeBackSlot(), backHead("&6&lVolver", List.of("", "&7Regresa al menú anterior.", "", "&eClick para volver.")));
    }

    private void drawRecipeIngredients(Inventory inventory, RecipeMenuHolder holder, MdvRecipe recipe) {
        List<Integer> slots = recipeIngredientSlots();
        if (recipe.getType() == RecipeType.SHAPED) {
            for (int i = 0; i < Math.min(9, slots.size()); i++) {
                int row = i / 3;
                int col = i % 3;
                String line = row < recipe.getShape().size() ? recipe.getShape().get(row) : "   ";
                char symbol = col < line.length() ? line.charAt(col) : ' ';
                ItemSpec spec = recipe.getShapedIngredients().get(symbol);
                setIngredientOrEmpty(inventory, holder, slots.get(i), spec);
            }
            return;
        }
        if (recipe.getType() == RecipeType.SHAPELESS) {
            int index = 0;
            for (ItemSpec spec : recipe.getShapelessIngredients().values()) {
                if (index >= slots.size()) {
                    break;
                }
                setIngredientOrEmpty(inventory, holder, slots.get(index++), spec);
            }
            while (index < slots.size()) {
                inventory.setItem(slots.get(index++), emptySlot());
            }
            return;
        }
        for (int slot : slots) {
            inventory.setItem(slot, emptySlot());
        }
        setIngredientOrEmpty(inventory, holder, cookingIngredientSlot(), recipe.getCookingIngredient());
    }

    private void setIngredientOrEmpty(Inventory inventory, RecipeMenuHolder holder, int slot, ItemSpec spec) {
        if (spec == null) {
            inventory.setItem(slot, emptySlot());
            return;
        }
        inventory.setItem(slot, ingredientDisplay(spec));
        holder.getIngredientSlots().put(slot, spec);
    }

    private void drawCategories(Inventory inventory, int page) {
        List<CategoryInfo> categoryList = new ArrayList<>(categories().values());
        List<Integer> slots = categorySlots();
        int start = Math.max(0, page) * slots.size();
        for (int i = 0; i < slots.size(); i++) {
            int index = start + i;
            if (index >= categoryList.size()) {
                break;
            }
            CategoryInfo category = categoryList.get(index);
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("&7Recetas visibles: &e" + currentCategoryRecipes(category.id(), false).size());
            int hiddenCount = currentCategoryRecipes(category.id(), true).size() - currentCategoryRecipes(category.id(), false).size();
            if (hiddenCount > 0) {
                lore.add("&8Ocultas admin: " + hiddenCount);
            }
            lore.add("");
            lore.add("&eClick para ver esta categoría.");
            inventory.setItem(slots.get(i), button(category.icon(), category.name(), lore));
        }
    }

    private void drawSearchBox(Inventory inventory, ItemStack searchItem, boolean active) {
        Material material = active ? searchActivePane() : searchInactivePane();
        String name = active ? "&aBuscando recetas" : "&cBuscador de ingrediente";
        List<String> lore = active
                ? List.of("&7Mostrando recetas que usan", "&7el objeto del centro.")
                : List.of("&7Pon un objeto en el centro", "&7para ver qué puedes fabricar.");
        ItemStack border = button(material, name, lore);
        for (int slot : searchBorderSlots()) {
            inventory.setItem(slot, border);
        }
        if (searchItem == null || searchItem.getType().isAir()) {
            inventory.setItem(searchCenterSlot(), null);
        } else {
            inventory.setItem(searchCenterSlot(), searchItem.clone());
        }
    }

    private void drawRecipeResults(Inventory inventory, RecipeMenuHolder holder, Map<Integer, MdvRecipe> pageRecipes) {
        for (Map.Entry<Integer, MdvRecipe> entry : pageRecipes.entrySet()) {
            int slot = entry.getKey();
            MdvRecipe recipe = entry.getValue();
            inventory.setItem(slot, displayResult(recipe, holder.isAdminMode()));
            holder.getRecipeSlots().put(slot, recipe);
        }
    }

    private void drawRecipeResults(Inventory inventory, RecipeMenuHolder holder, List<MdvRecipe> recipes, List<Integer> slots) {
        List<MdvRecipe> ordered = new ArrayList<>(recipes);
        ordered.sort(recipeManager.displayComparator());
        int maxPage = maxPage(ordered.size(), slots.size());
        if (holder.getPage() > maxPage) {
            holder.setPage(maxPage);
        }
        int start = holder.getPage() * slots.size();
        for (int i = 0; i < slots.size(); i++) {
            int index = start + i;
            int slot = slots.get(i);
            if (index >= ordered.size()) {
                continue;
            }
            MdvRecipe recipe = ordered.get(index);
            inventory.setItem(slot, displayResult(recipe, holder.isAdminMode()));
            holder.getRecipeSlots().put(slot, recipe);
        }
    }

    private void handleSearchCenterChanged(Player player) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof RecipeMenuHolder holder)) {
            return;
        }
        if (holder.getScreen() != RecipeMenuHolder.Screen.MAIN && holder.getScreen() != RecipeMenuHolder.Screen.SEARCH) {
            return;
        }
        ItemStack item = holder.getInventory().getItem(searchCenterSlot());
        if (item == null || item.getType().isAir()) {
            searchSessions.remove(player.getUniqueId());
            holder.setScreen(RecipeMenuHolder.Screen.MAIN);
            holder.setPage(0);
            renderMain(holder);
            return;
        }
        item = item.clone();
        int sourceSlot = searchSessions.containsKey(player.getUniqueId())
                ? searchSessions.get(player.getUniqueId()).sourceSlot()
                : lastBottomClickSlot.getOrDefault(player.getUniqueId(), -1);
        searchSessions.put(player.getUniqueId(), new SearchSession(item.clone(), sourceSlot));
        holder.setScreen(RecipeMenuHolder.Screen.SEARCH);
        holder.setPage(0);
        renderSearch(holder, item);
        socialHook.play(player, "open");
    }

    private void returnSearchItem(Player player, RecipeMenuHolder holder) {
        SearchSession session = searchSessions.remove(player.getUniqueId());
        ItemStack item = session == null ? null : session.item().clone();

        Inventory inventory = holder == null ? null : holder.getInventory();
        boolean hasEditableSearchSlot = holder != null
                && (holder.getScreen() == RecipeMenuHolder.Screen.MAIN
                || holder.getScreen() == RecipeMenuHolder.Screen.SEARCH);

        // Slot 40 is the search input only in MAIN/SEARCH. In CATEGORY it is a
        // normal recipe-display slot (for example BOTASORCO), so reading it from
        // every menu duplicated preview results whenever the GUI/plugin closed.
        if ((item == null || item.getType().isAir()) && hasEditableSearchSlot && inventory != null) {
            item = inventory.getItem(searchCenterSlot());
            if (item != null) {
                item = item.clone();
            }
        }
        if (hasEditableSearchSlot && inventory != null && inventory.getSize() > searchCenterSlot()) {
            inventory.setItem(searchCenterSlot(), null);
        }
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (session != null && session.sourceSlot() >= 0) {
            PlayerInventory playerInventory = player.getInventory();
            int slot = session.sourceSlot();
            if (slot >= 0 && slot < playerInventory.getSize()) {
                ItemStack current = playerInventory.getItem(slot);
                if (current != null && !current.getType().isAir()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), current.clone());
                }
                playerInventory.setItem(slot, item);
                return;
            }
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private boolean isSearchCenter(RecipeMenuHolder holder, int rawSlot) {
        return rawSlot == searchCenterSlot() && (holder.getScreen() == RecipeMenuHolder.Screen.MAIN || holder.getScreen() == RecipeMenuHolder.Screen.SEARCH);
    }

    private void openInventory(Player player, Inventory inventory) {
        cleanupCursorIfGuiItem(player);
        internalTransitions.add(player.getUniqueId());
        player.openInventory(inventory);
        Bukkit.getScheduler().runTask(plugin, () -> internalTransitions.remove(player.getUniqueId()));
    }

    private void openMdvSocial(Player player) {
        String command = plugin.getConfig().getString("gui.main-back-command", "mdvsocial");
        if (command == null || command.isBlank()) {
            player.closeInventory();
            return;
        }
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(player, command.replaceFirst("^/", "")));
    }

    private List<MdvRecipe> currentCategoryRecipes(String category) {
        return currentCategoryRecipes(category, false);
    }

    private List<MdvRecipe> currentCategoryRecipes(String category, boolean includeHidden) {
        return includeHidden ? recipeManager.getByCategory(category) : recipeManager.getVisibleByCategory(category);
    }

    private List<MdvRecipe> currentCategoryDisplayRecipes(String category, boolean includeHidden) {
        return recipeManager.collapseDisplayGroups(currentCategoryRecipes(category, includeHidden));
    }

    private List<MdvRecipe> currentSearchRecipes(ItemStack searchItem) {
        return new ArrayList<>(recipeManager.findVisibleDisplayRecipesUsing(searchItem));
    }

    private ItemStack displayResult(MdvRecipe recipe) {
        return displayResult(recipe, false);
    }

    private ItemStack displayResult(MdvRecipe recipe, boolean adminMode) {
        ItemStack item = itemResolver.buildItem(recipe.getResult());
        if (item == null || item.getType().isAir()) {
            item = new ItemStack(Material.BARRIER);
        }
        item = item.clone();
        if (adminMode) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add(color(""));
                lore.add(color("&8ID: &7" + recipe.getId()));
                lore.add(color("&8Estado: " + (recipe.isHidden() ? "&cOculta" : "&aVisible")));
                lore.add(color("&eClick izquierdo: ver receta"));
                lore.add(color("&6Click derecho: editar"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        }
        tagPreviewItem(item);
        return item;
    }

    private ItemStack ingredientDisplay(ItemSpec spec) {
        ItemStack item = itemResolver.buildItem(spec);
        if (item == null || item.getType().isAir()) {
            return button(Material.BARRIER, "&cIngrediente inválido", List.of("&7Revisa el YAML de la receta."));
        }
        item = item.clone();
        item.setAmount(Math.max(1, spec.getAmount()));
        tagPreviewItem(item);
        return item;
    }

    private ItemStack stationItem(StationType station, MdvRecipe recipe, int variantIndex, int variantCount) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7Dónde se fabrica:");
        lore.add("&e" + stationName(station));
        lore.add("");
        lore.add("&7Categoría: &f" + prettyCategory(recipe.getCategory()));
        lore.add("&7Tipo: &f" + recipeTypeName(recipe.getType()));
        if (variantCount > 1) {
            lore.add("&7Variante: &e" + (variantIndex + 1) + "&7/&e" + variantCount);
            lore.add("&8Esta receta cambia sola cada 1.5s.");
        }
        if (recipe.getForjador().getExp() > 0) {
            lore.add("&7Forjador: &e+" + formatDouble(recipe.getForjador().getExp()) + " EXP");
        }
        if (recipe.getType() == RecipeType.COOKING) {
            lore.add("&7Tiempo: &e" + recipe.getCookingTime() + " ticks");
            lore.add("&7EXP vanilla: &e" + recipe.getCookingVanillaExp());
        }
        return button(stationMaterial(station), "&6&l" + stationName(station), lore);
    }

    private ItemStack searchInfoItem(ItemStack item, int count) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7Objeto consultado:");
        lore.add("&e" + (item == null ? "Nada" : item.getType().name()));
        lore.add("");
        lore.add("&7Recetas encontradas: &e" + count);
        return button(Material.OAK_SIGN, "&eBuscador", lore);
    }


    private ItemStack arrowLeft(String name, int page, int maxPage) {
        return button(Material.ARROW, name, List.of("&7Página &e" + (page + 1) + " &7/ &e" + (maxPage + 1)));
    }

    private ItemStack arrowRight(String name, int page, int maxPage) {
        return button(Material.ARROW, name, List.of("&7Página &e" + (page + 1) + " &7/ &e" + (maxPage + 1)));
    }

    private ItemStack emptySlot() {
        return button(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
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
            tagGuiItem(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack backHead(String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta rawMeta = item.getItemMeta();
        if (rawMeta instanceof SkullMeta skullMeta) {
            applyTexture(skullMeta, plugin.getConfig().getString("gui.buttons.back-texture", ""));
            skullMeta.setDisplayName(color(name));
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(color(line));
            }
            skullMeta.setLore(coloredLore);
            tagGuiItem(skullMeta);
            item.setItemMeta(skullMeta);
            return item;
        }
        return button(Material.ARROW, name, lore);
    }

    private void applyTexture(SkullMeta meta, String textureValue) {
        if (meta == null || textureValue == null || textureValue.isBlank()) {
            return;
        }
        String textureUrl = extractTextureUrl(textureValue.trim());
        if (textureUrl == null || textureUrl.isBlank()) {
            plugin.getLogger().warning("No se pudo aplicar textura custom de cabeza: textura invalida o Base64 sin URL.");
            return;
        }
        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "MDVRecetas");
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(textureUrl));
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (Throwable ex) {
            plugin.getLogger().warning("No se pudo aplicar textura custom de cabeza con API publica: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
        }
    }

    /**
     * Misma estrategia que MDVSocial: acepta Base64 de Minecraft Heads o URL directa.
     * En Paper/Purpur 1.21+ es mas seguro aplicar la URL con SkullMeta#setOwnerProfile
     * que tocar GameProfile/campos internos por reflexion.
     */
    private String extractTextureUrl(String textureValue) {
        if (textureValue == null) {
            return "";
        }
        String value = textureValue.trim();
        if (value.isBlank()) {
            return "";
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            int urlKey = decoded.indexOf("\"url\"");
            if (urlKey < 0) {
                return "";
            }
            int colon = decoded.indexOf(':', urlKey);
            if (colon < 0) {
                return "";
            }
            int firstQuote = decoded.indexOf('\"', colon);
            if (firstQuote < 0) {
                return "";
            }
            int secondQuote = decoded.indexOf('\"', firstQuote + 1);
            if (secondQuote < 0) {
                return "";
            }
            return decoded.substring(firstQuote + 1, secondQuote).replace("\\/", "/");
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void fillAll(Inventory inventory, Material material, String name) {
        ItemStack filler = button(material, name, List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
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
                        icon == null ? Material.BOOK : icon
                ));
            }
        }
        if (!result.isEmpty()) {
            return result;
        }
        String[] defaults = {"ARMAS_CUERPO_A_CUERPO", "ARMAS_A_DISTANCIA", "ARMAS_MAGICAS", "SOPORTE_MAGICO", "ARMADURAS", "HERRAMIENTAS", "AGRICULTURA", "CONSUMIBLES", "MATERIALES", "REPARACIONES", "UTILITARIOS"};
        Material[] icons = {Material.IRON_SWORD, Material.BOW, Material.BLAZE_ROD, Material.ENCHANTED_BOOK, Material.IRON_CHESTPLATE, Material.IRON_PICKAXE, Material.WHEAT, Material.HONEY_BOTTLE, Material.IRON_INGOT, Material.ANVIL, Material.COMPASS};
        for (int i = 0; i < defaults.length; i++) {
            result.put(defaults[i], new CategoryInfo(defaults[i], "&f" + prettyCategory(defaults[i]), icons[i]));
        }
        return result;
    }

    private String categoryByVisibleSlot(int slot, int page) {
        List<CategoryInfo> categoryList = new ArrayList<>(categories().values());
        List<Integer> slots = categorySlots();
        int localIndex = slots.indexOf(slot);
        if (localIndex < 0) {
            return null;
        }
        int index = page * slots.size() + localIndex;
        if (index < 0 || index >= categoryList.size()) {
            return null;
        }
        return categoryList.get(index).id();
    }

    private String firstCategory() {
        Map<String, CategoryInfo> categories = categories();
        if (!categories.isEmpty()) {
            return categories.keySet().iterator().next();
        }
        Collection<MdvRecipe> recipes = recipeManager.getVisibleRecipes();
        if (!recipes.isEmpty()) {
            return recipes.iterator().next().getCategory();
        }
        return "GENERAL";
    }

    private String categoryTitle(String category) {
        String template = configString("gui.titles.category", "&8&lRecetas &e%category%");
        return color(template.replace("%category%", categoryDisplayName(category)));
    }

    private String categoryDisplayName(String category) {
        String normalized = normalizeCategory(category);
        CategoryInfo info = categories().get(normalized);
        if (info != null) {
            return info.name();
        }
        return prettyCategory(normalized);
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "GENERAL";
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

    private String recipeTypeName(RecipeType type) {
        return switch (type) {
            case SHAPED -> "Con forma";
            case SHAPELESS -> "Sin forma";
            case COOKING -> "Cocción";
        };
    }

    private int maxPage(int size, int pageSize) {
        int safePageSize = Math.max(1, pageSize);
        return Math.max(0, (int) Math.ceil(size / (double) safePageSize) - 1);
    }

    private List<Integer> categorySlots() {
        return integerList("gui.main.category-slots", List.of(1,2,3,4,5,6,7,11,12,13,14,15,16,17));
    }

    private List<Integer> categoryRecipeSlots() {
        return integerList("gui.category.recipe-slots", List.of(10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43));
    }

    private List<Integer> searchRecipeSlots() {
        return integerList("gui.search.recipe-slots", List.of(1,2,3,4,5,6,7,10,11,12,13,14,15,16,19,20,21,22,23,24,25));
    }

    private List<Integer> recipeIngredientSlots() {
        return integerList("gui.recipe.ingredient-slots", List.of(10,11,12,19,20,21,28,29,30));
    }

    private List<Integer> searchBorderSlots() {
        return integerList("gui.search.border-slots", List.of(30,31,32,39,41,48,49,50));
    }

    private int searchCenterSlot() {
        return plugin.getConfig().getInt("gui.search.center-slot", DEFAULT_SEARCH_CENTER);
    }

    private int searchInfoSlot() {
        return plugin.getConfig().getInt("gui.search.info-slot", 38);
    }

    private int mainBackSlot() {
        return plugin.getConfig().getInt("gui.main.back-slot", 45);
    }

    private int mainPreviousSlot() {
        return plugin.getConfig().getInt("gui.main.previous-slot", 46);
    }

    private int mainNextSlot() {
        return plugin.getConfig().getInt("gui.main.next-slot", 52);
    }

    private int categoryBackSlot() {
        return plugin.getConfig().getInt("gui.category.back-slot", 49);
    }

    private int categoryPreviousSlot() {
        return plugin.getConfig().getInt("gui.category.previous-slot", 45);
    }

    private int categoryNextSlot() {
        return plugin.getConfig().getInt("gui.category.next-slot", 53);
    }

    private int searchBackSlot() {
        return plugin.getConfig().getInt("gui.search.back-slot", 45);
    }

    private int searchPreviousSlot() {
        return plugin.getConfig().getInt("gui.search.previous-slot", 46);
    }

    private int searchNextSlot() {
        return plugin.getConfig().getInt("gui.search.next-slot", 52);
    }

    private int recipeBackSlot() {
        return plugin.getConfig().getInt("gui.recipe.back-slot", 45);
    }

    private int recipeStationSlot() {
        return plugin.getConfig().getInt("gui.recipe.station-slot", 23);
    }

    private int recipeResultSlot() {
        return plugin.getConfig().getInt("gui.recipe.result-slot", 25);
    }

    private int cookingIngredientSlot() {
        return plugin.getConfig().getInt("gui.recipe.cooking-ingredient-slot", 20);
    }


    private Material searchInactivePane() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("gui.search.inactive-pane", "RED_STAINED_GLASS_PANE"));
        return material == null ? Material.RED_STAINED_GLASS_PANE : material;
    }

    private Material searchActivePane() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("gui.search.active-pane", "LIME_STAINED_GLASS_PANE"));
        return material == null ? Material.LIME_STAINED_GLASS_PANE : material;
    }

    private List<Integer> integerList(String path, List<Integer> fallback) {
        List<Integer> list = plugin.getConfig().getIntegerList(path);
        return list.isEmpty() ? fallback : list;
    }

    private List<String> configStringList(String path, List<String> fallback) {
        List<String> list = plugin.getConfig().getStringList(path);
        return list.isEmpty() ? fallback : list;
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

    private CategoryLayout layoutCategoryRecipes(List<MdvRecipe> recipes, List<Integer> slots) {
        List<MdvRecipe> ordered = new ArrayList<>(recipes);
        ordered.sort(recipeManager.displayComparator());

        Map<Integer, Map<Integer, MdvRecipe>> pages = new HashMap<>();
        List<MdvRecipe> automatic = new ArrayList<>();

        for (MdvRecipe recipe : ordered) {
            Optional<VisualPosition> position = visualPositionFor(recipe, slots);
            if (position.isEmpty()) {
                automatic.add(recipe);
                continue;
            }
            int page = position.get().page();
            int slot = position.get().slot();
            Map<Integer, MdvRecipe> pageMap = pages.computeIfAbsent(page, ignored -> new LinkedHashMap<>());
            if (pageMap.containsKey(slot)) {
                automatic.add(recipe);
                continue;
            }
            pageMap.put(slot, recipe);
        }

        int page = 0;
        int slotIndex = 0;
        for (MdvRecipe recipe : automatic) {
            while (true) {
                Map<Integer, MdvRecipe> pageMap = pages.computeIfAbsent(page, ignored -> new LinkedHashMap<>());
                while (slotIndex < slots.size() && pageMap.containsKey(slots.get(slotIndex))) {
                    slotIndex++;
                }
                if (slotIndex < slots.size()) {
                    pageMap.put(slots.get(slotIndex), recipe);
                    slotIndex++;
                    break;
                }
                page++;
                slotIndex = 0;
            }
        }

        int maxPage = 0;
        for (Map.Entry<Integer, Map<Integer, MdvRecipe>> entry : pages.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                maxPage = Math.max(maxPage, entry.getKey());
            }
        }
        return new CategoryLayout(pages, maxPage);
    }

    private Optional<VisualPosition> visualPositionFor(MdvRecipe recipe, List<Integer> allowedSlots) {
        for (MdvRecipe candidate : recipeManager.getLinkedRecipes(recipe, true)) {
            if (!candidate.hasVisualPosition()) {
                continue;
            }
            if (!allowedSlots.contains(candidate.getVisualSlot())) {
                continue;
            }
            return Optional.of(new VisualPosition(candidate.getVisualPage() - 1, candidate.getVisualSlot()));
        }
        return Optional.empty();
    }

    private void tickLinkedRecipeViews() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof RecipeMenuHolder holder)) {
                continue;
            }
            if (holder.getScreen() != RecipeMenuHolder.Screen.RECIPE || holder.getRecipe() == null) {
                continue;
            }
            if (recipeManager.getLinkedRecipes(holder.getRecipe(), true).size() <= 1) {
                continue;
            }
            holder.nextLinkedCycleIndex();
            renderRecipe(holder);
        }
    }

    private void tagGuiItem(ItemMeta meta) {
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(guiItemKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void tagPreviewItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        tagGuiItem(meta);
        item.setItemMeta(meta);
    }

    private boolean isTaggedGuiItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(guiItemKey, PersistentDataType.BYTE);
    }

    private void cleanupCursorIfGuiItem(Player player) {
        if (isTaggedGuiItem(player.getItemOnCursor())) {
            player.setItemOnCursor(new ItemStack(Material.AIR));
        }
    }

    private void cleanupEscapedGuiItems(Player player) {
        cleanupCursorIfGuiItem(player);
        removeNearbyDroppedGuiItems(player);
        PlayerInventory inventory = player.getInventory();
        boolean changed = false;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (isTaggedGuiItem(item)) {
                inventory.setItem(i, null);
                changed = true;
            }
        }
        if (changed) {
            player.updateInventory();
        }
    }

    private void removeNearbyDroppedGuiItems(Player player) {
        player.getWorld().getNearbyEntities(player.getLocation(), 3.0D, 3.0D, 3.0D).forEach(entity -> {
            if (entity instanceof Item dropped && isTaggedGuiItem(dropped.getItemStack())) {
                dropped.remove();
            }
        });
    }

    private record CategoryInfo(String id, String name, Material icon) {
    }

    private record CategoryLayout(Map<Integer, Map<Integer, MdvRecipe>> pages, int maxPage) {
        Map<Integer, MdvRecipe> recipesForPage(int page) {
            return pages.getOrDefault(page, Map.of());
        }
    }

    private record VisualPosition(int page, int slot) {
    }

    private record SearchSession(ItemStack item, int sourceSlot) {
    }
}
