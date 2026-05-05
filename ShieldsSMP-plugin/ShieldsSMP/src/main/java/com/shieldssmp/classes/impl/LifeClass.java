package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import com.shieldssmp.data.PlayerData;
import com.shieldssmp.systems.ClassManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class LifeClass extends PlayerClass {

    /** Players currently under Decay Pulse debuff */
    private final Set<UUID> decayAffected = new HashSet<>();
    /** Players under Heart Siphon window */
    private final Map<UUID, Integer> siphonBonusHearts = new HashMap<>();

    @Override public String getName()         { return "Life"; }
    @Override public String getDescription()  { return "Master of vitality and stolen hearts"; }
    @Override public String getAbility1Name() { return "Decay Pulse"; }
    @Override public String getAbility2Name() { return "Heart Siphon"; }
    @Override public String getUltimateName() { return "Life Sacrifice"; }

    // ── Passive: Vitality – Golden Apple effects +50% ─────────────────────────
    @Override
    public void onConsumeItem(Player player, org.bukkit.inventory.ItemStack item) {
        if (item.getType() == Material.GOLDEN_APPLE || item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            // Extend regen and absorption by 50%
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 150, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,    300, 0));
            player.sendActionBar(Component.text("💚 Vitality: Golden Apple effects enhanced!", NamedTextColor.GREEN));
        }
    }

    // ── Ability 1: Decay Pulse ────────────────────────────────────────────────
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
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 300, 1));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 300, 2));
            victim.sendActionBar(Component.text("💀 Decay Pulse: healing reduced for 15s!", NamedTextColor.DARK_RED));

            new BukkitRunnable() { @Override public void run() { decayAffected.remove(vid); }
            }.runTaskLater(getPlugin(), 300L);
        }

        player.sendActionBar(Component.text("💚 Decay Pulse released!", NamedTextColor.GREEN, TextDecoration.BOLD));
    }

    // ── Ability 2: Heart Siphon ───────────────────────────────────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "HeartSiphon", 3 * MIN)) return;
        startCooldown(player, "HeartSiphon", 3 * MIN);

        UUID id = player.getUniqueId();
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,     200, 9)); // 20 temp hearts
        player.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST,   200, 4));
        siphonBonusHearts.put(id, 0);

        player.sendActionBar(Component.text("💗 Heart Siphon active! Kill to steal hearts!", NamedTextColor.RED, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 0.5f);

        new BukkitRunnable() { @Override public void run() {
            if (player.isOnline() && siphonBonusHearts.containsKey(id)) {
                siphonBonusHearts.remove(id);
                player.removePotionEffect(PotionEffectType.ABSORPTION);
                player.removePotionEffect(PotionEffectType.HEALTH_BOOST);
                player.sendActionBar(Component.text("💗 Heart Siphon ended", NamedTextColor.GRAY));
            }
        }}.runTaskLater(getPlugin(), 200L);
    }

    @Override
    public void onKill(Player killer, LivingEntity killed) {
        UUID id = killer.getUniqueId();
        if (!siphonBonusHearts.containsKey(id)) return;
        if (!(killed instanceof Player victim)) return;

        // Give killer permanent +1 heart until death (via health boost)
        int bonus = siphonBonusHearts.getOrDefault(id, 0) + 1;
        siphonBonusHearts.put(id, bonus);
        killer.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, Integer.MAX_VALUE, bonus - 1, false, false, false));
        killer.sendActionBar(Component.text("💗 Stole 1 heart from " + victim.getName() + "!", NamedTextColor.RED, TextDecoration.BOLD));
    }

    @Override
    public void onDeath(Player player) {
        super.onDeath(player);
        siphonBonusHearts.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.HEALTH_BOOST);
    }

    // ── Ultimate: Life Sacrifice ──────────────────────────────────────────────
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
            if (!(e instanceof Player p)) continue;

            if (p.equals(player)) {
                // Enemies: reduce max hearts for 12s
                // (handled below)
                continue;
            }

            if (p.getWorld().equals(player.getWorld())) {
                // Treat all nearby as allies for simplicity; SMP teams could filter here
                p.setHealth(p.getMaxHealth());
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 240, 4)); // 10 absorption
                p.sendActionBar(Component.text("💚 Life Sacrifice: healed to full!", NamedTextColor.GREEN, TextDecoration.BOLD));
                alliesHealed++;
            }
        }

        // Debuff enemies (all nearby players in opposing team – simplified: everyone in radius)
        for (Entity e : world.getNearbyEntities(loc, 15, 15, 15)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            // Apply health debuff -3 hearts (Weakness as proxy)
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 240, 2));
            p.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 240, -2)); // -3 hearts
        }

        player.sendActionBar(Component.text("💚 Life Sacrifice: healed " + alliesHealed + " allies!", NamedTextColor.GREEN, TextDecoration.BOLD));
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() {
        return ShieldsSMP.getInstance();
    }
}
