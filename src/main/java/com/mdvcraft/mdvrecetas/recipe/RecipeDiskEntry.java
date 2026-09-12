package com.mdvcraft.mdvrecetas.recipe;

import com.mdvcraft.mdvrecetas.model.MdvRecipe;

/**
 * Immutable representation of one enabled recipe loaded from disk.
 * The fingerprint is based only on that recipe's YAML node, so a full
 * /mdvrecetas reload can detect what actually changed without re-registering
 * every recipe in Bukkit.
 */
record RecipeDiskEntry(MdvRecipe recipe, String sourceFile, String fingerprint) {
}
