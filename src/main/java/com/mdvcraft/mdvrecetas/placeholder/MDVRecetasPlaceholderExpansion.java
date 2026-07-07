package com.mdvcraft.mdvrecetas.placeholder;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import java.util.Locale;

public final class MDVRecetasPlaceholderExpansion extends PlaceholderExpansion {
    private final MDVRecetasPlugin plugin;

    public MDVRecetasPlaceholderExpansion(MDVRecetasPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "mdvrecetas";
    }

    @Override
    public String getAuthor() {
        return "MDVCRAFT";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) {
            return "";
        }

        String key = params.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "forjador_nivel", "forjador_level" -> String.valueOf(plugin.getForjadorModifierService().getForjadorLevel(player));

            // Nombres técnicos
            case "forjador_chance_bad" -> chance(player, "bad");
            case "forjador_chance_normal" -> chance(player, "normal");
            case "forjador_chance_good" -> chance(player, "good");
            case "forjador_chance_very_good", "forjador_chance_very-good" -> chance(player, "very-good");

            // Nombres roleros recomendados para la UI
            case "forjador_chance_danado", "forjador_chance_dañado" -> chance(player, "bad");
            case "forjador_chance_estable" -> chance(player, "normal");
            case "forjador_chance_refinado" -> chance(player, "good");
            case "forjador_chance_magistral" -> chance(player, "very-good");

            case "forjador_probabilidades_1", "forjador_chances_line_1" ->
                    "&7Dañado: &c" + chance(player, "bad") + "% &8| &7Estable: &e" + chance(player, "normal") + "%";
            case "forjador_probabilidades_2", "forjador_chances_line_2" ->
                    "&7Refinado: &a" + chance(player, "good") + "% &8| &7Magistral: &2" + chance(player, "very-good") + "%";
            case "forjador_probabilidades", "forjador_chances" ->
                    "&c" + chance(player, "bad") + "% &7Dañado &8| &e" + chance(player, "normal") + "% &7Estable &8| &a" + chance(player, "good") + "% &7Refinado &8| &2" + chance(player, "very-good") + "% &7Magistral";
            default -> null;
        };
    }

    private String chance(Player player, String quality) {
        return plugin.getForjadorModifierService().formatCurrentChance(player, quality);
    }
}
