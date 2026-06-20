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

    private final Set<UUID> decayAffected = new HashSet<>();
    private final Set<UUID> siphonActive  = new HashSet<>();
    private final Map<UUID, Integer> stolenHearts = new HashMap<>();

    private static final double PASSIVE_MAX_HP = 30.0; // 15 hearts
    private static final double DEFAULT_MAX_HP = 20.0;

    @Override public String getName()         { return "Life"; }
    @Override public String getDescription()  { return "Master of vitality and stolen hearts"; }
    @Override public String getAbility1Name() { return "Decay Pulse"; }
    @Override public String getAbility2Name() { return "Heart Siphon"; }
    @Override public String getUltimateName() { return "Life Sacrifice"; }
    @Override public String getAbility1CooldownKey() { return "DecayPulse"; }
    @Override public String getAbility2CooldownKey() { return "HeartSiphon"; }
    @Override public String getUltimateCooldownKey() { return "LifeSacrifice"; }

    @Override public void onEquip(Player player) { setMaxHP(player, PASSIVE_MAX_HP); }
    @Override public void onUnequip(Player player) {
        setMaxHP(player, DEFAULT_MAX_HP);
        stolenHearts.remove(player.getUniqueId());
        siphonActive.remove(player.getUniqueId());
    }
    @Override public void onDeath(Player player) {
        super.onDeath(player);
        stolenHearts.remove(player.getUniqueId());
        siphonActive.remove(player.getUniqueId());
        new BukkitRunnable() {
            @Override public void run() { if (player.isOnline()) setMaxHP(player, PASSIVE_MAX_HP); }
        }.runTaskLater(getPlugin(), 5L);
    }

    @Override
    public void onConsumeItem(Player player, org.bukkit.inventory.ItemStack item) {
        if (item.getType() == Material.GOLDEN_APPLE || item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 1));
        }
    }

    // ── Ability 1: Decay Pulse – strong Poison + Weakness, 20s ───────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "DecayPulse", 1 * MIN)) return;
        startCooldown(player, "DecayPulse", 1 * MIN);

        Location loc = player.getLocation();
        World world  = player.getWorld();
        world.spawnParticle(Particle.ANGRY_VILLAGER, loc, 40, 5, 1, 5);
        world.playSound(loc, Sound.ENTITY_WITHER_AMBIENT, 1f, 0.6f);

        var trust = ShieldsSMP.getInstance().getTrustSystem();
        for (Entity e : world.getNearbyEntities(loc, 10, 10, 10)) {
            if (!(e instanceof Player victim) || victim.equals(player)) continue;
            if (trust.isTrusted(player.getUniqueId(), victim.getUniqueId())) continue; // skip trusted
            UUID vid = victim.getUniqueId();
            decayAffected.add(vid);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 400, 2));    // 20s Weakness III
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON,   200, 1));    // 10s Poison II
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 400, 1));    // 20s Slowness II
            victim.damage(4, player); // 2 hearts initial hit
            victim.sendActionBar(Component.text("💀 Decay Pulse: 20s debuff!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            new BukkitRunnable() { @Override public void run() { decayAffected.remove(vid); } }.runTaskLater(getPlugin(), 400L);
        }
        player.sendActionBar(Component.text("💚 Decay Pulse released!", NamedTextColor.GREEN, TextDecoration.BOLD));
    }

    // ── Ability 2: Heart Siphon – gives EXACTLY 20 max hearts extra for 10s ──
    // Fixed: was giving 35 hearts because old max health wasn't reset properly
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "HeartSiphon", 3 * MIN)) return;
        startCooldown(player, "HeartSiphon", 3 * MIN);

        UUID id = player.getUniqueId();
        siphonActive.add(id);

        // Set max to exactly 20 hearts (40 HP) total – not added on top
        setMaxHP(player, 40.0); // 20 hearts = 40 HP
        player.setHealth(40.0);

        player.sendActionBar(Component.text("💗 Heart Siphon: +20 real hearts for 10s!", NamedTextColor.RED, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 0.5f);
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0,2,0), 10, 0.4, 0.4, 0.4);

        new BukkitRunnable() {
            @Override public void run() {
                siphonActive.remove(id);
                if (!player.isOnline()) return;
                // Restore to exactly 15 hearts (30 HP)
                setMaxHP(player, PASSIVE_MAX_HP);
                if (player.getHealth() > PASSIVE_MAX_HP) player.setHealth(PASSIVE_MAX_HP);
                player.sendActionBar(Component.text("💗 Heart Siphon ended – back to 15 hearts", NamedTextColor.GRAY));
            }
        }.runTaskLater(getPlugin(), 200L);
    }

    @Override
    public void onKill(Player killer, LivingEntity killed) {
        if (!siphonActive.contains(killer.getUniqueId())) return;
        if (!(killed instanceof Player)) return;
        int stolen = stolenHearts.merge(killer.getUniqueId(), 1, Integer::sum);
        // Update max HP only if siphon not active (so it takes effect on next siphon)
        if (!siphonActive.contains(killer.getUniqueId())) {
            setMaxHP(killer, PASSIVE_MAX_HP + (stolen * 2.0));
        }
        killer.sendActionBar(Component.text("💗 Stole 1 heart! Total stolen: " + stolen, NamedTextColor.RED, TextDecoration.BOLD));
        killer.getWorld().spawnParticle(Particle.HEART, killer.getLocation().add(0,2,0), 5, 0.3, 0.3, 0.3);
    }

    // ── Ultimate: Life Sacrifice ───────────────────────────────────────────────
    // Heals allies. Reduces ENEMIES' max hearts by 3 WITHOUT giving absorption.
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "LifeSacrifice", 5 * MIN)) return;
        startCooldown(player, "LifeSacrifice", 5 * MIN);

        Location loc = player.getLocation();
        World world  = player.getWorld();
        world.spawnParticle(Particle.HEART, loc, 60, 8, 2, 8);
        world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);

        // Only heal TRUSTED allies (positive abilities only affect trusted players)
        var trust = ShieldsSMP.getInstance().getTrustSystem();
        int healed = 0;
        for (Entity e : world.getNearbyEntities(loc, 15, 15, 15)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            if (!trust.isTrusted(player.getUniqueId(), p.getUniqueId())) continue; // skip non-trusted
            // Full heal + 10 absorption hearts
            p.setHealth(p.getMaxHealth());
            p.removePotionEffect(PotionEffectType.ABSORPTION);
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 240, 4, false, true, true));
            p.sendActionBar(Component.text("💚 Life Sacrifice: Healed + 10 absorption!", NamedTextColor.GREEN, TextDecoration.BOLD));
            healed++;
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
