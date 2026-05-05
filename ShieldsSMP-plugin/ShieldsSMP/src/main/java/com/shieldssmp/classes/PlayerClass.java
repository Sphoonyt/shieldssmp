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
        cd.set(player.getUniqueId(), key, ms);
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    protected static final long MIN  = 60_000L;
    protected static final long SEC  = 1_000L;
}
