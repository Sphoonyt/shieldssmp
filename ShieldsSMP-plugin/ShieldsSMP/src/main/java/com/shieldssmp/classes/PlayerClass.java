package com.shieldssmp.classes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public abstract class PlayerClass {

    protected final CooldownManager cd = new CooldownManager();

    // ── Identity ───────────────────────────────────────────────────────────────

    public abstract String getName();
    public abstract String getDescription();
    public abstract String getAbility1Name();
    public abstract String getAbility2Name();
    public abstract String getUltimateName();

    // Cooldown keys used in startCooldown() – override in each class
    public String getAbility1CooldownKey() { return getAbility1Name().replace(" ", ""); }
    public String getAbility2CooldownKey() { return getAbility2Name().replace(" ", ""); }
    public String getUltimateCooldownKey() { return getUltimateName().replace(" ", ""); }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /** Called every 10 ticks for passive tick effects */
    public void tickPassive(Player player) {}

    /** Called when this class is assigned to a player */
    public void onEquip(Player player) {}

    /** Called when this class is removed from a player */
    public void onUnequip(Player player) {}

    /** Called on player death */
    public void onDeath(Player player) { cd.clearPlayer(player.getUniqueId()); }

    // ── Abilities ──────────────────────────────────────────────────────────────

    public abstract void useAbility1(Player player);
    public abstract void useAbility2(Player player);
    public abstract void useUltimate(Player player);

    // ── Combat hooks ──────────────────────────────────────────────────────────

    public void onDealDamage(Player attacker, LivingEntity victim, double damage) {}
    public void onTakeDamage(Player victim, Entity attacker, double damage) {}
    public void onKill(Player killer, LivingEntity killed) {}
    public void onBreakBlock(Player player) {}
    public void onConsumeItem(Player player, ItemStack item) {}

    // ── Cooldown helpers ──────────────────────────────────────────────────────

    protected boolean checkCooldown(Player player, String key, long ms) {
        if (cd.isOnCooldown(player.getUniqueId(), key)) {
            long sec = cd.remainingSeconds(player.getUniqueId(), key);
            player.sendActionBar(Component.text(
                    "⏳ " + key + " ready in " + sec + "s", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    protected void startCooldown(Player player, String key, long ms) {
        cd.set(player.getUniqueId(), key, scaleByLives(player, ms));
    }

    /**
     * Start cooldown AND show a visual bar on the given item material.
     * The bar uses vanilla setCooldown() (same as ender pearl grey overlay).
     */
    protected void startCooldown(Player player, String key, long ms, org.bukkit.Material visual) {
        cd.set(player, key, scaleByLives(player, ms), visual);
    }

    /**
     * Scales a cooldown duration based on the player's current lives, UNLESS they
     * are holding/carrying the Warden's Pocket Watch, in which case it's a flat
     * 0.5x on the baseline duration — replacing the life-based multiplier entirely
     * (it does not stack on top of it).
     *
     * Life-based scaling (no watch):
     *   5 lives  → 1.00x (baseline, unchanged)
     *  10 lives  → 0.75x (25% faster cooldowns)
     *   1 life   → 1.25x (25% slower cooldowns)
     * Linear between those anchor points on each side of 5 lives.
     */
    protected long scaleByLives(Player player, long ms) {
        if (hasPocketWatch(player)) {
            return Math.round(ms * 0.5); // flat half cooldown, overrides life scaling
        }

        int lives = com.shieldssmp.ShieldsSMP.getInstance()
                .getLifeSystem().getLives(player.getUniqueId());
        lives = Math.max(0, Math.min(10, lives));

        double mult = lives >= 5
                ? 1.0 - 0.05  * (lives - 5)   // 5→1.00, 10→0.75
                : 1.0 + 0.0625 * (5 - lives); // 5→1.00, 1→1.25 (0→1.3125)

        return Math.round(ms * mult);
    }

    private boolean hasPocketWatch(Player player) {
        var mi = com.shieldssmp.ShieldsSMP.getInstance().getMythicalItems();
        if (mi.isPocketWatch(player.getInventory().getItemInMainHand())) return true;
        if (mi.isPocketWatch(player.getInventory().getItemInOffHand())) return true;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents())
            if (mi.isPocketWatch(item)) return true;
        return false;
    }

    public CooldownManager getCD() { return cd; }

    // ── Utility ───────────────────────────────────────────────────────────────

    protected static final long MIN  = 60_000L;
    protected static final long SEC  = 1_000L;
}
