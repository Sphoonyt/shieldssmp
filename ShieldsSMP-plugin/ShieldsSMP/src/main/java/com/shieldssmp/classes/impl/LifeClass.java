package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class LifeClass extends PlayerClass {

    private static final String STOLEN_MOD_KEY = "life_stolen_hearts";
    private final Set<UUID>          decayAffected = new HashSet<>();
    private final Set<UUID>          siphonActive  = new HashSet<>();
    /** Stolen hearts count per player – saved across respawn via max-health modifier */
    private final Map<UUID, Integer> stolenHearts  = new HashMap<>();

    private static final double BASE_MAX_HP    = 30.0; // 15 hearts (passive +5)
    private static final double DEFAULT_MAX_HP = 20.0; // 10 hearts

    @Override public String getName()         { return "Life"; }
    @Override public String getDescription()  { return "Master of vitality and stolen hearts"; }
    @Override public String getAbility1Name() { return "Decay Pulse"; }
    @Override public String getAbility2Name() { return "Heart Siphon"; }
    @Override public String getUltimateName() { return "Life Sacrifice"; }

    // ── Passive: Vitality – +5 permanent bonus hearts, enhanced gapples ───────
    @Override
    public void onEquip(Player player) {
        setBaseMaxHP(player, BASE_MAX_HP);
    }

    @Override
    public void onUnequip(Player player) {
        setBaseMaxHP(player, DEFAULT_MAX_HP);
        stolenHearts.remove(player.getUniqueId());
        siphonActive.remove(player.getUniqueId());
    }

    @Override
    public void onConsumeItem(Player player, org.bukkit.inventory.ItemStack item) {
        if (item.getType() == Material.GOLDEN_APPLE || item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 150, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,    300, 0));
            player.sendActionBar(Component.text("💚 Vitality: Enhanced healing!", NamedTextColor.GREEN));
        }
    }

    // ── Ability 1: Decay Pulse ─────────────────────────────────────────────────
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
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,       300, 1));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 300, 2));
            victim.sendActionBar(Component.text("💀 Decay Pulse: healing reduced for 15s!", NamedTextColor.DARK_RED));
            new BukkitRunnable() {
                @Override public void run() { decayAffected.remove(vid); }
            }.runTaskLater(getPlugin(), 300L);
        }
        player.sendActionBar(Component.text("💚 Decay Pulse released!", NamedTextColor.GREEN, TextDecoration.BOLD));
    }

    // ── Ability 2: Heart Siphon – +20 REAL hearts (max HP) for 10s ─────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "HeartSiphon", 3 * MIN)) return;
        startCooldown(player, "HeartSiphon", 3 * MIN);

        UUID id = player.getUniqueId();
        siphonActive.add(id);

        double currentMax = player.getMaxHealth();
        double boostedMax = currentMax + 40.0; // +20 real hearts = +40 HP

        setBaseMaxHP(player, boostedMax);
        // Also heal to full so they feel the benefit immediately
        player.setHealth(boostedMax);

        player.sendActionBar(Component.text("💗 Heart Siphon: +20 real hearts for 10s! Kill to steal!", NamedTextColor.RED, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 0.5f);
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 10, 0.4, 0.4, 0.4);

        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) { siphonActive.remove(id); return; }
                siphonActive.remove(id);
                // Restore max HP (base + any stolen hearts)
                int stolen = stolenHearts.getOrDefault(id, 0);
                double restoredMax = BASE_MAX_HP + (stolen * 2.0);
                setBaseMaxHP(player, restoredMax);
                // Clamp current health to new max
                if (player.getHealth() > restoredMax) player.setHealth(restoredMax);
                player.sendActionBar(Component.text("💗 Heart Siphon ended", NamedTextColor.GRAY));
            }
        }.runTaskLater(getPlugin(), 200L);
    }

    // Kill during siphon: steal 1 permanent max heart from victim ─────────────
    @Override
    public void onKill(Player killer, LivingEntity killed) {
        UUID id = killer.getUniqueId();
        if (!siphonActive.contains(id)) return;
        if (!(killed instanceof Player)) return;

        int stolen = stolenHearts.getOrDefault(id, 0) + 1;
        stolenHearts.put(id, stolen);

        // Stolen hearts are PERMANENT until death – increase max HP
        double newMax = BASE_MAX_HP + (stolen * 2.0); // +1 heart per kill
        setBaseMaxHP(killer, newMax);

        killer.sendActionBar(Component.text("💗 Stole a heart! Max now " + (int)(newMax/2) + " hearts!", NamedTextColor.RED, TextDecoration.BOLD));
        killer.getWorld().spawnParticle(Particle.HEART, killer.getLocation().add(0,2,0), 5, 0.3, 0.3, 0.3);
    }

    // On death: reset stolen hearts, restore base max HP ─────────────────────
    @Override
    public void onDeath(Player player) {
        super.onDeath(player);
        UUID id = player.getUniqueId();
        stolenHearts.remove(id);
        siphonActive.remove(id);
        // Will be re-equipped with BASE_MAX_HP on respawn via onEquip
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

        int alliesHealed = 0;
        for (Entity e : world.getNearbyEntities(loc, 15, 15, 15)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            // Heal to full + 10 absorption hearts
            p.setHealth(p.getMaxHealth());
            p.removePotionEffect(PotionEffectType.ABSORPTION);
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 240, 4, false, true, true));
            p.sendActionBar(Component.text("💚 Life Sacrifice: Healed + 10 absorption hearts!", NamedTextColor.GREEN, TextDecoration.BOLD));
            alliesHealed++;
        }

        // Enemies in radius: reduce max hearts by 3 for 12s
        for (Entity e : world.getNearbyEntities(loc, 15, 15, 15)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            double origMax = p.getMaxHealth();
            double debuffMax = Math.max(2.0, origMax - 6.0);
            setBaseMaxHP(p, debuffMax);
            if (p.getHealth() > debuffMax) p.setHealth(debuffMax);
            new BukkitRunnable() {
                @Override public void run() { if (p.isOnline()) setBaseMaxHP(p, origMax); }
            }.runTaskLater(getPlugin(), 240L);
        }

        player.sendActionBar(Component.text("💚 Life Sacrifice! " + alliesHealed + " allies healed!", NamedTextColor.GREEN, TextDecoration.BOLD));
    }

    // ── Utility ───────────────────────────────────────────────────────────────
    private void setBaseMaxHP(Player player, double hp) {
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr == null) return;
        attr.setBaseValue(hp);
        if (player.getHealth() > hp) player.setHealth(hp);
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
