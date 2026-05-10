package com.shieldssmp.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class SpecialItems {

    private static final String KEY_NULLIFIER       = "nullifier_axe";
    private static final String KEY_ESSENCE         = "life_essence";
    private static final String KEY_UPGRADE         = "upgrade_core";
    private static final String KEY_ULT_UPGRADER    = "ultimate_upgrader";

    private final NamespacedKey nullifierKey;
    private final NamespacedKey essenceKey;
    private final NamespacedKey upgradeKey;
    private final NamespacedKey ultUpgraderKey;

    public SpecialItems(JavaPlugin plugin) {
        nullifierKey    = new NamespacedKey(plugin, KEY_NULLIFIER);
        essenceKey      = new NamespacedKey(plugin, KEY_ESSENCE);
        upgradeKey      = new NamespacedKey(plugin, KEY_UPGRADE);
        ultUpgraderKey  = new NamespacedKey(plugin, KEY_ULT_UPGRADER);
    }

    // ── Builders ───────────────────────────────────────────────────────────────

    public ItemStack buildNullifierAxe() {
        ItemStack item = new ItemStack(Material.NETHERITE_AXE);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("Nullifier Axe", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(""),
                Component.text("  On Hit: ", NamedTextColor.RED, TextDecoration.BOLD)
                         .append(Component.text("Disables target's abilities for 60s", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)),
                Component.text("  Cooldown: ", NamedTextColor.YELLOW, TextDecoration.BOLD)
                         .append(Component.text("3 minutes", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)),
                Component.text(""),
                Component.text("  ☠ Mythical Item", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false)
        ));
        meta.addEnchant(Enchantment.SHARPNESS,   5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(nullifierKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack buildLifeEssence() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Life Essence", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(""),
                Component.text("  Right-click to use and gain +1 Life", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("  Dropped on death", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(essenceKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack buildUpgradeCore() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("⚡ Class Upgrade Core", NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(""),
                Component.text("  Right-click to upgrade your class to Level 2", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("  Unlocks Ability 1, Ability 2, and Ultimate", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text(""),
                Component.text("  Requires: Level 1 class already selected", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(upgradeKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    // ── Ultimate Upgrader builder ─────────────────────────────────────────────────

    public ItemStack buildUltimateUpgrader() {
        ItemStack item = new ItemStack(Material.HEAVY_CORE);
        ItemMeta  meta = item.getItemMeta();

        meta.displayName(
                Component.text("⚡ Ultimate Upgrader", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)
                         .decoration(TextDecoration.ITALIC, false));

        meta.lore(List.of(
                Component.text(""),
                Component.text("  Hold & Right-Click to unleash", NamedTextColor.GRAY)
                         .decoration(TextDecoration.ITALIC, false),
                Component.text("  your class Ultimate ability.", NamedTextColor.GRAY)
                         .decoration(TextDecoration.ITALIC, false),
                Component.text(""),
                Component.text("  Requires Level 2 class.", NamedTextColor.DARK_GRAY)
                         .decoration(TextDecoration.ITALIC, false)
        ));

        // Enchant glint with a harmless enchant at level 0 trick via unsafe enchant
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        meta.getPersistentDataContainer().set(ultUpgraderKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    // ── Detection ──────────────────────────────────────────────────────────────

    public boolean isNullifierAxe(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_AXE) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(nullifierKey, PersistentDataType.BOOLEAN);
    }

    public boolean isLifeEssence(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(essenceKey, PersistentDataType.BOOLEAN);
    }

    public boolean isUpgradeCore(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(upgradeKey, PersistentDataType.BOOLEAN);
    }

    public boolean isUltimateUpgrader(ItemStack item) {
        if (item == null || item.getType() != Material.HEAVY_CORE) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(ultUpgraderKey, PersistentDataType.BOOLEAN);
    }
}
