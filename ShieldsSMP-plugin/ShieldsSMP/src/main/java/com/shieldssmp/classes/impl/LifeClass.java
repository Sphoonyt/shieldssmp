package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class LifeClass extends PlayerClass {

    private final Set<UUID>          decayAffected = new HashSet<>();
    private final Set<UUID>          siphonActive  = new HashSet<>();
    private final Map<UUID, Integer> stolenHearts  = new HashMap<>();

    private static final double PASSIVE_MAX_HP = 30.0; // 15 hearts
    private static final double DEFAULT_MAX_HP = 20.0; // 10 hearts

    @Override public String getName()         { return "Life"; }
    @Override public String getDescription()  { return "Master of vitality and stolen hearts"; }
    @Override public String getAbility1Name() { return "Decay Pulse"; }
    @Override public String getAbility2Name() { return "Heart Siphon"; }
    @Override public String getUltimateName() { return "Life Sacrifice"; }

    // ── Passive: +5 max hearts. No regen. No unkillable. ─────────────────────
    @Override
    public void onEquip(Player player) {
        setMaxHP(player, PASSIVE_MAX_HP);
        // Do NOT give permanent regen - that was a bug
    }

    @Override
    public void onUnequip(Player player) {
        setMaxHP(player, DEFAULT_MAX_HP);
        stolenHearts.remove(player.getUniqueId());
        siphonActive.remove(player.getUniqueId());
    }

    @Override
    public void onDeath(Player player) {
        super.onDeath(player);
        stolenHearts.remove(player.getUniqueId());
        siphonActive.remove(player.getUniqueId());
        // Re-apply base 15 hearts after respawn
        new BukkitRunnable() {
            @Override public void run() {
                if (player.isOnline()) setMaxHP(player, PASSIVE_MAX_HP);
            }
        }.runTaskLater(getPlugin(), 5L);
    }

    // Enhanced gapple passive – works via consume hook
    @Override
    public void onConsumeItem(Player player, org.bukkit.inventory.ItemStack item) {
        if (item.getType() == Material.GOLDEN_APPLE || item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            // Extend effects slightly – do NOT give permanent regen
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 1)); // extra 4s regen II
            // Note: actual golden apple effects still apply on top
        }
    }

    // ── Ability 1: Decay Pulse ────────────────────────────────────────────────
    // Disables natural health regen + nerfs gapple healing for nearby enemies.
    // We apply Weakness + Poison (proxy for reduced healing) to enemies.
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "DecayPulse", 1 * MIN)) return;
        startCooldown(player, "DecayPulse", 1 * MIN);

        Location loc = player.getLocation();
        World world  = player.getWorld();
        world.spawnParticle(Particle.ANGRY_VILLAGER, loc, 30, 5, 1, 5);
        world.playSound(loc, Sound.ENTITY_WITHER_AMBIENT, 1f, 0.6f);

        for (Entity e : world.getNearbyEntities(loc, 10, 10, 10)) {
            if (!(e instanceof Player victim) || victim.equals(player)) continue;
            UUID vid = victim.getUniqueId();
            decayAffected.add(vid);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 300, 1));        // reduced damage + healing
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON,    60, 0));        // drains health
            victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 300, 2)); // debuff proxy
            victim.sendActionBar(Component.text("💀 Decay Pulse: healing disabled for 15s!", NamedTextColor.DARK_RED));
            new BukkitRunnable() {
                @Override public void run() { decayAffected.remove(vid); }
            }.runTaskLater(getPlugin(), 300L);
        }
        player.sendActionBar(Component.text("💚 Decay Pulse released!", NamedTextColor.GREEN, TextDecoration.BOLD));
    }

    // ── Ability 2: Heart Siphon – +20 real hearts for 10s, kill to keep ───────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "HeartSiphon", 3 * MIN)) return;
        startCooldown(player, "HeartSiphon", 3 * MIN);

        UUID id = player.getUniqueId();
        siphonActive.add(id);

        double currentMax = player.getMaxHealth();
        double boostedMax = currentMax + 40.0; // +20 hearts
        setMaxHP(player, boostedMax);
        player.setHealth(boostedMax); // Heal to new max

        player.sendActionBar(Component.text("💗 Heart Siphon: +20 real hearts for 10s! Kill to steal!", NamedTextColor.RED, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 0.5f);
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0,2,0), 10, 0.4, 0.4, 0.4);

        new BukkitRunnable() {
            @Override public void run() {
                siphonActive.remove(id);
                if (!player.isOnline()) return;
                int stolen = stolenHearts.getOrDefault(id, 0);
                double restoredMax = PASSIVE_MAX_HP + (stolen * 2.0);
                setMaxHP(player, restoredMax);
                if (player.getHealth() > restoredMax) player.setHealth(restoredMax);
                player.sendActionBar(Component.text("💗 Heart Siphon ended", NamedTextColor.GRAY));
            }
        }.runTaskLater(getPlugin(), 200L);
    }

    @Override
    public void onKill(Player killer, LivingEntity killed) {
        if (!siphonActive.contains(killer.getUniqueId())) return;
        if (!(killed instanceof Player)) return;
        int stolen = stolenHearts.merge(killer.getUniqueId(), 1, Integer::sum);
        double newMax = PASSIVE_MAX_HP + (stolen * 2.0);
        setMaxHP(killer, newMax);
        killer.sendActionBar(Component.text("💗 Stole 1 heart! Max: " + (int)(newMax/2) + " hearts", NamedTextColor.RED, TextDecoration.BOLD));
        killer.getWorld().spawnParticle(Particle.HEART, killer.getLocation().add(0,2,0), 5, 0.3, 0.3, 0.3);
    }

    // ── Ultimate: Life Sacrifice ───────────────────────────────────────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "LifeSacrifice", 5 * MIN)) return;
        startCooldown(player, "LifeSacrifice", 5 * MIN);

        Location loc = player.getLocation();
        World world  = player.getWorld();
        world.spawnParticle(Particle.HEART, loc, 60, 8, 2, 8);
        world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);

        int healed = 0;
        for (Entity e : world.getNearbyEntities(loc, 15, 15, 15)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            p.setHealth(p.getMaxHealth());
            p.removePotionEffect(PotionEffectType.ABSORPTION);
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 240, 4, false, true, true)); // 10 absorption hearts
            p.sendActionBar(Component.text("💚 Life Sacrifice: Healed + 10 absorption hearts!", NamedTextColor.GREEN, TextDecoration.BOLD));
            healed++;

            // Also reduce enemy max hearts by 3 for 12s
            double origMax = p.getMaxHealth();
            double debuffMax = Math.max(2.0, origMax - 6.0);
            setMaxHP(p, debuffMax);
            if (p.getHealth() > debuffMax) p.setHealth(debuffMax);
            new BukkitRunnable() {
                @Override public void run() { if (p.isOnline()) setMaxHP(p, origMax); }
            }.runTaskLater(getPlugin(), 240L);
        }

        player.sendActionBar(Component.text("💚 Life Sacrifice! " + healed + " allies healed!", NamedTextColor.GREEN, TextDecoration.BOLD));
    }

    private void setMaxHP(Player player, double hp) {
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr == null) return;
        attr.setBaseValue(hp);
        if (player.getHealth() > hp) player.setHealth(hp);
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
