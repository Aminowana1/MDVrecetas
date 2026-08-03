package com.mdvcraft.mdvrecetas.service;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.model.ItemKind;
import com.mdvcraft.mdvrecetas.model.ItemSpec;
import com.mdvcraft.mdvrecetas.model.MdvRecipe;
import com.mdvcraft.mdvrecetas.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ForjadorModifierService {
    private static final Pattern SOCKET_GROUP_PATTERN = Pattern.compile(
            "^mdv_sockets_(fisica|distancia|arcana|soporte)_t([2-5])$",
            Pattern.CASE_INSENSITIVE
    );

    private final MDVRecetasPlugin plugin;
    private final Random random = new Random();
    private final NamespacedKey modifierAppliedKey;
    private final NamespacedKey modifierIdKey;
    private final NamespacedKey modifierQualityKey;
    private final NamespacedKey modifierRecipeKey;
    private final NamespacedKey modifierPrefixKey;
    private final NamespacedKey socketGroupKey;
    private final NamespacedKey socketModifierIdKey;
    private final NamespacedKey socketCountKey;
    private FileConfiguration modifierConfig;

    public ForjadorModifierService(MDVRecetasPlugin plugin) {
        this.plugin = plugin;
        this.modifierAppliedKey = new NamespacedKey(plugin, "forjador_modifier_applied");
        this.modifierIdKey = new NamespacedKey(plugin, "forjador_modifier_id");
        this.modifierQualityKey = new NamespacedKey(plugin, "forjador_modifier_quality");
        this.modifierRecipeKey = new NamespacedKey(plugin, "forjador_modifier_recipe");
        this.modifierPrefixKey = new NamespacedKey(plugin, "forjador_modifier_prefix");
        this.socketGroupKey = new NamespacedKey(plugin, "forjador_socket_group");
        this.socketModifierIdKey = new NamespacedKey(plugin, "forjador_socket_modifier");
        this.socketCountKey = new NamespacedKey(plugin, "forjador_socket_count");
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "modifiers.yml");
        this.modifierConfig = YamlConfiguration.loadConfiguration(file);
    }

    public int getForjadorLevel(Player player) {
        return readForjadorLevel(player);
    }

    public double getCurrentChance(Player player, String qualityKey) {
        ModifierQuality quality = ModifierQuality.fromConfig(qualityKey);
        if (!quality.isRollable()) {
            return 0.0D;
        }
        return currentChancesForLevel(readForjadorLevel(player)).getOrDefault(quality, 0.0D);
    }

    public String formatCurrentChance(Player player, String qualityKey) {
        double value = getCurrentChance(player, qualityKey);
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }


    public ItemStack applyModifierIfNeeded(ItemStack original, Player player, MdvRecipe recipe) {
        if (original == null || original.getType().isAir() || player == null || recipe == null || recipe.getForjador() == null) {
            return original;
        }
        if (!recipe.getForjador().isModifiers()) {
            return original;
        }
        ItemSpec resultSpec = recipe.getResult();
        if (resultSpec == null || resultSpec.getKind() != ItemKind.MMOITEMS) {
            return original;
        }
        if (hasAlreadyRolled(original)) {
            return original;
        }

        int level = readForjadorLevel(player);

        Optional<SelectedModifier> qualityModifier = getBoolean("forjador-modifiers.enabled", true)
                ? selectModifier(resultSpec, level, recipe.getForjador().getModifierPool())
                : Optional.empty();

        Optional<SelectedSocketModifier> socketModifier = getBoolean("forjador-sockets.enabled", true)
                ? selectSocketModifier(resultSpec, level)
                : Optional.empty();

        if (qualityModifier.isEmpty() && socketModifier.isEmpty()) {
            return markAsRolled(
                    original.clone(),
                    "none",
                    ModifierQuality.NONE,
                    recipe.getId(),
                    null,
                    null
            );
        }

        List<AppliedModifier> modifiersToApply = new ArrayList<>(2);
        qualityModifier.ifPresent(selected ->
                modifiersToApply.add(new AppliedModifier(selected.id(), selected.node())));
        socketModifier.ifPresent(selected ->
                modifiersToApply.add(new AppliedModifier(selected.id(), selected.node())));

        ItemStack modified = buildMmoItemWithModifiers(resultSpec, modifiersToApply);
        if (modified == null || modified.getType().isAir()) {
            String attempted = modifiersToApply.stream()
                    .map(AppliedModifier::id)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("none");
            plugin.getLogger().warning("Could not apply MMOItems modifiers [" + attempted + "] to "
                    + resultSpec.getMmoType() + ":" + resultSpec.getMmoId() + ". Using original item.");

            SelectedModifier quality = qualityModifier.orElse(null);
            return markAsRolled(
                    original.clone(),
                    quality == null ? "none" : "failed:" + quality.id(),
                    quality == null ? ModifierQuality.NONE : quality.quality(),
                    recipe.getId(),
                    null,
                    null
            );
        }

        modified.setAmount(Math.max(1, original.getAmount()));

        SelectedModifier quality = qualityModifier.orElse(null);
        String appliedPrefix = quality == null ? null : readPrefixFormat(quality);
        applyModifierPrefix(modified, appliedPrefix);

        return markAsRolled(
                modified,
                quality == null ? "none" : quality.id(),
                quality == null ? ModifierQuality.NONE : quality.quality(),
                recipe.getId(),
                appliedPrefix,
                socketModifier.orElse(null)
        );
    }

    private Optional<SelectedModifier> selectModifier(ItemSpec resultSpec, int level, String configuredPool) {
        Map<String, Object> candidates = loadCandidateModifierNodes(resultSpec, configuredPool);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        Map<ModifierQuality, List<ModifierCandidate>> byQuality = classifyCandidates(candidates);
        if (byQuality.values().stream().allMatch(List::isEmpty)) {
            return Optional.empty();
        }

        ModifierQuality rolled = rollQuality(level);
        for (ModifierQuality quality : fallbackQualities(rolled)) {
            List<ModifierCandidate> list = byQuality.getOrDefault(quality, List.of());
            if (!list.isEmpty()) {
                ModifierCandidate candidate = weightedPick(list);
                return Optional.of(new SelectedModifier(candidate.id(), quality, candidate.node()));
            }
        }
        return Optional.empty();
    }

    private Optional<SelectedSocketModifier> selectSocketModifier(ItemSpec resultSpec, int level) {
        Optional<SocketGroup> socketGroup = findSocketGroup(resultSpec);
        if (socketGroup.isEmpty()) {
            return Optional.empty();
        }

        SocketGroup group = socketGroup.get();
        int socketCount = rollSocketCount(group.tier(), level);
        if (socketCount <= 0) {
            return Optional.empty();
        }

        String expectedModifierId = "mdv_socket_" + group.family() + "_" + socketCount;
        Map<String, Object> leafNodes = new LinkedHashMap<>();
        collectLeafNodes(group.node(), leafNodes, new HashSet<>());
        Object modifierNode = leafNodes.get(normalize(expectedModifierId));
        if (modifierNode == null) {
            plugin.getLogger().warning("Socket group '" + group.id() + "' rolled " + socketCount
                    + " socket(s), but modifier '" + expectedModifierId + "' was not found inside the group.");
            return Optional.empty();
        }

        return Optional.of(new SelectedSocketModifier(
                normalize(expectedModifierId),
                modifierNode,
                group.id(),
                group.family(),
                group.tier(),
                socketCount
        ));
    }

    private Optional<SocketGroup> findSocketGroup(ItemSpec resultSpec) {
        Map<String, SocketGroup> groups = new LinkedHashMap<>();
        try {
            Object pluginInstance = getMmoItemsPlugin();
            if (pluginInstance == null) {
                return Optional.empty();
            }
            Object type = getMmoType(pluginInstance, resultSpec.getMmoType());
            if (type == null) {
                return Optional.empty();
            }
            Object templates = invokeNoArgs(pluginInstance, "getTemplates");
            Object template = invokeTemplateGetter(templates, type, resultSpec.getMmoId());
            if (template == null) {
                return Optional.empty();
            }

            Object hasGroup = invokeNoArgs(template, "hasModifierGroup");
            if (hasGroup instanceof Boolean && (Boolean) hasGroup) {
                collectSocketGroups(invokeNoArgs(template, "getModifierGroup"), groups, new HashSet<>());
            }

            Object modifiers = invokeNoArgs(template, "getModifiers");
            if (modifiers instanceof Map<?, ?> map) {
                for (Object value : map.values()) {
                    collectSocketGroups(value, groups, new HashSet<>());
                }
            }
        } catch (Throwable exception) {
            plugin.getLogger().warning("Could not read MMOItems socket modifier group: " + exception.getMessage());
            return Optional.empty();
        }

        if (groups.isEmpty()) {
            return Optional.empty();
        }
        SocketGroup selected = groups.values().iterator().next();
        if (groups.size() > 1) {
            plugin.getLogger().warning("MMOItem " + resultSpec.getMmoType() + ":" + resultSpec.getMmoId()
                    + " declares more than one MDV socket group. Using '" + selected.id() + "'.");
        }
        return Optional.of(selected);
    }

    private void collectSocketGroups(Object node, Map<String, SocketGroup> out, Set<Object> visited) {
        if (node == null || visited.contains(node)) {
            return;
        }
        visited.add(node);

        String rawId = readNodeId(node);
        String normalizedId = normalize(rawId);
        Matcher matcher = SOCKET_GROUP_PATTERN.matcher(normalizedId);
        if (matcher.matches()) {
            out.putIfAbsent(normalizedId, new SocketGroup(
                    normalizedId,
                    matcher.group(1).toLowerCase(Locale.ROOT),
                    Integer.parseInt(matcher.group(2)),
                    node
            ));
            return;
        }

        List<?> children = readChildren(node);
        if (children == null || children.isEmpty()) {
            return;
        }
        for (Object child : children) {
            collectSocketGroups(child, out, visited);
        }
    }

    private int rollSocketCount(int tier, int level) {
        Map<Integer, Double> chances = currentSocketChancesForLevel(tier, level);
        double total = 0.0D;
        for (double chance : chances.values()) {
            total += Math.max(0.0D, chance);
        }
        if (total <= 0.0D) {
            return 0;
        }

        double roll = random.nextDouble() * total;
        double cursor = 0.0D;
        for (int count = 0; count <= 3; count++) {
            cursor += Math.max(0.0D, chances.getOrDefault(count, 0.0D));
            if (roll <= cursor) {
                return count;
            }
        }
        return 0;
    }

    private Map<Integer, Double> currentSocketChancesForLevel(int tier, int level) {
        int min = getInt("forjador-modifiers.level.min", 1);
        int max = getInt("forjador-modifiers.level.max", 50);
        double t = max <= min ? 1.0D : (Math.max(min, Math.min(max, level)) - min) / (double) (max - min);

        String basePath = "forjador-sockets.chances.t" + tier;
        Map<Integer, Double> low = readSocketChancePoint(basePath + ".level-1", tier, false);
        Map<Integer, Double> high = readSocketChancePoint(basePath + ".level-50", tier, true);

        Map<Integer, Double> interpolated = new LinkedHashMap<>();
        for (int count = 0; count <= 3; count++) {
            double lowValue = low.getOrDefault(count, 0.0D);
            double highValue = high.getOrDefault(count, 0.0D);
            interpolated.put(count, Math.max(0.0D, lowValue + (highValue - lowValue) * t));
        }
        return interpolated;
    }

    private Map<Integer, Double> readSocketChancePoint(String path, int tier, boolean high) {
        Map<Integer, Double> values = new LinkedHashMap<>();
        for (int count = 0; count <= 3; count++) {
            values.put(count, getDouble(path + "." + count, defaultSocketChance(tier, high, count)));
        }
        return values;
    }

    private double defaultSocketChance(int tier, boolean high, int count) {
        return switch (tier) {
            case 2 -> {
                if (count == 0) {
                    yield high ? 35.0D : 65.0D;
                }
                if (count == 1) {
                    yield high ? 65.0D : 35.0D;
                }
                yield 0.0D;
            }
            case 3 -> {
                if (count == 0) {
                    yield high ? 5.0D : 25.0D;
                }
                if (count == 1) {
                    yield high ? 55.0D : 60.0D;
                }
                if (count == 2) {
                    yield high ? 40.0D : 15.0D;
                }
                yield 0.0D;
            }
            case 4 -> {
                if (count == 1) {
                    yield high ? 20.0D : 50.0D;
                }
                if (count == 2) {
                    yield high ? 55.0D : 45.0D;
                }
                if (count == 3) {
                    yield high ? 25.0D : 5.0D;
                }
                yield 0.0D;
            }
            case 5 -> {
                if (count == 1) {
                    yield high ? 5.0D : 15.0D;
                }
                if (count == 2) {
                    yield high ? 40.0D : 55.0D;
                }
                if (count == 3) {
                    yield high ? 55.0D : 30.0D;
                }
                yield 0.0D;
            }
            default -> 0.0D;
        };
    }

    private Map<String, Object> loadCandidateModifierNodes(ItemSpec resultSpec, String configuredPool) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            Object pluginInstance = getMmoItemsPlugin();
            if (pluginInstance == null) {
                return result;
            }
            Object type = getMmoType(pluginInstance, resultSpec.getMmoType());
            if (type == null) {
                return result;
            }
            Object templates = invokeNoArgs(pluginInstance, "getTemplates");
            if (templates == null) {
                return result;
            }

            if (configuredPool != null && !configuredPool.isBlank()) {
                Object node = invokeOneString(templates, "getModifierNode", configuredPool.toLowerCase(Locale.ROOT));
                if (node == null) {
                    node = invokeOneString(templates, "getModifierNode", configuredPool.toUpperCase(Locale.ROOT));
                }
                collectLeafNodes(node, result, new HashSet<>());
            }

            Object template = invokeTemplateGetter(templates, type, resultSpec.getMmoId());
            if (template != null) {
                Object hasGroup = invokeNoArgs(template, "hasModifierGroup");
                if (hasGroup instanceof Boolean && (Boolean) hasGroup) {
                    Object group = invokeNoArgs(template, "getModifierGroup");
                    collectLeafNodes(group, result, new HashSet<>());
                }
                Object modifiers = invokeNoArgs(template, "getModifiers");
                if (modifiers instanceof Map<?, ?> map) {
                    for (Object value : map.values()) {
                        collectLeafNodes(value, result, new HashSet<>());
                    }
                }
            }
        } catch (Throwable exception) {
            plugin.getLogger().warning("Could not read MMOItems modifier candidates: " + exception.getMessage());
        }
        return result;
    }

    private void collectLeafNodes(Object node, Map<String, Object> out, Set<Object> visited) {
        if (node == null || visited.contains(node)) {
            return;
        }
        visited.add(node);
        String id = readNodeId(node);
        List<?> children = readChildren(node);
        if (children == null || children.isEmpty()) {
            if (id != null && !id.isBlank()) {
                out.putIfAbsent(normalize(id), node);
            }
            return;
        }
        for (Object child : children) {
            collectLeafNodes(child, out, visited);
        }
    }

    private Map<ModifierQuality, List<ModifierCandidate>> classifyCandidates(Map<String, Object> candidates) {
        Map<ModifierQuality, List<ModifierCandidate>> result = new EnumMap<>(ModifierQuality.class);
        for (ModifierQuality quality : ModifierQuality.rollableValues()) {
            result.put(quality, new ArrayList<>());
        }

        Set<String> blockedExact = configuredIds("forjador-modifiers.qualities.blocked");
        List<String> blockedContains = configuredContains("forjador-modifiers.blocked-contains");

        for (Map.Entry<String, Object> entry : candidates.entrySet()) {
            String id = normalize(entry.getKey());
            if (isBlocked(id, blockedExact, blockedContains)) {
                continue;
            }
            ModifierQuality quality = configuredQualityOf(id);
            if (!quality.isRollable()) {
                continue;
            }
            double weight = configuredWeight(id, quality);
            result.get(quality).add(new ModifierCandidate(id, quality, entry.getValue(), weight));
        }
        return result;
    }

    private ModifierQuality configuredQualityOf(String id) {
        for (ModifierQuality quality : ModifierQuality.rollableValues()) {
            if (configuredIds("forjador-modifiers.qualities." + quality.configKey()).contains(id)) {
                return quality;
            }
        }
        String fallback = getString("forjador-modifiers.unclassified-as", "none");
        return ModifierQuality.fromConfig(fallback);
    }

    private Set<String> configuredIds(String path) {
        Set<String> values = new LinkedHashSet<>();
        for (String value : getStringList(path)) {
            if (value != null && !value.isBlank()) {
                values.add(normalize(value));
            }
        }
        return values;
    }

    private List<String> configuredContains(String path) {
        List<String> values = new ArrayList<>();
        for (String value : getStringList(path)) {
            if (value != null && !value.isBlank()) {
                values.add(normalize(value));
            }
        }
        return values;
    }

    private boolean isBlocked(String id, Set<String> blockedExact, List<String> blockedContains) {
        if (blockedExact.contains(id)) {
            return true;
        }
        for (String blocked : blockedContains) {
            if (!blocked.isBlank() && id.contains(blocked)) {
                return true;
            }
        }
        return false;
    }

    private double configuredWeight(String id, ModifierQuality quality) {
        String direct = "forjador-modifiers.weights." + id;
        if (isNumber(direct)) {
            return Math.max(0.0001D, getDouble(direct, 0.0D));
        }
        return Math.max(0.0001D, getDouble("forjador-modifiers.default-weight." + quality.configKey(), 1.0D));
    }

    private ModifierCandidate weightedPick(List<ModifierCandidate> candidates) {
        double total = 0.0D;
        for (ModifierCandidate candidate : candidates) {
            total += Math.max(0.0001D, candidate.weight());
        }
        double roll = random.nextDouble() * total;
        double cursor = 0.0D;
        for (ModifierCandidate candidate : candidates) {
            cursor += Math.max(0.0001D, candidate.weight());
            if (roll <= cursor) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private Map<ModifierQuality, Double> currentChancesForLevel(int level) {
        int min = getInt("forjador-modifiers.level.min", 1);
        int max = getInt("forjador-modifiers.level.max", 50);
        double t = max <= min ? 1.0D : (Math.max(min, Math.min(max, level)) - min) / (double) (max - min);

        Map<ModifierQuality, Double> low = readChancePoint("forjador-modifiers.chances.level-1");
        Map<ModifierQuality, Double> high = readChancePoint("forjador-modifiers.chances.level-50");
        Map<ModifierQuality, Double> interpolated = new EnumMap<>(ModifierQuality.class);
        for (ModifierQuality quality : ModifierQuality.rollableValues()) {
            double value = low.getOrDefault(quality, 0.0D) + (high.getOrDefault(quality, 0.0D) - low.getOrDefault(quality, 0.0D)) * t;
            interpolated.put(quality, Math.max(0.0D, value));
        }
        return interpolated;
    }

    private ModifierQuality rollQuality(int level) {
        Map<ModifierQuality, Double> interpolated = currentChancesForLevel(level);
        double total = 0.0D;
        for (ModifierQuality quality : ModifierQuality.rollableValues()) {
            total += Math.max(0.0D, interpolated.getOrDefault(quality, 0.0D));
        }
        if (total <= 0.0D) {
            return ModifierQuality.NORMAL;
        }
        double roll = random.nextDouble() * total;
        double cursor = 0.0D;
        for (ModifierQuality quality : new ModifierQuality[]{ModifierQuality.BAD, ModifierQuality.NORMAL, ModifierQuality.GOOD, ModifierQuality.VERY_GOOD}) {
            cursor += interpolated.getOrDefault(quality, 0.0D);
            if (roll <= cursor) {
                return quality;
            }
        }
        return ModifierQuality.NORMAL;
    }

    private Map<ModifierQuality, Double> readChancePoint(String path) {
        Map<ModifierQuality, Double> values = new EnumMap<>(ModifierQuality.class);
        for (ModifierQuality quality : ModifierQuality.rollableValues()) {
            values.put(quality, getDouble(path + "." + quality.configKey(), defaultChance(path, quality)));
        }
        return values;
    }

    private double defaultChance(String path, ModifierQuality quality) {
        boolean high = path.endsWith("level-50");
        return switch (quality) {
            case BAD -> high ? 5.0D : 40.0D;
            case NORMAL -> high ? 45.0D : 50.0D;
            case GOOD -> high ? 35.0D : 9.0D;
            case VERY_GOOD -> high ? 15.0D : 1.0D;
            default -> 0.0D;
        };
    }

    private List<ModifierQuality> fallbackQualities(ModifierQuality rolled) {
        return switch (rolled) {
            case VERY_GOOD -> List.of(ModifierQuality.VERY_GOOD, ModifierQuality.GOOD, ModifierQuality.NORMAL, ModifierQuality.BAD);
            case GOOD -> List.of(ModifierQuality.GOOD, ModifierQuality.NORMAL, ModifierQuality.VERY_GOOD, ModifierQuality.BAD);
            case NORMAL -> List.of(ModifierQuality.NORMAL, ModifierQuality.GOOD, ModifierQuality.BAD, ModifierQuality.VERY_GOOD);
            case BAD -> List.of(ModifierQuality.BAD, ModifierQuality.NORMAL, ModifierQuality.GOOD, ModifierQuality.VERY_GOOD);
            default -> List.of(ModifierQuality.NORMAL, ModifierQuality.BAD, ModifierQuality.GOOD, ModifierQuality.VERY_GOOD);
        };
    }

    private ItemStack buildMmoItemWithModifiers(ItemSpec spec, List<AppliedModifier> selectedModifiers) {
        try {
            Object pluginInstance = getMmoItemsPlugin();
            if (pluginInstance == null) {
                return null;
            }
            Object type = getMmoType(pluginInstance, spec.getMmoType());
            if (type == null) {
                return null;
            }
            Object templates = invokeNoArgs(pluginInstance, "getTemplates");
            Object template = invokeTemplateGetter(templates, type, spec.getMmoId());
            if (template == null) {
                return null;
            }

            Class<?> builderClass = Class.forName("net.Indyuce.mmoitems.api.item.build.MMOItemBuilder");
            Class<?> templateClass = Class.forName("net.Indyuce.mmoitems.api.item.template.MMOItemTemplate");
            Class<?> tierClass = Class.forName("net.Indyuce.mmoitems.api.ItemTier");
            Constructor<?> constructor = builderClass.getConstructor(templateClass, int.class, tierClass, boolean.class);
            Object builder = constructor.newInstance(template, 0, null, true);

            for (AppliedModifier selected : selectedModifiers) {
                Method whenCollected = selected.node().getClass().getMethod("whenCollected", builderClass, UUID.class);
                whenCollected.invoke(selected.node(), builder, UUID.randomUUID());
            }

            Object mmoItem = builderClass.getMethod("build").invoke(builder);
            Object stackBuilder = mmoItem.getClass().getMethod("newBuilder").invoke(mmoItem);
            Object stack = stackBuilder.getClass().getMethod("build").invoke(stackBuilder);
            return stack instanceof ItemStack ? (ItemStack) stack : null;
        } catch (Throwable exception) {
            plugin.getLogger().warning("Could not build modified MMOItem: " + exception.getMessage());
            return null;
        }
    }

    private int readForjadorLevel(Player player) {
        int min = getInt("forjador-modifiers.level.min", 1);
        int max = getInt("forjador-modifiers.level.max", 50);

        int apiLevel = readForjadorLevelFromMMOCoreApi(player);
        if (apiLevel > 0) {
            return clampLevel(apiLevel, min, max);
        }

        int papiLevel = readForjadorLevelFromPapi(player);
        if (papiLevel > 0) {
            return clampLevel(papiLevel, min, max);
        }

        return min;
    }

    private int readForjadorLevelFromPapi(Player player) {
        String professionId = getForjadorProfessionId();
        List<String> placeholders = new ArrayList<>();
        String configured = getString("forjador-modifiers.level.placeholder", "%mmocore_profession_" + professionId + "%");
        if (configured != null && !configured.isBlank()) {
            placeholders.add(configured);
        }
        placeholders.add("%mmocore_profession_" + professionId + "%");
        placeholders.add("%mmocore_profession_level_" + professionId + "%");

        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                return -1;
            }
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method setPlaceholders = papi.getMethod("setPlaceholders", Player.class, String.class);
            for (String placeholder : placeholders) {
                Object parsed = setPlaceholders.invoke(null, player, placeholder);
                int level = parseLevelStrict(String.valueOf(parsed));
                if (level > 0) {
                    return level;
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private int readForjadorLevelFromMMOCoreApi(Player player) {
        try {
            if (player == null || !Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
                return -1;
            }
            Class<?> playerDataClass = Class.forName("net.Indyuce.mmocore.api.player.PlayerData");
            try {
                Method has = playerDataClass.getMethod("has", Player.class);
                Object loaded = has.invoke(null, player);
                if (loaded instanceof Boolean && !((Boolean) loaded)) {
                    return -1;
                }
            } catch (NoSuchMethodException ignored) {
                // Older/newer MMOCore builds may not expose this exact method; try get() anyway.
            }

            Method get = playerDataClass.getMethod("get", org.bukkit.OfflinePlayer.class);
            Object playerData = get.invoke(null, player);
            if (playerData == null) {
                return -1;
            }
            Object professions = playerData.getClass().getMethod("getCollectionSkills").invoke(playerData);
            if (professions == null) {
                return -1;
            }
            Object level = professions.getClass().getMethod("getLevel", String.class).invoke(professions, getForjadorProfessionId());
            if (level instanceof Number number) {
                return number.intValue();
            }
            return parseLevelStrict(String.valueOf(level));
        } catch (Throwable ignored) {
            return -1;
        }
    }

    public List<String> debugForjadorLevel(Player player) {
        String professionId = getForjadorProfessionId();
        List<String> lines = new ArrayList<>();
        lines.add("&6Debug Forjador");
        lines.add("&7Profession ID: &e" + professionId);
        lines.add("&7MMOCore API level: &e" + readForjadorLevelFromMMOCoreApi(player));
        lines.add("&7PAPI configured: &e" + getString("forjador-modifiers.level.placeholder", "%mmocore_profession_" + professionId + "%"));
        lines.add("&7PAPI level parsed: &e" + readForjadorLevelFromPapi(player));
        lines.add("&7Final level usado: &a" + readForjadorLevel(player));
        lines.add("&7Dañado: &c" + formatCurrentChance(player, "bad") + "%");
        lines.add("&7Estable: &e" + formatCurrentChance(player, "normal") + "%");
        lines.add("&7Refinado: &a" + formatCurrentChance(player, "good") + "%");
        lines.add("&7Magistral: &2" + formatCurrentChance(player, "very-good") + "%");
        return lines;
    }

    private String getForjadorProfessionId() {
        String fromModifiers = getString("forjador-modifiers.level.profession-id", null);
        if (fromModifiers != null && !fromModifiers.isBlank()) {
            return fromModifiers.toLowerCase(Locale.ROOT);
        }
        return plugin.getConfig().getString("forjador.profession-id", "forjador").toLowerCase(Locale.ROOT);
    }

    private int clampLevel(int level, int min, int max) {
        return Math.max(min, Math.min(max, level));
    }

    private int parseLevelStrict(String raw) {
        if (raw == null) {
            return -1;
        }
        String trimmed = raw.trim();
        if (trimmed.isBlank() || trimmed.contains("%")) {
            return -1;
        }
        String cleaned = trimmed.replaceAll("[^0-9]", "");
        if (cleaned.isBlank()) {
            return -1;
        }
        try {
            return Math.max(1, Integer.parseInt(cleaned));
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private boolean hasAlreadyRolled(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(modifierAppliedKey, PersistentDataType.BYTE);
    }

    private ItemStack markAsRolled(
            ItemStack item,
            String modifierId,
            ModifierQuality quality,
            String recipeId,
            String prefix,
            SelectedSocketModifier socket
    ) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(modifierAppliedKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(modifierIdKey, PersistentDataType.STRING, modifierId == null ? "" : modifierId);
        pdc.set(modifierQualityKey, PersistentDataType.STRING, quality == null ? "none" : quality.configKey());
        pdc.set(modifierRecipeKey, PersistentDataType.STRING, recipeId == null ? "" : recipeId);
        if (prefix != null && !prefix.isBlank()) {
            pdc.set(modifierPrefixKey, PersistentDataType.STRING, prefix);
        }
        if (socket != null) {
            pdc.set(socketGroupKey, PersistentDataType.STRING, socket.groupId());
            pdc.set(socketModifierIdKey, PersistentDataType.STRING, socket.id());
            pdc.set(socketCountKey, PersistentDataType.INTEGER, socket.count());
        } else {
            pdc.set(socketCountKey, PersistentDataType.INTEGER, 0);
        }
        item.setItemMeta(meta);
        return item;
    }


    private void applyModifierPrefix(ItemStack item, String prefix) {
        if (item == null || item.getType().isAir() || prefix == null || prefix.isBlank()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        String currentName = meta.hasDisplayName() ? meta.getDisplayName() : null;
        if (currentName == null || currentName.isBlank()) {
            return;
        }
        String plainPrefix = ColorUtil.stripColor(prefix).trim();
        String plainName = ColorUtil.stripColor(currentName).trim();
        if (!plainPrefix.isBlank() && plainName.toLowerCase(Locale.ROOT).startsWith(plainPrefix.toLowerCase(Locale.ROOT))) {
            return;
        }
        meta.setDisplayName(ColorUtil.color(prefix).trim() + " " + currentName);
        item.setItemMeta(meta);
    }


    public boolean hasModifierData(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(modifierAppliedKey, PersistentDataType.BYTE);
    }

    /**
     * Restores MDVRecetas metadata and the visible quality prefix after MMOItems
     * rebuilds an item because of a Revision ID change. MMOItems can preserve
     * the native modifier StatHistory, but it does not know about the Bukkit
     * PDC or prefix overrides added by MDVRecetas after item generation.
     */
    public ItemStack restoreAfterRevision(ItemStack oldItem, ItemStack revisedItem) {
        return restoreAfterMmoItemsMutation(oldItem, revisedItem);
    }

    /**
     * Restores MDVRecetas modifier metadata and its visible prefix after any
     * MMOItems item rebuild, including Revision ID, gem insertion and removal.
     */
    public ItemStack restoreAfterMmoItemsMutation(ItemStack oldItem, ItemStack revisedItem) {
        if (oldItem == null || oldItem.getType().isAir()
                || revisedItem == null || revisedItem.getType().isAir()) {
            return revisedItem;
        }

        ItemMeta oldMeta = oldItem.getItemMeta();
        if (oldMeta == null) {
            return revisedItem;
        }
        PersistentDataContainer oldPdc = oldMeta.getPersistentDataContainer();
        if (!oldPdc.has(modifierAppliedKey, PersistentDataType.BYTE)) {
            return revisedItem;
        }

        String modifierId = oldPdc.get(modifierIdKey, PersistentDataType.STRING);
        String quality = oldPdc.get(modifierQualityKey, PersistentDataType.STRING);
        String recipeId = oldPdc.get(modifierRecipeKey, PersistentDataType.STRING);
        String prefix = oldPdc.get(modifierPrefixKey, PersistentDataType.STRING);
        String socketGroup = oldPdc.get(socketGroupKey, PersistentDataType.STRING);
        String socketModifierId = oldPdc.get(socketModifierIdKey, PersistentDataType.STRING);
        Integer socketCount = oldPdc.get(socketCountKey, PersistentDataType.INTEGER);

        if ((prefix == null || prefix.isBlank()) && modifierId != null && !modifierId.isBlank()) {
            prefix = getString("forjador-modifiers.prefix-overrides." + normalize(modifierId), "");
        }

        ItemStack restored = revisedItem.clone();
        ItemMeta newMeta = restored.getItemMeta();
        if (newMeta == null) {
            return revisedItem;
        }

        PersistentDataContainer newPdc = newMeta.getPersistentDataContainer();
        newPdc.set(modifierAppliedKey, PersistentDataType.BYTE, (byte) 1);
        newPdc.set(modifierIdKey, PersistentDataType.STRING, modifierId == null ? "" : modifierId);
        newPdc.set(modifierQualityKey, PersistentDataType.STRING, quality == null ? "none" : quality);
        newPdc.set(modifierRecipeKey, PersistentDataType.STRING, recipeId == null ? "" : recipeId);
        if (prefix != null && !prefix.isBlank()) {
            newPdc.set(modifierPrefixKey, PersistentDataType.STRING, prefix);
        }
        if (socketGroup != null && !socketGroup.isBlank()) {
            newPdc.set(socketGroupKey, PersistentDataType.STRING, socketGroup);
        }
        if (socketModifierId != null && !socketModifierId.isBlank()) {
            newPdc.set(socketModifierIdKey, PersistentDataType.STRING, socketModifierId);
        }
        if (socketCount != null) {
            newPdc.set(socketCountKey, PersistentDataType.INTEGER, Math.max(0, socketCount));
        }
        restored.setItemMeta(newMeta);

        applyModifierPrefix(restored, prefix);
        return restored;
    }

    private String readPrefixFormat(SelectedModifier selected) {
        String override = getString("forjador-modifiers.prefix-overrides." + selected.id(), "");
        if (override != null && !override.isBlank()) {
            return override;
        }
        return readPrefixFormatFromNode(selected.node());
    }

    private String readPrefixFormatFromNode(Object node) {
        if (node == null) {
            return null;
        }
        String direct = readStringPath(node, "prefix.format");
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        for (String methodName : List.of("getConfig", "getConfiguration", "getSection", "getYaml", "getObject")) {
            try {
                Object config = node.getClass().getMethod(methodName).invoke(node);
                String fromConfig = readStringPath(config, "prefix.format");
                if (fromConfig != null && !fromConfig.isBlank()) {
                    return fromConfig;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            Object modifier = node.getClass().getMethod("getModifier").invoke(node);
            if (modifier != null && modifier != node) {
                String fromModifier = readPrefixFormatFromNode(modifier);
                if (fromModifier != null && !fromModifier.isBlank()) {
                    return fromModifier;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String readStringPath(Object target, String path) {
        if (target == null || path == null || path.isBlank()) {
            return null;
        }
        if (target instanceof ConfigurationSection section) {
            return section.getString(path);
        }
        try {
            Object value = target.getClass().getMethod("getString", String.class).invoke(target, path);
            if (value != null) {
                return String.valueOf(value);
            }
        } catch (Throwable ignored) {
        }
        try {
            Object value = target.getClass().getMethod("get", String.class).invoke(target, path);
            if (value instanceof String string) {
                return string;
            }
        } catch (Throwable ignored) {
        }
        String[] parts = path.split("\\.");
        if (parts.length == 2) {
            Object child = null;
            if (target instanceof ConfigurationSection section) {
                child = section.getConfigurationSection(parts[0]);
            } else {
                try {
                    child = target.getClass().getMethod("get", String.class).invoke(target, parts[0]);
                } catch (Throwable ignored) {
                }
                if (child == null) {
                    try {
                        child = target.getClass().getMethod("getConfigurationSection", String.class).invoke(target, parts[0]);
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (child != null) {
                return readStringPath(child, parts[1]);
            }
        }
        return null;
    }

    private boolean getBoolean(String path, boolean def) {
        if (modifierConfig != null && modifierConfig.contains(path)) {
            return modifierConfig.getBoolean(path, def);
        }
        return plugin.getConfig().getBoolean(path, def);
    }

    private String getString(String path, String def) {
        if (modifierConfig != null && modifierConfig.contains(path)) {
            return modifierConfig.getString(path, def);
        }
        return plugin.getConfig().getString(path, def);
    }

    private int getInt(String path, int def) {
        if (modifierConfig != null && modifierConfig.contains(path)) {
            return modifierConfig.getInt(path, def);
        }
        return plugin.getConfig().getInt(path, def);
    }

    private double getDouble(String path, double def) {
        if (modifierConfig != null && modifierConfig.contains(path)) {
            return modifierConfig.getDouble(path, def);
        }
        return plugin.getConfig().getDouble(path, def);
    }

    private boolean isNumber(String path) {
        if (modifierConfig != null && modifierConfig.contains(path)) {
            return modifierConfig.isDouble(path) || modifierConfig.isInt(path);
        }
        return plugin.getConfig().isDouble(path) || plugin.getConfig().isInt(path);
    }

    private List<String> getStringList(String path) {
        if (modifierConfig != null && modifierConfig.contains(path)) {
            return modifierConfig.getStringList(path);
        }
        return plugin.getConfig().getStringList(path);
    }

    private Object getMmoItemsPlugin() throws ReflectiveOperationException {
        Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
        Field field = mmoItemsClass.getField("plugin");
        return field.get(null);
    }

    private Object getMmoType(Object pluginInstance, String typeId) throws ReflectiveOperationException {
        Object types = invokeNoArgs(pluginInstance, "getTypes");
        if (types == null) {
            return null;
        }
        return invokeOneString(types, "get", typeId == null ? "" : typeId.toUpperCase(Locale.ROOT));
    }

    private Object invokeTemplateGetter(Object templates, Object type, String itemId) throws ReflectiveOperationException {
        if (templates == null || type == null || itemId == null) {
            return null;
        }
        for (Method method : templates.getClass().getMethods()) {
            if (!method.getName().equals("getTemplate") || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params[0].isInstance(type) && params[1].equals(String.class)) {
                return method.invoke(templates, type, itemId.toUpperCase(Locale.ROOT));
            }
        }
        return null;
    }

    private Object invokeNoArgs(Object target, String methodName) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private Object invokeOneString(Object target, String methodName, String value) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Method method = target.getClass().getMethod(methodName, String.class);
        return method.invoke(target, value);
    }

    private String readNodeId(Object node) {
        try {
            Object id = node.getClass().getMethod("getId").invoke(node);
            return id == null ? null : String.valueOf(id);
        } catch (Throwable exception) {
            return null;
        }
    }

    private List<?> readChildren(Object node) {
        try {
            Object children = node.getClass().getMethod("getChildren").invoke(node);
            if (children instanceof List<?> list) {
                return list;
            }
            if (children instanceof Collection<?> collection) {
                return new ArrayList<>(collection);
            }
            if (children instanceof Map<?, ?> map) {
                return new ArrayList<>(map.values());
            }
            return Collections.emptyList();
        } catch (Throwable exception) {
            return Collections.emptyList();
        }
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private enum ModifierQuality {
        BAD("bad"),
        NORMAL("normal"),
        GOOD("good"),
        VERY_GOOD("very-good"),
        BLOCKED("blocked"),
        NONE("none");

        private final String configKey;

        ModifierQuality(String configKey) {
            this.configKey = configKey;
        }

        public String configKey() {
            return configKey;
        }

        public boolean isRollable() {
            return this == BAD || this == NORMAL || this == GOOD || this == VERY_GOOD;
        }

        public static List<ModifierQuality> rollableValues() {
            return List.of(BAD, NORMAL, GOOD, VERY_GOOD);
        }

        public static ModifierQuality fromConfig(String raw) {
            String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            for (ModifierQuality quality : values()) {
                if (quality.configKey.equals(normalized)) {
                    return quality;
                }
            }
            return NONE;
        }
    }

    private record ModifierCandidate(String id, ModifierQuality quality, Object node, double weight) {
    }

    private record SelectedModifier(String id, ModifierQuality quality, Object node) {
    }

    private record AppliedModifier(String id, Object node) {
    }

    private record SocketGroup(String id, String family, int tier, Object node) {
    }

    private record SelectedSocketModifier(
            String id,
            Object node,
            String groupId,
            String family,
            int tier,
            int count
    ) {
    }
}
