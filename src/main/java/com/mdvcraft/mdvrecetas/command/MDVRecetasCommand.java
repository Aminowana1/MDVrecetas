package com.mdvcraft.mdvrecetas.command;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import com.mdvcraft.mdvrecetas.hook.MMOItemsHook;
import com.mdvcraft.mdvrecetas.util.ColorUtil;
import com.mdvcraft.mdvrecetas.util.ItemStackSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class MDVRecetasCommand implements CommandExecutor, TabCompleter {
    private final MDVRecetasPlugin plugin;

    public MDVRecetasCommand(MDVRecetasPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            handleOpen(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "open", "menu", "guia", "guide" -> handleOpen(sender);
            case "reload" -> handleReload(sender);
            case "debugitem" -> handleDebugItem(sender);
            case "debugforjador", "debugforge", "debuglevel" -> handleDebugForjador(sender);
            case "editor", "edit" -> handleEditor(sender);
            case "admin" -> handleAdmin(sender);
            case "serializehand" -> handleSerializeHand(sender, args);
            case "help", "ayuda" -> sendHelp(sender, label);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void handleOpen(CommandSender sender) {
        if (!sender.hasPermission("mdvrecetas.use")) {
            message(sender, "messages.no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            message(sender, "messages.player-only");
            return;
        }
        plugin.getRecipeGuiManager().openMain(player);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("mdvrecetas.admin")) {
            message(sender, "messages.no-permission");
            return;
        }
        message(sender, "messages.reload-start");
        try {
            plugin.reloadConfig();
            plugin.getForjadorModifierService().reload();
            int count = plugin.getRecipeManager().reloadRecipes();
            String msg = plugin.getConfig().getString("messages.reload-done", "&aRecetas recargadas: %count%")
                    .replace("%count%", String.valueOf(count));
            sender.sendMessage(prefix() + ColorUtil.color(msg));
        } catch (Exception exception) {
            plugin.getLogger().severe("Could not reload recipes: " + exception.getMessage());
            exception.printStackTrace();
            message(sender, "messages.reload-fail");
        }
    }


    private void handleEditor(CommandSender sender) {
        if (!sender.hasPermission("mdvrecetas.editor") && !sender.hasPermission("mdvrecetas.admin")) {
            message(sender, "messages.no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            message(sender, "messages.player-only");
            return;
        }
        plugin.getEditorGuiManager().openStationSelect(player);
    }

    private void handleAdmin(CommandSender sender) {
        if (!sender.hasPermission("mdvrecetas.admin")) {
            message(sender, "messages.no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            message(sender, "messages.player-only");
            return;
        }
        plugin.getRecipeGuiManager().openAdminMain(player);
    }

    private void handleDebugItem(CommandSender sender) {
        if (!sender.hasPermission("mdvrecetas.admin")) {
            message(sender, "messages.no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            message(sender, "messages.player-only");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            message(sender, "messages.no-item-in-hand");
            return;
        }

        sender.sendMessage(prefix() + ColorUtil.color(plugin.getConfig().getString("messages.debug-header", "&6Item en mano:")));
        for (String line : plugin.getItemResolver().describe(item).split("\n")) {
            sender.sendMessage(ColorUtil.color("&7" + line));
        }
    }

    private void handleDebugForjador(CommandSender sender) {
        if (!sender.hasPermission("mdvrecetas.admin")) {
            message(sender, "messages.no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            message(sender, "messages.player-only");
            return;
        }
        for (String line : plugin.getForjadorModifierService().debugForjadorLevel(player)) {
            sender.sendMessage(prefix() + ColorUtil.color(line));
        }
    }

    private void handleSerializeHand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mdvrecetas.admin")) {
            message(sender, "messages.no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            message(sender, "messages.player-only");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(prefix() + ColorUtil.color("&cUso: /mdvrecetas serializehand <id>"));
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            message(sender, "messages.no-item-in-hand");
            return;
        }

        String id = args[1].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        File folder = new File(plugin.getDataFolder(), "serialized-items");
        if (!folder.exists() && !folder.mkdirs()) {
            sender.sendMessage(prefix() + ColorUtil.color("&cNo se pudo crear la carpeta serialized-items."));
            return;
        }

        File file = new File(folder, id + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.set("item.kind", "ITEMSTACK");
            yaml.set("item.amount", item.getAmount());
            yaml.set("item.match", "SIMILAR");
            yaml.set("item.data", ItemStackSerializer.toBase64(item));
            yaml.set("item.preview.material", item.getType().name());
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                yaml.set("item.preview.name", item.getItemMeta().getDisplayName());
            }

            Optional<MMOItemsHook.MmoIdentity> identity = plugin.getItemResolver().getMmoItemsHook().readIdentity(item);
            identity.ifPresent(value -> {
                yaml.set("detected.kind", "MMOITEMS");
                yaml.set("detected.type", value.type());
                yaml.set("detected.id", value.id());
            });
            yaml.save(file);
        } catch (IOException exception) {
            sender.sendMessage(prefix() + ColorUtil.color("&cNo se pudo guardar el item: " + exception.getMessage()));
            return;
        }

        String msg = plugin.getConfig().getString("messages.serialize-saved", "&aItem guardado en &e%file%&a.")
                .replace("%file%", "serialized-items/" + file.getName());
        sender.sendMessage(prefix() + ColorUtil.color(msg));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(prefix() + ColorUtil.color("&e/" + label + " &7- Abre la guía de recetas."));
        sender.sendMessage(prefix() + ColorUtil.color("&e/" + label + " reload &7- Recarga recetas."));
        sender.sendMessage(prefix() + ColorUtil.color("&e/" + label + " debugitem &7- Revisa el item en mano."));
        sender.sendMessage(prefix() + ColorUtil.color("&e/" + label + " debugforjador &7- Revisa nivel/probabilidades de Forjador."));
        sender.sendMessage(prefix() + ColorUtil.color("&e/" + label + " admin &7- Abre el catálogo admin de recetas."));
        sender.sendMessage(prefix() + ColorUtil.color("&e/" + label + " editor &7- Crea una receta nueva sin acceso al catálogo admin."));
        sender.sendMessage(prefix() + ColorUtil.color("&e/" + label + " serializehand <id> &7- Guarda un ItemStack exacto."));
    }

    private void message(CommandSender sender, String path) {
        sender.sendMessage(prefix() + ColorUtil.color(plugin.getConfig().getString(path, "&cMensaje no configurado.")));
    }

    private String prefix() {
        return ColorUtil.color(plugin.getConfig().getString("messages.prefix", "&8[&6MDVRecetas&8] &r"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> available = new ArrayList<>(List.of("open", "help"));
            if (sender.hasPermission("mdvrecetas.editor") || sender.hasPermission("mdvrecetas.admin")) available.add("editor");
            if (sender.hasPermission("mdvrecetas.admin")) {
                available.addAll(List.of("reload", "debugitem", "debugforjador", "serializehand", "admin"));
            }
            String input = args[0].toLowerCase(Locale.ROOT);
            return available.stream().filter(option -> option.startsWith(input)).toList();
        }
        return List.of();
    }
}
