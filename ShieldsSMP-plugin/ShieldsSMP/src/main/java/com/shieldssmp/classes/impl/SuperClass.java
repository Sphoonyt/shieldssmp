package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;

public class SuperClass extends PlayerClass {

    // Stamina measured in ticks; 8 s = 160 ticks at full
    private static final int MAX_STAMINA = 160;
    private final Map<UUID, Integer> flightStamina = new HashMap<>();
    private final Map<UUID, BossBar> staminaBars   = new HashMap<>();
    private final Set<UUID> laserActive             = new HashSet<>();
    private final Set<UUID> novaActive              = new HashSet<>();
    private final Set<UUID> novaFallImmune          = new HashSet<>();

    @Override public String getName()         { return "Super"; }
    @Override public String getDescription()  { return "Faster than a creeper, stronger than a golem"; }
    @Override public String getAbility1Name() { return "Laser Eyes"; }
    @Override public String getAbility2Name() { return "Heavy Punch"; }
    @Override public String getUltimateName() { return "Supernova"; }
    @Override public String getAbility1CooldownKey() { return "LaserEyes"; }
    @Override public String getAbility2CooldownKey() { return "HeavyPunch"; }
    @Override public String getUltimateCooldownKey() { return "Supernova"; }

    @Override
    public void onEquip(Player player) {
        UUID id = player.getUniqueId();
        flightStamina.put(id, MAX_STAMINA);
        BossBar bar = Bukkit.createBossBar("☀ Flight Stamina", BarColor.YELLOW, BarStyle.SEGMENTED_10);
        bar.addPlayer(player);
        staminaBars.put(id, bar);
    }

    @Override
    public void onUnequip(Player player) {
        UUID id = player.getUniqueId();
        laserActive.remove(id); novaActive.remove(id); novaFallImmune.remove(id);
        BossBar bar = staminaBars.remove(id);
        if (bar != null) bar.removeAll();
        flightStamina.remove(id);
        if (player.isGliding()) player.setGliding(false);
        if (!player.getGameMode().equals(GameMode.CREATIVE)) player.setAllowFlight(false);
    }

    @Override
    public void onDeath(Player player) {
        super.onDeath(player);
        UUID id = player.getUniqueId();
        laserActive.remove(id); novaActive.remove(id); novaFallImmune.remove(id);
        flightStamina.put(id, MAX_STAMINA);
        BossBar bar = staminaBars.get(id);
        if (bar != null) bar.setProgress(1.0);
    }

    // ── Passive: Solar Flight – true elytra-physics jetpack with firework thrust ──
    // While airborne, the player automatically gets elytra gliding physics
    // (via Player#setGliding – no actual elytra item needed). Holding SNEAK while
    // airborne fires the "jetpack": forward+upward thrust with firework particles
    // and rocket sound, draining stamina. Released sneak = pure glide (no drain),
    // exactly like coasting in an elytra. Total thrust duration ≈ one elytra
    // firework boost, but spread out slower.
    @Override
    public void tickPassive(Player player) {
        UUID id = player.getUniqueId();
        int stamina = flightStamina.getOrDefault(id, MAX_STAMINA);
        BossBar bar = staminaBars.get(id);

        if (novaActive.contains(id)) {
            if (bar != null) bar.setProgress((double) stamina / MAX_STAMINA);
            return; // Supernova handles its own flight state
        }

        if (player.isOnGround()) {
            if (player.isGliding()) player.setGliding(false);
            if (stamina < MAX_STAMINA) {
                stamina = Math.min(MAX_STAMINA, stamina + 1);
                flightStamina.put(id, stamina);
            }
        } else {
            // Airborne: always glide (elytra physics) as long as we have ANY stamina left
            if (stamina > 0) {
                if (!player.isGliding()) player.setGliding(true);

                if (player.isSneaking()) {
                    // Jetpack thrust: forward + upward boost
                    Vector dir = player.getLocation().getDirection().normalize();
                    Vector thrust = dir.multiply(0.45);
                    thrust.setY(Math.max(thrust.getY(), 0.18));
                    player.setVelocity(player.getVelocity().add(thrust));

                    // Firework particle trail behind the player
                    Vector behind = dir.clone().multiply(-1);
                    Location trailLoc = player.getLocation().clone().add(behind.getX() * 0.6, -0.3, behind.getZ() * 0.6);
                    player.getWorld().spawnParticle(Particle.FIREWORK, trailLoc, 4, 0.15, 0.15, 0.15, 0.02);
                    player.getWorld().spawnParticle(Particle.SMOKE, trailLoc, 2, 0.1, 0.1, 0.1, 0.01);

                    // Rocket sound every 5 ticks (not every tick, avoids spam)
                    if (stamina % 5 == 0)
                        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.6f, 1.4f);

                    stamina = Math.max(0, stamina - 2); // drain 2 ticks per jetpack-tick
                    flightStamina.put(id, stamina);
                    if (stamina == 0) {
                        player.sendActionBar(Component.text("☀ Jetpack fuel depleted! Gliding only.", NamedTextColor.YELLOW));
                    }
                }
                // else: pure coasting glide, no stamina drain (just like elytra without fireworks)
            } else {
                if (player.isGliding()) player.setGliding(false); // no fuel left, falls normally
            }
        }

