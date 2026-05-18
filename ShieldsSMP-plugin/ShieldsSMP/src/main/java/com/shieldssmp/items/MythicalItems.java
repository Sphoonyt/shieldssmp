package com.shieldssmp.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class MythicalItems {

    private final NamespacedKey keyShieldedHelmet;
    private final NamespacedKey keyShieldedChest;
    private final NamespacedKey keyShieldedLegs;
    private final NamespacedKey keyShieldedBoots;
    private final NamespacedKey keyPocketWatch;
    private final NamespacedKey keyReaperScythe;
    private final NamespacedKey keyShieldedTotem;

    public MythicalItems(JavaPlugin plugin) {
        keyShieldedHelmet = new NamespacedKey(plugin, "shielded_helmet");
        keyShieldedChest  = new NamespacedKey(plugin, "shielded_chestplate");
        keyShieldedLegs   = new NamespacedKey(plugin, "shielded_leggings");
        keyShieldedBoots  = new NamespacedKey(plugin, "shielded_boots");
        keyPocketWatch    = new NamespacedKey(plugin, "wardens_pocket_watch");
        keyReaperScythe   = new NamespacedKey(plugin, "reapers_scythe");
        keyShieldedTotem  = new NamespacedKey(plugin, "shielded_totem");
    }

    // ── Shielded Helmet ───────────────────────────────────────────────────────

    public ItemStack buildShieldedHelmet() {
        ItemStack item = new ItemStack(Material.GOLDEN_HELMET);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Shielded Helmet", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                lore("Passive: Permanent Water Breathing", NamedTextColor.AQUA),
                lore("1/20 hit: Pulse disables enemy shields 30s", NamedTextColor.YELLOW),
                lore("         + 2 hearts true damage", NamedTextColor.YELLOW),
                lore("Prot IV Netherite equivalent | Unbreakable", NamedTextColor.GRAY),
                lore("✦ Shielded Set", NamedTextColor.GOLD)
        ));
        applyArmorStats(meta, 3, 3, EquipmentSlotGroup.HEAD);
        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyShieldedHelmet, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    // ── Shielded Chestplate ───────────────────────────────────────────────────

    public ItemStack buildShieldedChestplate() {
        ItemStack item = new ItemStack(Material.GOLDEN_CHESTPLATE);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Shielded Chestplate", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                lore("Passive: Permanent Resistance I", NamedTextColor.GREEN),
                lore("1/50 hit: Resistance V + 2x damage for 8s", NamedTextColor.YELLOW),
                lore("         Player glows grey while active", NamedTextColor.YELLOW),
                lore("Prot IV Netherite equivalent | Unbreakable", NamedTextColor.GRAY),
                lore("✦ Shielded Set", NamedTextColor.GOLD)
        ));
        applyArmorStats(meta, 8, 3, EquipmentSlotGroup.CHEST);
        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyShieldedChest, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    // ── Shielded Leggings ─────────────────────────────────────────────────────

    public ItemStack buildShieldedLeggings() {
        ItemStack item = new ItemStack(Material.GOLDEN_LEGGINGS);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Shielded Leggings", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                lore("Ability: Big Pockets – +10 inventory slots", NamedTextColor.YELLOW),
                lore("Prot IV Netherite equivalent | Unbreakable", NamedTextColor.GRAY),
                lore("✦ Shielded Set", NamedTextColor.GOLD)
        ));
        applyArmorStats(meta, 6, 3, EquipmentSlotGroup.LEGS);
        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyShieldedLegs, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    // ── Shielded Boots ────────────────────────────────────────────────────────

    public ItemStack buildShieldedBoots() {
        ItemStack item = new ItemStack(Material.GOLDEN_BOOTS);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Shielded Boots", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                lore("Passive: Speed II + Dolphin's Grace + Fall Immunity", NamedTextColor.AQUA),
                lore("Ability: 50+ block fall → Shockwave on land", NamedTextColor.YELLOW),
                lore("         5 hearts + 1.5s stun to nearby enemies", NamedTextColor.YELLOW),
                lore("Prot IV Netherite equivalent | Unbreakable", NamedTextColor.GRAY),
                lore("✦ Shielded Set", NamedTextColor.GOLD)
        ));
        applyArmorStats(meta, 3, 3, EquipmentSlotGroup.FEET);
        meta.addEnchant(Enchantment.PROTECTION,       4, true);
        meta.addEnchant(Enchantment.FEATHER_FALLING,  4, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyShieldedBoots, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    // ── Warden's Pocket Watch ─────────────────────────────────────────────────

    public ItemStack buildPocketWatch() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("⏱ Warden's Pocket Watch", NamedTextColor.DARK_AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                lore(""),
                lore("Right-Click: Activate Rewind", NamedTextColor.YELLOW),
                lore("Records your location, health & inventory.", NamedTextColor.GRAY),
                lore("After 6 seconds you revert to that state.", NamedTextColor.GRAY),
                lore(""),
                lore("Cooldown: 4 minutes", NamedTextColor.DARK_GRAY),
                lore("✦ Mythical Item", NamedTextColor.DARK_PURPLE)
        ));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyPocketWatch, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    // ── Reaper's Scythe ───────────────────────────────────────────────────────

    public ItemStack buildReaperScythe() {
        ItemStack item = new ItemStack(Material.NETHERITE_HOE);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("☽ Reaper's Scythe", NamedTextColor.DARK_RED, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                lore(""),
                lore("Passive: +25% damage to targets below 30% HP", NamedTextColor.RED),
                lore("Life Harvest: Killing a player sends their", NamedTextColor.DARK_RED),
                lore("  Life Essence to your inventory directly.", NamedTextColor.DARK_RED),
                lore(""),
                lore("+6 Attack Damage | 4.0 Attack Speed", NamedTextColor.GRAY),
                lore("✦ Mythical Item", NamedTextColor.DARK_PURPLE)
        ));
        meta.addEnchant(Enchantment.SHARPNESS,   6, true);
        meta.addEnchant(Enchantment.UNBREAKING, 10, true);
        meta.addEnchant(Enchantment.MENDING,     1, true);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyReaperScythe, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    // ── Shielded Totem ────────────────────────────────────────────────────────

    public ItemStack buildShieldedTotem() {
        ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta  meta = item.getItemMeta();
        meta.displayName(Component.text("✦ Shielded Totem", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                lore(""),
                lore("Passive: Soul Guard", NamedTextColor.YELLOW),
                lore("  Dying while holding negates life loss.", NamedTextColor.GRAY),
                lore("  No Life Essence drops. Totem shatters.", NamedTextColor.GRAY),
                lore(""),
                lore("Right-Click: Consume – grants nearby allies", NamedTextColor.AQUA),
                lore("  Resistance III + Regen IV for 10s (6 blocks)", NamedTextColor.AQUA),
                lore(""),
                lore("✦ Mythical Item", NamedTextColor.DARK_PURPLE)
        ));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(keyShieldedTotem, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    // ── Detection ─────────────────────────────────────────────────────────────

    public boolean isShieldedHelmet(ItemStack i)  { return hasPDC(i, Material.GOLDEN_HELMET,     keyShieldedHelmet); }
    public boolean isShieldedChest(ItemStack i)   { return hasPDC(i, Material.GOLDEN_CHESTPLATE, keyShieldedChest); }
    public boolean isShieldedLegs(ItemStack i)    { return hasPDC(i, Material.GOLDEN_LEGGINGS,   keyShieldedLegs); }
    public boolean isShieldedBoots(ItemStack i)   { return hasPDC(i, Material.GOLDEN_BOOTS,      keyShieldedBoots); }
    public boolean isPocketWatch(ItemStack i)     { return hasPDC(i, Material.CLOCK,             keyPocketWatch); }
    public boolean isReaperScythe(ItemStack i)    { return hasPDC(i, Material.NETHERITE_HOE,     keyReaperScythe); }
    public boolean isShieldedTotem(ItemStack i)   { return hasPDC(i, Material.TOTEM_OF_UNDYING,  keyShieldedTotem); }
    public boolean isAnyShieldedArmor(ItemStack i){ return isShieldedHelmet(i)||isShieldedChest(i)||isShieldedLegs(i)||isShieldedBoots(i); }

    private boolean hasPDC(ItemStack item, Material type, NamespacedKey key) {
        if (item == null || item.getType() != type) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
    }

    private void applyArmorStats(ItemMeta meta, double armor, double toughness, EquipmentSlotGroup slot) {
        String id = slot.toString().toLowerCase();
        meta.addAttributeModifier(Attribute.ARMOR,
                new AttributeModifier(new NamespacedKey("shieldssmp", "armor_" + id),
                        armor, AttributeModifier.Operation.ADD_NUMBER, slot));
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS,
                new AttributeModifier(new NamespacedKey("shieldssmp", "toughness_" + id),
                        toughness, AttributeModifier.Operation.ADD_NUMBER, slot));
        meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE,
                new AttributeModifier(new NamespacedKey("shieldssmp", "kbr_" + id),
                        0.1, AttributeModifier.Operation.ADD_NUMBER, slot));
    }

    private Component lore(String text, NamedTextColor color) {
        return Component.text("  " + text, color).decoration(TextDecoration.ITALIC, false);
    }
    private Component lore(String text) {
        return Component.text(text).decoration(TextDecoration.ITALIC, false);
    }
}
