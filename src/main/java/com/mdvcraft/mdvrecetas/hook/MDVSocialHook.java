package com.mdvcraft.mdvrecetas.hook;

import com.mdvcraft.mdvrecetas.MDVRecetasPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public final class MDVSocialHook {
    private final MDVRecetasPlugin plugin;
    private final boolean pluginPresent;
    private Method playSoundMethod;

    public MDVSocialHook(MDVRecetasPlugin plugin) {
        this.plugin = plugin;
        this.pluginPresent = Bukkit.getPluginManager().isPluginEnabled("MDVSocial");
        if (pluginPresent) {
            this.playSoundMethod = findPlaySoundMethod();
        }
    }

    public void play(Player player, String soundKey) {
        if (player == null || soundKey == null || soundKey.isBlank()) {
            return;
        }
        if (tryMdvSocial(player, soundKey)) {
            return;
        }
        playFallback(player, soundKey);
    }

    private boolean tryMdvSocial(Player player, String soundKey) {
        if (!pluginPresent || playSoundMethod == null) {
            return false;
        }
        try {
            playSoundMethod.invoke(null, player, soundKey);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private Method findPlaySoundMethod() {
        for (String className : new String[]{
                "com.mdvcraft.mdvsocial.api.MDVSocialAPI",
                "com.mdvcraft.mdvsocial.MDVSocialAPI",
                "com.mdvcraft.mdvsocial.api.MdvSocialAPI"
        }) {
            try {
                Class<?> apiClass = Class.forName(className);
                for (Method method : apiClass.getMethods()) {
                    if (!method.getName().equals("playUISound")) {
                        continue;
                    }
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == 2 && Player.class.isAssignableFrom(params[0]) && params[1].equals(String.class)) {
                        return method;
                    }
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    private void playFallback(Player player, String soundKey) {
        String normalized = soundKey.toLowerCase();
        try {
            switch (normalized) {
                case "back" -> player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.55F, 0.85F);
                case "close" -> player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.55F, 1.15F);
                case "page" -> player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7F, 1.1F);
                case "confirm" -> player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.65F, 1.25F);
                case "invalid" -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.65F, 0.65F);
                default -> player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.2F);
            }
        } catch (Exception exception) {
            if (plugin.getConfig().getBoolean("settings.debug", false)) {
                plugin.getLogger().warning("Could not play fallback UI sound: " + exception.getMessage());
            }
        }
    }
}
