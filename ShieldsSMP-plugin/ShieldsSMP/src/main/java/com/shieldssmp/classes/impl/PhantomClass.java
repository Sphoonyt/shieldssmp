package com.shieldssmp.classes.impl;

import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class PhantomClass extends PlayerClass {

    private final Set<UUID> phasing    = new HashSet<>();
    private final Set<UUID> spectral   = new HashSet<>();

    @Override public String getName()         { return "Phantom"; }
    @Override public String getDescription()  { return "A shadow that slips between worlds"; }
    @Override public String getAbility1Name() { return "Phase"; }
    @Override public String getAbility2Name() { return "Void Blink"; }
    @Override public String getUltimateName() { return "Spectral Realm"; }

    // ── Passive: Shadow Crouch ─────────────────────────────────────────────────
    @Override
    public void tickPassive(Player player) {
        if (spectral.contains(player.getUniqueId())) return;
        if (player.isSneaking()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30, 0, false, false, false));
        }
    }

    @Override
    public void onBreakBlock(Player player) {
        revealPlayer(player, 40); // reveal 2s
    }

    @Override
    public void onDealDamage(Player attacker, org.bukkit.entity.LivingEntity victim, double damage) {
        revealPlayer(attacker, 40);
    }

    private void revealPlayer(Player player, int ticks) {
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        new BukkitRunnable() {
            @Override public void run() {
                if (player.isOnline()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30, 0, false, false, false));
                }
            }
        }.runTaskLater(getPlugin(), ticks);
    }

    // ── Ability 1: Phase ──────────────────────────────────────────────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "Phase", 2 * MIN)) return;
        startCooldown(player, "Phase", 2 * MIN);

        UUID id = player.getUniqueId();
        phasing.add(id);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 1, false, false, false));
        player.sendActionBar(Component.text("👻 PHASE active for 8s", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f);

        new BukkitRunnable() {
            @Override public void run() {
                if (player.isOnline()) {
                    phasing.remove(id);
                    player.removePotionEffect(PotionEffectType.SPEED);
                    player.sendActionBar(Component.text("👻 Phase ended", NamedTextColor.GRAY));
                } else {
                    phasing.remove(id);
                }
            }
        }.runTaskLater(getPlugin(), 160L);
    }

    public boolean isPhasing(UUID id) { return phasing.contains(id); }

    // ── Ability 2: Void Blink ────────────────────────────────────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "VoidBlink", (long)(1.5 * MIN))) return;
        startCooldown(player, "VoidBlink", (long)(1.5 * MIN));

        Vector dir = player.getLocation().getDirection().normalize();
        Location current = player.getLocation().clone();
        Location dest = null;
        boolean throughBlock = false;

        for (int i = 1; i <= 12; i++) {
            current.add(dir);
            if (!current.getBlock().getType().isAir()) {
                throughBlock = true;
                dest = current.clone();
            }
        }
        if (dest == null) dest = current;

        player.teleport(dest);
        player.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.8f);
        player.getWorld().spawnParticle(Particle.PORTAL, dest, 40, 0.3, 0.5, 0.3, 0.2);

        if (throughBlock && !dest.getBlock().getType().isAir()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            player.sendActionBar(Component.text("⚫ Void Blink – landed inside block! Slowness II", NamedTextColor.DARK_PURPLE));
        } else {
            player.sendActionBar(Component.text("⚫ Void Blink!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        }
    }

    // ── Ultimate: Spectral Realm ──────────────────────────────────────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "SpectralRealm", 5 * MIN)) return;
        startCooldown(player, "SpectralRealm", 5 * MIN);

        UUID id = player.getUniqueId();
        spectral.add(id);

        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,   160, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,      160, 255, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,           160, 1, false, false, false));
        player.setAllowFlight(true);
        player.setFlying(true);

        player.sendActionBar(Component.text("✦ SPECTRAL REALM – 8s invulnerability!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 2f);

        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) { spectral.remove(id); return; }
                spectral.remove(id);
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                player.removePotionEffect(PotionEffectType.RESISTANCE);
                player.removePotionEffect(PotionEffectType.SPEED);
                if (!player.getGameMode().equals(org.bukkit.GameMode.CREATIVE) && !player.getGameMode().equals(org.bukkit.GameMode.SPECTATOR)) {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                }
                player.sendActionBar(Component.text("✦ Spectral Realm ended", NamedTextColor.GRAY));
            }
        }.runTaskLater(getPlugin(), 160L);
    }

    public boolean isSpectral(UUID id) { return spectral.contains(id); }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() {
        return com.shieldssmp.ShieldsSMP.getInstance();
    }
}
