package com.mdvcraft.mdvrecetas.service;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecipeSignatureServiceTest {
    private static final UUID CREATOR = UUID.fromString("03183b9d-bae3-49e6-bdba-269a0a2cdcdb");
    private static final String FORGED = "&a &7🔨 &l&aForjado por: &e%player%";
    private final Map<ItemStack, TestItem> items = new IdentityHashMap<>();
    private YamlConfiguration config;
    private Material material;
    private Player creator;
    private RecipeSignatureService service;
    private NamespacedKey uuidKey;
    private NamespacedKey nameKey;
    private NamespacedKey recipeKey;
    private NamespacedKey otherKey;

    @BeforeEach
    void setup() {
        MDVRecetasPlugin plugin = mock(MDVRecetasPlugin.class);
        when(plugin.getName()).thenReturn("MDVRecetas");
        config = new YamlConfiguration();
        config.set("signature.enabled", true);
        config.set("signature.lore-lines", List.of("", FORGED));
        when(plugin.getConfig()).thenReturn(config);
        material = mock(Material.class);
        creator = mock(Player.class);
        when(creator.getUniqueId()).thenReturn(CREATOR);
        when(creator.getName()).thenReturn("Julia");
        uuidKey = new NamespacedKey(plugin, "crafted_by_uuid");
        nameKey = new NamespacedKey(plugin, "crafted_by_name");
        recipeKey = new NamespacedKey(plugin, "crafted_recipe_id");
        otherKey = new NamespacedKey(plugin, "unrelated_data");
        service = new RecipeSignatureService(plugin);
    }

    @Test
    void fiveRepairsWithUppercaseColorsAndResetPrefixesKeepOneSignature() {
        TestItem base = new TestItem(List.of("§7Durabilidad: 10", "§aDaño: 40"), Map.of(otherKey, "keep"));
        ItemStack current = service.applySignature(base.item, creator, "ESPADA");
        for (int repair = 1; repair <= 5; repair++) {
            List<String> rebuiltLore = new ArrayList<>(items.get(current).lore);
            rebuiltLore.set(0, "§7Durabilidad: " + (10 + repair));
            for (int i = 2; i < rebuiltLore.size(); i++) {
                rebuiltLore.set(i, mmoItemsFormatting(rebuiltLore.get(i)));
            }
            assertTrue(rebuiltLore.get(3).contains("§L§A"));
            TestItem rebuilt = new TestItem(rebuiltLore, Map.of(otherKey, "keep"));
            current = service.restoreAfterMmoItemsMutation(current, rebuilt.item);
            TestItem result = items.get(current);
            assertEquals(List.of("§7Durabilidad: " + (10 + repair), "§aDaño: 40", "", forged("Julia")), result.lore);
            assertEquals(CREATOR.toString(), result.data.get(uuidKey));
            assertEquals("Julia", result.data.get(nameKey));
            assertEquals("ESPADA", result.data.get(recipeKey));
            assertEquals("keep", result.data.get(otherKey));
            assertFalse(rebuilt.data.containsKey(uuidKey), "Repair result is cloned before adding the signature data");
        }
        assertEquals(List.of("§7Durabilidad: 10", "§aDaño: 40"), base.lore);
        assertFalse(base.data.containsKey(uuidKey));
    }

    @Test
    void cleansFiveExistingDuplicateBlocksAndKeepsTheirFirstPosition() {
        TestItem signed = signed(List.of("§aDaño: 40", "", forged("Julia")));
        List<String> duplicated = new ArrayList<>(List.of("§aDaño: 45"));
        for (int i = 0; i < 5; i++) {
            duplicated.add(mmoItemsFormatting(""));
            duplicated.add(mmoItemsFormatting(forged("Julia")));
        }
        duplicated.add("§bGema: RUBI");
        TestItem revised = new TestItem(duplicated, Map.of(otherKey, "unchanged"));
        TestItem result = restored(signed, revised);
        assertEquals(List.of("§aDaño: 45", "", forged("Julia"), "§bGema: RUBI"), result.lore);
        assertEquals("unchanged", result.data.get(otherKey));
    }

    @Test
    void multilineSignatureCollapsesOnlyCompleteBlocksAndPreservesOtherLoreAndSpacing() {
        config.set("signature.lore-lines", List.of("", "&aCreador: %player%", "", "&eReceta: %recipe%", ""));
        List<String> block = List.of("", "§aCreador: Julia", "", "§eReceta: ESPADA", "");
        TestItem signed = signed(block);
        List<String> lore = new ArrayList<>(List.of("§7Daño: 12", "", ""));
        lore.addAll(block.stream().map(RecipeSignatureServiceTest::mmoItemsFormatting).toList());
        lore.addAll(List.of("§7Daño: 12", "", "§eReceta: ESPADA", "§7Otra propiedad"));
        lore.addAll(block);
        lore.addAll(List.of("", "§bGema: DIAMANTE"));
        TestItem result = restored(signed, new TestItem(lore, Map.of()));
        List<String> expected = new ArrayList<>(List.of("§7Daño: 12", "", ""));
        expected.addAll(block);
        expected.addAll(List.of("§7Daño: 12", "", "§eReceta: ESPADA", "§7Otra propiedad", "", "§bGema: DIAMANTE"));
        assertEquals(expected, result.lore);
    }

    @Test
    void adjacentCopiesWithoutSpacersDoNotConsumeUnrelatedBlankLines() {
        TestItem signed = signed(List.of("", forged("Julia")));
        TestItem revised = new TestItem(List.of("§7Daño: 40", "", "", "",
                mmoItemsFormatting(forged("Julia")), mmoItemsFormatting(forged("Julia"))), Map.of());
        assertEquals(List.of("§7Daño: 40", "", "", "", forged("Julia")), restored(signed, revised).lore);
    }

    @Test
    void revisionMissingTheSignatureRestoresOriginalCreatorAndPreservesNewStats() {
        TestItem signed = signed(List.of("§aDaño: 10", "", forged("Julia")));
        TestItem revised = new TestItem(List.of("§aDaño: 80", "§dVelocidad: +15%"), Map.of(otherKey, "new-value"));
        TestItem result = items.get(service.restoreAfterRevision(signed.item, revised.item));
        assertEquals(List.of("§aDaño: 80", "§dVelocidad: +15%", "", forged("Julia")), result.lore);
        assertEquals(CREATOR.toString(), result.data.get(uuidKey));
        assertEquals("new-value", result.data.get(otherKey));
        verify(result.meta, never()).setDisplayName(anyString());
        verify(result.item, never()).setType(any());
    }

    @Test
    void alreadySignedCraftingIsIdempotentAndNeverChangesOriginalCreatorOrRecipe() {
        TestItem original = signed(List.of("§aDaño: 20", "", forged("Julia"), "", forged("Julia")));
        Player another = mock(Player.class);
        when(another.getUniqueId()).thenReturn(UUID.randomUUID());
        when(another.getName()).thenReturn("OtroJugador");
        ItemStack first = service.applySignature(original.item, another, "OTRA_RECETA");
        TestItem result = items.get(service.applySignature(first, another, "OTRA_RECETA"));
        assertEquals(List.of("§aDaño: 20", "", forged("Julia")), result.lore);
        assertEquals(CREATOR.toString(), result.data.get(uuidKey));
        assertEquals("Julia", result.data.get(nameKey));
        assertEquals("ESPADA", result.data.get(recipeKey));
        verify(another, never()).getUniqueId();
        verify(another, never()).getName();
        assertEquals(5, original.lore.size(), "Original item stays unchanged");
    }

    @Test
    void legacySingleLineConfigurationIsAlsoNormalizedAndCollapsed() {
        config.set("signature.lore-lines", null);
        config.set("signature.lore-line", "&aHecho por %player% (%recipe%)");
        TestItem signed = signed(List.of("§aHecho por Julia (ESPADA)"));
        TestItem revised = new TestItem(List.of("§7Daño: 40", "§f§AHecho por Julia (ESPADA)§r",
                "§r§aHecho por Julia (ESPADA)§r"), Map.of());
        assertEquals(List.of("§7Daño: 40", "§aHecho por Julia (ESPADA)"), restored(signed, revised).lore);
    }

    @Test
    void unsignedItemsRemainUntouchedEvenWhenLoreLooksLikeASignature() {
        TestItem unsigned = new TestItem(List.of("", forged("Julia")), Map.of());
        TestItem revised = new TestItem(List.of("§7Daño: 60", "", forged("Julia")), Map.of());
        assertSame(revised.item, service.restoreAfterMmoItemsMutation(unsigned.item, revised.item));
        verify(revised.item, never()).clone();
        assertFalse(service.hasSignatureData(unsigned.item));
        assertTrue(service.hasSignatureData(signed(List.of()).item));
    }

    @Test
    void disabledSigningAndInvalidItemsRemainUntouched() {
        TestItem item = new TestItem(List.of("§7Daño: 10"), Map.of());
        config.set("signature.enabled", false);
        assertSame(item.item, service.applySignature(item.item, creator, "ESPADA"));
        assertSame(item.item, service.applySignature(item.item, null, "ESPADA"));
        assertNull(service.applySignature(null, creator, "ESPADA"));
        assertSame(item.item, service.restoreAfterMmoItemsMutation(null, item.item));
        assertNull(service.restoreAfterMmoItemsMutation(item.item, null));
        when(material.isAir()).thenReturn(true);
        assertSame(item.item, service.restoreAfterMmoItemsMutation(item.item, item.item));
        assertFalse(service.hasSignatureData(item.item));
        verify(item.item, never()).clone();
    }

    @Test
    void blankOnlySignatureConfigurationDoesNotAppendSpacersOnEachMutation() {
        config.set("signature.lore-lines", List.of("", "&r"));
        TestItem signed = signed(List.of("§7Daño: 10", ""));
        TestItem revised = new TestItem(List.of("§7Daño: 15", ""), Map.of());
        assertEquals(revised.lore, restored(signed, revised).lore);
    }

    private TestItem signed(List<String> lore) {
        return new TestItem(lore, Map.of(uuidKey, CREATOR.toString(), nameKey, "Julia", recipeKey, "ESPADA"));
    }

    private TestItem restored(TestItem old, TestItem revised) {
        return items.get(service.restoreAfterMmoItemsMutation(old.item, revised.item));
    }

    private static String forged(String name) {
        return ColorUtil.color(FORGED.replace("%player%", name));
    }

    private static String mmoItemsFormatting(String line) {
        String uppercaseCodes = Pattern.compile("§([0-9a-fk-or])", Pattern.CASE_INSENSITIVE).matcher(line)
                .replaceAll(match -> "§" + match.group(1).toUpperCase(Locale.ROOT));
        return "§f§r" + uppercaseCodes + "§r";
    }

    /** Stateful item clones emulate Bukkit metadata without booting a server. */
    private final class TestItem {
        final ItemStack item = mock(ItemStack.class);
        final ItemMeta meta = mock(ItemMeta.class);
        final PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        final Map<NamespacedKey, String> data;
        List<String> lore;

        TestItem(List<String> lore, Map<NamespacedKey, String> data) {
            this.lore = new ArrayList<>(lore);
            this.data = new HashMap<>(data);
            items.put(item, this);
            when(item.getType()).thenReturn(material);
            when(item.getItemMeta()).thenReturn(meta);
            when(item.clone()).thenAnswer(invocation -> new TestItem(this.lore, this.data).item);
            when(meta.getPersistentDataContainer()).thenReturn(pdc);
            when(meta.hasLore()).thenAnswer(invocation -> !this.lore.isEmpty());
            when(meta.getLore()).thenAnswer(invocation -> new ArrayList<>(this.lore));
            doAnswer(invocation -> {
                this.lore = new ArrayList<>(invocation.<List<String>>getArgument(0));
                return null;
            }).when(meta).setLore(anyList());
            when(pdc.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                    .thenAnswer(invocation -> this.data.get(invocation.getArgument(0)));
            when(pdc.has(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                    .thenAnswer(invocation -> this.data.containsKey(invocation.getArgument(0)));
            doAnswer(invocation -> {
                this.data.put(invocation.getArgument(0), invocation.getArgument(2));
                return null;
            }).when(pdc).set(any(NamespacedKey.class), eq(PersistentDataType.STRING), anyString());
        }
    }
}
