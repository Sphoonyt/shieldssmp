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

public class BloodClass extends PlayerClass {

    /** Players currently under Hemorrhage (bleeding) */
    private final Map<UUID, BukkitRunnable> bleeding = new HashMap<>();
    /** Players in Crimson Frenzy – take 30% more damage */
    private final Set<UUID> frenzied = new HashSet<>();

    @Override public String getName()         { return "Blood"; }
    @Override public String getDescription()  { return "Power through bloodshed and rage"; }
    @Override public String getAbility1Name() { return "Hemorrhage Strike"; }
    @Override public String getAbility2Name() { return "Blood God"; }
    @Override public String getUltimateName() { return "Crimson Frenzy"; }
    @Override public String getAbility1CooldownKey() { return "Hemorrhage"; }
    @Override public String getAbility2CooldownKey() { return "BloodGod"; }
    @Override public String getUltimateCooldownKey() { return "CrimsonFrenzy"; }

    // ── Passive: Bloodlust – kill grants Strength III + red glow for 10s ──────
    @Override
    public void onKill(Player killer, LivingEntity killed) {
        killer.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 2));
        killer.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,  200, 0, false, false, false));
        killer.sendActionBar(Component.text("🩸 BLOODLUST! Strength III for 10s!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
    }

    // ── Crimson Frenzy: take 30% more damage while active ─────────────────────
    @Override
    public void onTakeDamage(Player victim, Entity attacker, double damage) {
        // handled in GlobalListener via isFrenzied()
    }

    public boolean isFrenzied(UUID id) { return frenzied.contains(id); }

    // ── Ability 1: Hemorrhage Strike ──────────────────────────────────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "Hemorrhage", (long)(1.5 * MIN))) return;
        startCooldown(player, "Hemorrhage", (long)(1.5 * MIN));

        Location loc = player.getLocation();
        World world  = player.getWorld();

        world.spawnParticle(Particle.DUST, loc.clone().add(0,1,0), 40, 5, 1, 5,
                new Particle.DustOptions(Color.RED, 2f));
        world.playSound(loc, Sound.ENTITY_PLAYER_HURT, 1.5f, 0.6f);

        var trust1 = ShieldsSMP.getInstance().getTrustSystem();
        for (Entity e : world.getNearbyEntities(loc, 10, 10, 10)) {
            if (!(e instanceof Player victim) || victim.equals(player)) continue;
            if (trust1.isTrusted(player.getUniqueId(), victim.getUniqueId())) continue; // skip trusted
            startBleeding(victim, player);
        }
        player.sendActionBar(Component.text("🩸 Hemorrhage Strike – enemies are bleeding!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
    }

    private void startBleeding(Player victim, Player source) {
        UUID vid = victim.getUniqueId();
        BukkitRunnable old = bleeding.remove(vid);
        if (old != null) old.cancel();

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                ticks += 40;
                if (!victim.isOnline() || ticks > 160) { bleeding.remove(vid); cancel(); return; }
                victim.damage(2, source); // 1 heart every 2s
                victim.getWorld().spawnParticle(Particle.DUST,
                        victim.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3,
                        new Particle.DustOptions(Color.RED, 1.5f));
                victim.sendActionBar(Component.text("🩸 Bleeding! " + (4 - ticks / 40) + " pulses left", NamedTextColor.DARK_RED));
            }
        };
        task.runTaskTimer(getPlugin(), 40L, 40L);
        bleeding.put(vid, task);
    }

    // ── Ability 2: Blood God ──────────────────────────────────────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "BloodGod", 3 * MIN)) return;
        startCooldown(player, "BloodGod", 3 * MIN);

        Location loc = player.getLocation();
        World world  = player.getWorld();

        // Ground eruption particles
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Location ground = loc.clone().add(x, 0, z);
                world.spawnParticle(Particle.DUST, ground, 4, 0.2, 0.5, 0.2,
                        new Particle.DustOptions(Color.RED, 2f));
                world.spawnParticle(Particle.LARGE_SMOKE, ground, 2, 0.1, 0.3, 0.1);
            }
        }
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);

        var trust2 = ShieldsSMP.getInstance().getTrustSystem();
        for (Entity e : world.getNearbyEntities(loc, 5, 3, 5)) {
            if (!(e instanceof Player victim) || victim.equals(player)) continue;
            if (trust2.isTrusted(player.getUniqueId(), victim.getUniqueId())) continue; // skip trusted
            victim.damage(8, player); // 4 hearts
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 160, 1));
            Vector kb = victim.getLocation().toVector().subtract(loc.toVector());
            if (kb.lengthSquared() < 0.01) kb = new Vector(1, 0, 0);
            kb.normalize().multiply(3.5).setY(0.8);
            victim.setVelocity(kb);
            victim.sendActionBar(Component.text("🩸 Blood God: knocked back!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        }
        player.sendActionBar(Component.text("🩸 BLOOD GOD – ground eruption!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
    }

    // ── Ultimate: Crimson Frenzy ───────────────────────────────────────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "CrimsonFrenzy", 5 * MIN)) return;
        startCooldown(player, "CrimsonFrenzy", 5 * MIN);

        UUID id = player.getUniqueId();
        frenzied.add(id);

        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,   240, 1)); // 50% attack speed
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 240, 1)); // Strength II
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,  240, 0, false, true, true));
        // Red glowing via dust particle aura
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!frenzied.contains(id) || !player.isOnline() || ++t > 240) { cancel(); return; }
                player.getWorld().spawnParticle(Particle.DUST,
                        player.getLocation().add(0, 1, 0), 6, 0.4, 0.8, 0.4,
                        new Particle.DustOptions(Color.RED, 1.5f));
            }
        }.runTaskTimer(getPlugin(), 0L, 4L);

        player.sendActionBar(Component.text("🩸 CRIMSON FRENZY! +Strength II +Haste but -30% defense!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1f);

        new BukkitRunnable() {
            @Override public void run() {
                frenzied.remove(id);
                if (!player.isOnline()) return;
                player.removePotionEffect(PotionEffectType.HASTE);
                player.removePotionEffect(PotionEffectType.STRENGTH);
                player.removePotionEffect(PotionEffectType.GLOWING);
                player.sendActionBar(Component.text("🩸 Crimson Frenzy ended", NamedTextColor.GRAY));
            }
        }.runTaskLater(getPlugin(), 240L);
    }

    @Override
    public void onDeath(Player player) {
        super.onDeath(player);
        UUID id = player.getUniqueId();
        frenzied.remove(id);
        BukkitRunnable b = bleeding.remove(id);
        if (b != null) b.cancel();
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
