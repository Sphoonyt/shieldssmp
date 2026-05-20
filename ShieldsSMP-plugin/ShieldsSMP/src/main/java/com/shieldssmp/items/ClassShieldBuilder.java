package com.shieldssmp.items;

import com.shieldssmp.data.PlayerData;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ClassShieldBuilder {

    // PDC keys
    private final NamespacedKey classShieldKey;
    private final NamespacedKey rerollKey;

    /** Class name → display colour */
    private static final Map<String, NamedTextColor> CLASS_COLORS = Map.of(
            "Phantom",   NamedTextColor.DARK_PURPLE,
            "Randomize", NamedTextColor.GOLD,
            "Larp",      NamedTextColor.AQUA,
            "Life",      NamedTextColor.GREEN,
            "Gravity",   NamedTextColor.BLUE,
            "Terrorist", NamedTextColor.RED,
            "Boss",      NamedTextColor.DARK_RED,
            "Super",     NamedTextColor.YELLOW
    );

    /** Class name → flavour description */
    private static final Map<String, String> CLASS_DESC = Map.of(
            "Phantom",   "A shadow that slips between worlds",
            "Randomize", "Chaos incarnate — luck favours the bold",
            "Larp",      "You become what you defeat",
            "Life",      "Master of vitality and stolen hearts",
            "Gravity",   "Bend the laws of physics",
            "Terrorist", "Controlled chaos and superior firepower",
            "Boss",      "Command the monsters; become the threat",
            "Super",     "Faster than a creeper, stronger than a golem"
    );

    public ClassShieldBuilder(JavaPlugin plugin) {
        classShieldKey = new NamespacedKey(plugin, "class_shield");
        rerollKey      = new NamespacedKey(plugin, "reroll_item");
    }

    // ── Build a class shield ──────────────────────────────────────────────────

    public ItemStack buildShield(String className) {
        ItemStack shield = new ItemStack(Material.SHIELD);
        ItemMeta  meta   = shield.getItemMeta();

        NamedTextColor color = CLASS_COLORS.getOrDefault(className, NamedTextColor.WHITE);
        String desc = CLASS_DESC.getOrDefault(className, "");

        meta.displayName(
                Component.text("✦ ", NamedTextColor.GRAY)
                         .append(Component.text(className + " Shield", color, TextDecoration.BOLD))
                         .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("  " + desc, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(""));
        lore.add(Component.text("  Class: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(className, color, TextDecoration.BOLD))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  This shield cannot be dropped.", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(""));
        lore.add(Component.text("  Sneak + Left Click  → Ability 1", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  Sneak + Right Click → Ability 2", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  Hold shield + Right Click → Ultimate", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  (Requires Ultimate Upgrader in inventory)", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(""));
        lore.add(Component.text("  Use a Reroll Totem to change class.", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        // NOT unbreakable - durability bar only shows on breakable items.
        // We prevent actual damage via PlayerItemDamageEvent in ShieldListener instead.
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        // PDC: store the class name so we can read it back
        meta.getPersistentDataContainer().set(classShieldKey, PersistentDataType.STRING, className);

        shield.setItemMeta(meta);
        return shield;
    }

    // ── Reroll Totem ──────────────────────────────────────────────────────────

    public ItemStack buildRerollTotem() {
        ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta  meta = item.getItemMeta();

        meta.displayName(Component.text("🎲 Reroll Totem", NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(List.of(
                Component.text(""),
                Component.text("  Right-click to randomly reroll your class.", NamedTextColor.GRAY)
                         .decoration(TextDecoration.ITALIC, false),
                Component.text("  Your current class shield will be replaced.", NamedTextColor.GRAY)
                         .decoration(TextDecoration.ITALIC, false),
                Component.text(""),
                Component.text("  ⚠ Cannot pick the same class twice in a row.", NamedTextColor.YELLOW)
                         .decoration(TextDecoration.ITALIC, false)
        ));

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(rerollKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    // ── Detection helpers ─────────────────────────────────────────────────────

    /** Returns the class name stored in this shield, or null if not a class shield */
    public String getShieldClass(ItemStack item) {
        if (item == null || item.getType() != Material.SHIELD) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(classShieldKey, PersistentDataType.STRING);
    }

    public boolean isClassShield(ItemStack item) {
        return getShieldClass(item) != null;
    }

    /**
     * Update shield durability bar to reflect remaining lives.
     * lives=10 → full bar (damage=0), lives=0 → empty bar (damage=max-1).
     * Shield stays unbreakable so it never actually breaks.
     */
    /**
     * Updates the shield's visual durability bar based on lives:
     *   10 lives → enchantment glint (special), durability almost full
     *    9 lives → full durability bar (damage = 0)
     *  8–1 lives → damage scales linearly (8 = slight crack … 1 = almost broken)
     *    0 lives → nearly broken (damage = maxDur - 1)
     */
    public void updateShieldDurability(org.bukkit.inventory.ItemStack shield, int lives) {
        if (shield == null || shield.getType() != org.bukkit.Material.SHIELD) return;
        org.bukkit.inventory.meta.ItemMeta rawMeta = shield.getItemMeta();
        if (!(rawMeta instanceof Damageable dmg)) return;

        short maxDur = shield.getType().getMaxDurability(); // 336

        // Glint at 10 lives
        dmg.setEnchantmentGlintOverride(lives >= 10);

        // Durability: 9=full(0), 0=almost broken(maxDur-1)
        int clampedLives = Math.max(0, Math.min(lives, 9));
        int damage = (int) Math.round((1.0 - (double) clampedLives / 9.0) * (maxDur - 1));
        damage = Math.max(0, Math.min(maxDur - 1, damage));
        dmg.setDamage(damage);

        shield.setItemMeta((org.bukkit.inventory.meta.ItemMeta) dmg);
    }

    public boolean isRerollTotem(ItemStack item) {
        if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(rerollKey, PersistentDataType.BOOLEAN);
    }
}