        if (bar != null) bar.setProgress((double) stamina / MAX_STAMINA);
    }

    // ── Ability 1: Laser Eyes – true damage beam ──────────────────────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "LaserEyes", 2 * MIN)) return;
        startCooldown(player, "LaserEyes", 2 * MIN);
        UUID id = player.getUniqueId();
        laserActive.add(id);
        player.sendActionBar(Component.text("👁 LASER EYES – 6s!", NamedTextColor.RED, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.5f);

        new BukkitRunnable() {
            int ticks = 0;
            final Set<UUID> hitThisSec = new HashSet<>();
            @Override public void run() {
                if (!player.isOnline() || !laserActive.contains(id)) { cancel(); return; }
                if (++ticks > 120) { laserActive.remove(id); cancel(); return; }
                if (ticks % 20 == 0) hitThisSec.clear();

                Location eye = player.getEyeLocation();
                Vector dir = eye.getDirection().normalize();
                Location pos = eye.clone();

                for (int step = 0; step < 60; step++) {
                    pos.add(dir);
                    if (step % 3 == 0) player.getWorld().spawnParticle(Particle.FLAME, pos, 1, 0.05, 0.05, 0.05, 0);
                    if (step % 6 == 0) player.getWorld().spawnParticle(Particle.CRIT, pos, 1, 0.05, 0.05, 0.05, 0);

                    if (ticks % 20 == 0) {
                        for (Entity e : player.getWorld().getNearbyEntities(pos, 0.7, 0.7, 0.7)) {
                            if (!(e instanceof LivingEntity le) || e.equals(player)) continue;
                            if (e instanceof Player p && hitThisSec.contains(p.getUniqueId())) continue;
                            if (e instanceof Player p) hitThisSec.add(p.getUniqueId());
                            le.setHealth(Math.max(0, le.getHealth() - 4)); // 2 hearts true
                            le.setFireTicks(40);
                            if (le instanceof Player p2) p2.sendActionBar(Component.text("👁 Laser: 2 hearts true damage!", NamedTextColor.RED));
                            break;
                        }
                    }
                    if (pos.getBlock().getType().isSolid()) break;
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    // ── Ability 2: Heavy Punch – instant 5-heart strike + knockback ───────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "HeavyPunch", (long)(1.5 * MIN))) return;
        Player target = null; double best = Double.MAX_VALUE;
        for (Entity e : player.getWorld().getNearbyEntities(player.getLocation(), 5, 5, 5)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            double d = player.getLocation().distanceSquared(p.getLocation());
            if (d < best) { best = d; target = p; }
        }
        if (target == null) { player.sendActionBar(Component.text("💪 No player in melee range!", NamedTextColor.RED)); return; }
        startCooldown(player, "HeavyPunch", (long)(1.5 * MIN));

        target.setHealth(Math.max(0, target.getHealth() - 10)); // 5 hearts true
        Vector kb = player.getLocation().getDirection().normalize().multiply(4.0);
        kb.setY(1.2);
        target.setVelocity(kb);
        player.sendActionBar(Component.text("💪 HEAVY PUNCH! 5 hearts!", NamedTextColor.GOLD, TextDecoration.BOLD));
        target.sendActionBar(Component.text("💢 HEAVY PUNCHED!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 2f, 0.7f);
        player.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0,1,0), 25, 0.5, 0.5, 0.5, 0.3);
    }

    // ── Ultimate: Supernova – fly up, crash down, no fall damage ─────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "Supernova", 5 * MIN)) return;
        startCooldown(player, "Supernova", 5 * MIN);
        UUID id = player.getUniqueId();
        novaActive.add(id);
        novaFallImmune.add(id); // immune to fall damage for entire nova

        player.setVelocity(new Vector(0, 4.0, 0));
        player.setAllowFlight(true); player.setFlying(true);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.5f);
        player.showTitle(Title.title(
                Component.text("☀ SUPERNOVA", NamedTextColor.YELLOW, TextDecoration.BOLD),
                Component.text("Sneak to slam down! (Auto 3s)", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(4), Duration.ofMillis(300))));

        new BukkitRunnable() {
            int ticks = 0; boolean slamming = false;
            @Override public void run() {
                if (!player.isOnline() || !novaActive.contains(id)) { cancel(); return; }
                ticks++;
                if (!slamming) player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 3, 0.2, 0.2, 0.2, 0.05);

                if (!slamming && (player.isSneaking() || ticks >= 60)) {
                    slamming = true;
                    player.setVelocity(new Vector(0, -6, 0));
                    if (!player.getGameMode().equals(GameMode.CREATIVE)) { player.setFlying(false); player.setAllowFlight(false); }
                    player.sendActionBar(Component.text("☀ SLAMMING DOWN!", NamedTextColor.RED, TextDecoration.BOLD));
                }

                if (slamming && player.isOnGround()) {
                    novaActive.remove(id);
                    cancel();
                    triggerImpact(player);
                    // Remove fall immunity 1 tick after landing
                    new BukkitRunnable() { @Override public void run() { novaFallImmune.remove(id); } }.runTaskLater(getPlugin(), 2L);
                }
                if (ticks > 300) { novaActive.remove(id); novaFallImmune.remove(id); cancel(); }
            }
        }.runTaskTimer(getPlugin(), 5L, 1L);
    }

    private void triggerImpact(Player player) {
        Location loc = player.getLocation(); World world = player.getWorld();
        double radius = 15;
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 10, 3, 0, 3);
        world.spawnParticle(Particle.FLAME, loc, 200, 6, 2, 6, 0.15);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 4f, 0.5f);
        world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.6f);

        new BukkitRunnable() {
            double r = 0;
            @Override public void run() {
                r += 1.5;
                for (int i = 0; i < 36; i++) {
                    double a = (2 * Math.PI / 36) * i;
                    world.spawnParticle(Particle.FLAME, loc.getX() + r * Math.cos(a), loc.getY() + 0.1, loc.getZ() + r * Math.sin(a), 1, 0, 0, 0, 0.06);
                }
                if (r >= radius) cancel();
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);

        for (Entity e : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            p.setHealth(Math.max(0, p.getHealth() - 12));
            p.setFireTicks(200);
            Vector kb = p.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(3.0); kb.setY(0.8);
            p.setVelocity(kb);
            p.sendActionBar(Component.text("☀ SUPERNOVA: 6 hearts + fire!", NamedTextColor.RED, TextDecoration.BOLD));
        }
        player.sendActionBar(Component.text("☀ SUPERNOVA IMPACT!", NamedTextColor.GOLD, TextDecoration.BOLD));
    }

    /** EntityDamageEvent handler called from GlobalListener */
    public boolean cancelFallDamage(UUID id) { return novaFallImmune.contains(id); }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
