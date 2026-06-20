package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class SpeedDemonClass extends PlayerClass {

    /** Players currently in Speedstorm (for lightning tracking) */
    private final Set<UUID> speedstormActive = new HashSet<>();

    @Override public String getName()         { return "Speed Demon"; }
    @Override public String getDescription()  { return "Too fast to catch, too quick to stop"; }
    @Override public String getAbility1Name() { return "Mach Dash"; }
    @Override public String getAbility2Name() { return "Need for Speed"; }
    @Override public String getUltimateName() { return "Speedstorm"; }
    @Override public String getAbility1CooldownKey() { return "MachDash"; }
    @Override public String getAbility2CooldownKey() { return "NeedForSpeed"; }
    @Override public String getUltimateCooldownKey() { return "Speedstorm"; }

    // ── Passive: Frequency Flux – allies in 5-block radius get 30% faster CDs ─
    // We track nearby allies and give them Haste as a proxy buff
    @Override
    public void tickPassive(Player player) {
        var trust = ShieldsSMP.getInstance().getTrustSystem();
        for (Entity e : player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            if (!trust.isTrusted(player.getUniqueId(), p.getUniqueId())) continue; // only trusted allies
            p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 30, 0, false, false, false));
        }
    }

    // ── Ability 1: Mach Dash ──────────────────────────────────────────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "MachDash", 30 * SEC)) return;
        startCooldown(player, "MachDash", 30 * SEC);

        Location start = player.getLocation().clone();
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        Location dest = start.clone();
        Location safe = start.clone();

        // Find landing spot 15 blocks forward (stop at solid block)
        for (int i = 0; i < 30; i++) {
            dest.add(dir.clone().multiply(0.5));
            if (!dest.getBlock().getType().isSolid() && !dest.clone().subtract(0,1,0).getBlock().getType().isAir()) {
                safe = dest.clone();
                safe.setYaw(player.getLocation().getYaw());
                safe.setPitch(player.getLocation().getPitch());
            }
        }

        // Trail particles
        Location trailLoc = start.clone();
        World world = player.getWorld();
        for (int i = 0; i < 20; i++) {
            trailLoc.add(dir.clone().multiply(0.75));
            world.spawnParticle(Particle.CLOUD, trailLoc, 2, 0.1, 0.1, 0.1, 0.05);
        }

        player.teleport(safe);
        world.playSound(safe, Sound.ENTITY_BREEZE_SHOOT, 1.5f, 1.5f);

        // Kinetic shockwave at landing: 3×3, 3 hearts, knockback (skip trusted allies)
        var trust1 = ShieldsSMP.getInstance().getTrustSystem();
        for (Entity e : world.getNearbyEntities(safe, 3, 2, 3)) {
            if (!(e instanceof Player victim) || victim.equals(player)) continue;
            if (trust1.isTrusted(player.getUniqueId(), victim.getUniqueId())) continue; // skip trusted
            victim.damage(6, player); // 3 hearts
            Vector kb = victim.getLocation().toVector().subtract(safe.toVector());
            if (kb.lengthSquared() < 0.01) kb = new Vector(1, 0, 0);
            victim.setVelocity(kb.normalize().multiply(3).setY(0.5));
            victim.sendActionBar(Component.text("💨 Mach Dash shockwave!", NamedTextColor.YELLOW));
        }
        world.spawnParticle(Particle.CLOUD, safe, 20, 1, 0.5, 1, 0.1);
        player.sendActionBar(Component.text("💨 MACH DASH!", NamedTextColor.YELLOW, TextDecoration.BOLD));
    }

    // ── Ability 2: Need for Speed ─────────────────────────────────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "NeedForSpeed", 2 * MIN)) return;
        startCooldown(player, "NeedForSpeed", 2 * MIN);

        Location loc = player.getLocation();
        var trust2 = ShieldsSMP.getInstance().getTrustSystem();
        // Buff self + TRUSTED allies in 3-block radius
        int buffed = 0;
        for (Entity e : player.getWorld().getNearbyEntities(loc, 3, 3, 3)) {
            if (!(e instanceof Player p)) continue;
            if (!p.equals(player) && !trust2.isTrusted(player.getUniqueId(), p.getUniqueId())) continue;
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,      120, 4)); // Speed V
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 120, 4)); // Jump V
            p.sendActionBar(Component.text("💨 Need for Speed: Speed V + Jump V!", NamedTextColor.YELLOW, TextDecoration.BOLD));
            buffed++;
        }
        // Always buff self too
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,      120, 4));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 120, 4));

        player.getWorld().playSound(loc, Sound.ENTITY_BREEZE_SHOOT, 1f, 2f);
        player.sendActionBar(Component.text("💨 Need for Speed – buffed " + buffed + " allies!", NamedTextColor.YELLOW, TextDecoration.BOLD));
    }

    // ── Ultimate: Speedstorm ──────────────────────────────────────────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "Speedstorm", 3 * MIN)) return;
        startCooldown(player, "Speedstorm", 3 * MIN);

        UUID id = player.getUniqueId();
        speedstormActive.add(id);

        // Give player Speed V for the duration
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 4));
        player.sendActionBar(Component.text("⚡ SPEEDSTORM – 10s storm follows you!", NamedTextColor.YELLOW, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 1f);

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!player.isOnline() || ++t > 100) {
                    speedstormActive.remove(id);
                    if (player.isOnline()) { player.removePotionEffect(PotionEffectType.SPEED); player.sendActionBar(Component.text("⚡ Speedstorm ended", NamedTextColor.GRAY)); }
                    cancel(); return;
                }

                Location loc = player.getLocation();
                World world  = player.getWorld();

                // Dark clouds visual
                world.spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0, 5, 0), 8, 4, 0.5, 4, 0.02);

                // Strike enemies every 40 ticks (2s)
                if (t % 20 == 0) {
                    for (Entity e : world.getNearbyEntities(loc, 8, 8, 8)) {
                        if (!(e instanceof Player victim) || victim.equals(player)) continue;
                        // Lightning visual + 1 heart damage
                        world.strikeLightningEffect(victim.getLocation());
                        victim.damage(2, player); // 1 heart
                        victim.sendActionBar(Component.text("⚡ Speedstorm lightning: 1 heart!", NamedTextColor.YELLOW));
                    }
                    world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.8f);
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 2L);
    }

    @Override
    public void onDeath(Player player) {
        super.onDeath(player);
        speedstormActive.remove(player.getUniqueId());
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
