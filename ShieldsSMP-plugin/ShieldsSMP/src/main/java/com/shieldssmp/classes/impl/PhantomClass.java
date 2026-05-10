package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;

public class PhantomClass extends PlayerClass {

    private final Set<UUID> phasing   = new HashSet<>();
    private final Set<UUID> spectral  = new HashSet<>();
    private final Set<UUID> revealed  = new HashSet<>();

    @Override public String getName()         { return "Phantom"; }
    @Override public String getDescription()  { return "A shadow that slips between worlds"; }
    @Override public String getAbility1Name() { return "Phase"; }
    @Override public String getAbility2Name() { return "Void Blink"; }
    @Override public String getUltimateName() { return "Spectral Realm"; }

    // ── Passive: Shadow Crouch – invisible while sneaking ────────────────────
    @Override
    public void tickPassive(Player player) {
        if (spectral.contains(player.getUniqueId())) return;
        if (player.isSneaking() && !revealed.contains(player.getUniqueId())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30, 0, false, false, false));
        }
    }

    @Override
    public void onBreakBlock(Player player) { reveal(player); }

    @Override
    public void onDealDamage(Player attacker, LivingEntity victim, double damage) { reveal(attacker); }

    private void reveal(Player player) {
        UUID id = player.getUniqueId();
        if (revealed.contains(id)) return;
        revealed.add(id);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        new BukkitRunnable() {
            @Override public void run() {
                revealed.remove(id);
            }
        }.runTaskLater(getPlugin(), 40L); // 2s reveal
    }

    // ── Ability 1: Phase – move through solid blocks for 8s ─────────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "Phase", 2 * MIN)) return;
        startCooldown(player, "Phase", 2 * MIN);

        UUID id = player.getUniqueId();
        phasing.add(id);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 1, false, false, false));
        player.sendActionBar(Component.text("👻 PHASE – move through blocks for 8s!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f);

        // Every tick: if inside solid block, push player forward
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!player.isOnline() || !phasing.contains(id) || ++ticks > 160) {
                    phasing.remove(id);
                    cancel();
                    return;
                }
                Location loc = player.getLocation();
                // If inside a solid block, teleport forward through it
                if (loc.getBlock().getType().isSolid()) {
                    Vector dir = player.getLocation().getDirection().normalize().multiply(0.5);
                    Location next = loc.clone().add(dir);
                    next.setYaw(loc.getYaw());
                    next.setPitch(loc.getPitch());
                    player.teleport(next);
                    player.getWorld().spawnParticle(Particle.PORTAL, next, 3, 0.1, 0.1, 0.1, 0.05);
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

    // ── Ability 2: Void Blink – teleport 8-12 blocks forward ────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "VoidBlink", (long)(1.5 * MIN))) return;
        startCooldown(player, "VoidBlink", (long)(1.5 * MIN));

        Location eye = player.getEyeLocation();
        Vector dir   = eye.getDirection().normalize();
        World world  = player.getWorld();

        // Find the furthest safe destination up to 12 blocks
        Location dest = eye.clone();
        Location lastSafe = player.getLocation();
        boolean wentThroughBlock = false;

        for (int i = 1; i <= 24; i++) { // 0.5 block steps up to 12 blocks
            dest.add(dir.clone().multiply(0.5));
            if (dest.getBlock().getType().isSolid()) {
                wentThroughBlock = true;
            } else {
                lastSafe = dest.clone();
                lastSafe.setYaw(player.getLocation().getYaw());
                lastSafe.setPitch(player.getLocation().getPitch());
                // Stop at 12 blocks (24 half-steps)
                if (i >= 24) break;
            }
        }

        player.teleport(lastSafe);
        world.spawnParticle(Particle.PORTAL, lastSafe, 30, 0.3, 0.5, 0.3, 0.1);
        world.playSound(lastSafe, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.8f);

        if (wentThroughBlock && lastSafe.getBlock().getType().isSolid()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            player.sendActionBar(Component.text("⚫ Void Blink – clipped block! Slowness II", NamedTextColor.DARK_PURPLE));
        } else {
            player.sendActionBar(Component.text("⚫ VOID BLINK!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        }
    }

    // ── Ultimate: Spectral Realm – 8s full invulnerability + fly ────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "SpectralRealm", 5 * MIN)) return;
        startCooldown(player, "SpectralRealm", 5 * MIN);

        UUID id = player.getUniqueId();
        spectral.add(id);

        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,    160, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,       160, 255, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,            160, 2, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,     160, 0, false, false, false));
        player.setAllowFlight(true);
        player.setFlying(true);

        player.showTitle(Title.title(
                Component.text("✦ SPECTRAL REALM", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text("8s invulnerability – cannot attack!", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(2), Duration.ofMillis(400))));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 2f);

        // Particle aura every 5 ticks
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!spectral.contains(id) || ++t > 32) { cancel(); return; }
                player.getWorld().spawnParticle(Particle.PORTAL,
                        player.getLocation().add(0, 1, 0), 8, 0.4, 0.8, 0.4, 0.1);
            }
        }.runTaskTimer(getPlugin(), 0L, 5L);

        new BukkitRunnable() {
            @Override public void run() {
                spectral.remove(id);
                if (!player.isOnline()) return;
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                player.removePotionEffect(PotionEffectType.RESISTANCE);
                player.removePotionEffect(PotionEffectType.SPEED);
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
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
