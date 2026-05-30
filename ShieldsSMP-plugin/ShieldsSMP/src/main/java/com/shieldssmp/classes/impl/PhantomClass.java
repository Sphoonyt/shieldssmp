package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;

public class PhantomClass extends PlayerClass {

    private final Set<UUID> phasing  = new HashSet<>();
    private final Set<UUID> spectral = new HashSet<>();
    // revealed = cannot get invisibility for 2s after breaking block / attacking
    private final Set<UUID> revealed = new HashSet<>();

    @Override public String getName()         { return "Phantom"; }
    @Override public String getDescription()  { return "A shadow that slips between worlds"; }
    @Override public String getAbility1Name() { return "Phase"; }
    @Override public String getAbility2Name() { return "Void Blink"; }
    @Override public String getUltimateName() { return "Spectral Realm"; }

    // ── Passive: Shadow Crouch ────────────────────────────────────────────────
    @Override
    public void tickPassive(Player player) {
        if (spectral.contains(player.getUniqueId())) return;
        if (player.isSneaking() && !revealed.contains(player.getUniqueId())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30, 0, false, false, false));
        } else if (!player.isSneaking()) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

    // Breaking a block or attacking reveals you for 2s
    @Override public void onBreakBlock(Player player) { reveal(player); }
    @Override public void onDealDamage(Player attacker, org.bukkit.entity.LivingEntity victim, double damage) { reveal(attacker); }

    private void reveal(Player player) {
        UUID id = player.getUniqueId();
        revealed.add(id);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        new BukkitRunnable() {
            @Override public void run() {
                revealed.remove(id);
            }
        }.runTaskLater(getPlugin(), 40L);
    }

    // ── Ability 1: Phase – walk through solid blocks ──────────────────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "Phase", 2 * MIN)) return;
        startCooldown(player, "Phase", 2 * MIN, org.bukkit.Material.SHIELD);

        UUID id = player.getUniqueId();
        phasing.add(id);

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 1, false, false, false));
        player.sendActionBar(Component.text("👻 PHASE – walk through blocks for 8s!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f);

        // Every tick: nudge player through solid blocks
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!player.isOnline() || !phasing.contains(id) || ++ticks > 160) {
                    phasing.remove(id);
                    if (player.isOnline()) player.removePotionEffect(PotionEffectType.SPEED);
                    cancel(); return;
                }
                if (player.getLocation().getBlock().getType().isSolid()) {
                    // Push in movement direction to keep them going
                    Vector dir = player.getLocation().getDirection().normalize().multiply(0.4);
                    Location next = player.getLocation().clone().add(dir);
                    next.setYaw(player.getLocation().getYaw());
                    next.setPitch(player.getLocation().getPitch());
                    player.teleport(next);
                    player.getWorld().spawnParticle(Particle.PORTAL, next, 2, 0.1, 0.1, 0.1, 0.05);
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);

        new BukkitRunnable() {
            @Override public void run() {
                phasing.remove(id);
                if (player.isOnline()) {
                    player.removePotionEffect(PotionEffectType.SPEED);
                    player.sendActionBar(Component.text("👻 Phase ended", NamedTextColor.GRAY));
                }
            }
        }.runTaskLater(getPlugin(), 160L);
    }

    public boolean isPhasing(UUID id) { return phasing.contains(id); }

    // ── Ability 2: Void Blink – blink 8-12 blocks forward ────────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "VoidBlink", (long)(1.5 * MIN))) return;
        startCooldown(player, "VoidBlink", (long)(1.5 * MIN), org.bukkit.Material.SHIELD);

        Location eye = player.getEyeLocation();
        Vector step  = eye.getDirection().normalize().multiply(0.5);
        Location dest = eye.clone();
        Location safe = player.getLocation().clone();
        boolean wentThrough = false;

        for (int i = 0; i < 24; i++) {
            dest.add(step);
            if (!dest.getBlock().getType().isSolid()) {
                safe = dest.clone();
                safe.setYaw(player.getLocation().getYaw());
                safe.setPitch(player.getLocation().getPitch());
            } else {
                wentThrough = true;
            }
        }

        player.teleport(safe);
        player.getWorld().spawnParticle(Particle.PORTAL, safe, 30, 0.3, 0.5, 0.3, 0.1);
        player.getWorld().playSound(safe, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.8f);

        if (wentThrough) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            player.sendActionBar(Component.text("⚫ Void Blink – clipped block! Slowness II for 3s", NamedTextColor.DARK_PURPLE));
        } else {
            player.sendActionBar(Component.text("⚫ VOID BLINK!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        }
    }

    // ── Ultimate: Spectral Realm – 8s invulnerable + invisible + phase ────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "SpectralRealm", 5 * MIN)) return;
        startCooldown(player, "SpectralRealm", 5 * MIN, org.bukkit.Material.SHIELD);

        UUID id = player.getUniqueId();
        spectral.add(id);
        phasing.add(id); // can walk through blocks

        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,  160, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,    160, 255, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,         160, 2, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,  160, 0, false, false, false));
        player.setAllowFlight(true);
        player.setFlying(true);

        // Phase through blocks while in spectral realm
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!spectral.contains(id) || !player.isOnline() || ++t > 32) { cancel(); return; }
                if (player.getLocation().getBlock().getType().isSolid()) {
                    Vector dir = player.getLocation().getDirection().normalize().multiply(0.4);
                    Location next = player.getLocation().clone().add(dir);
                    next.setYaw(player.getLocation().getYaw());
                    next.setPitch(player.getLocation().getPitch());
                    player.teleport(next);
                }
                player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0,1,0), 3, 0.3, 0.5, 0.3, 0.05);
            }
        }.runTaskTimer(getPlugin(), 0L, 5L);

        player.showTitle(Title.title(
                Component.text("✦ SPECTRAL REALM", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text("8s – invisible, invulnerable, can phase", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(3), Duration.ofMillis(400))));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 2f);

        new BukkitRunnable() {
            @Override public void run() {
                spectral.remove(id);
                phasing.remove(id);
                if (!player.isOnline()) return;
                for (PotionEffectType t : List.of(PotionEffectType.INVISIBILITY, PotionEffectType.RESISTANCE,
                        PotionEffectType.SPEED, PotionEffectType.NIGHT_VISION))
                    player.removePotionEffect(t);
                if (!player.getGameMode().equals(GameMode.CREATIVE) && !player.getGameMode().equals(GameMode.SPECTATOR)) {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                }
                player.sendActionBar(Component.text("✦ Spectral Realm ended", NamedTextColor.GRAY));
            }
        }.runTaskLater(getPlugin(), 160L);
    }

    public boolean isSpectral(UUID id) { return spectral.contains(id); }
    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
