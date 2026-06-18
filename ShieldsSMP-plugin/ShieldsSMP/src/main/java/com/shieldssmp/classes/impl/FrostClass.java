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

public class FrostClass extends PlayerClass {

    private final Random rng = new Random();
    /** Players currently frozen by Absolute Zero */
    private final Map<UUID, List<Location>> frozenPlayers = new HashMap<>();
    /** Players with Frost Armor active */
    private final Set<UUID> frostArmorActive = new HashSet<>();

    @Override public String getName()         { return "Frost"; }
    @Override public String getDescription()  { return "Ice cold, colder than the void"; }
    @Override public String getAbility1Name() { return "Glacial Shard"; }
    @Override public String getAbility2Name() { return "Frost Armor"; }
    @Override public String getUltimateName() { return "Absolute Zero"; }
    @Override public String getAbility1CooldownKey() { return "GlacialShard"; }
    @Override public String getAbility2CooldownKey() { return "FrostArmor"; }
    @Override public String getUltimateCooldownKey() { return "AbsoluteZero"; }

    // ── Passive: 1/30 chance to freeze on hit ──────────────────────────────────
    @Override
    public void onDealDamage(Player attacker, LivingEntity victim, double damage) {
        if (rng.nextInt(30) == 0) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 255)); // 2s freeze
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
            if (victim instanceof Player p)
                p.sendActionBar(Component.text("❄ FROZEN for 2s!", NamedTextColor.AQUA, TextDecoration.BOLD));
            attacker.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0,1,0), 10, 0.3, 0.5, 0.3, 0.1);
        }
    }

    // ── Frost Armor: counter-freeze on melee hit ───────────────────────────────
    @Override
    public void onTakeDamage(Player victim, Entity attacker, double damage) {
        if (!frostArmorActive.contains(victim.getUniqueId())) return;
        if (!(attacker instanceof LivingEntity le)) return;
        // Only proc on melee (no projectile)
        if (victim.getLastDamageCause() != null &&
                victim.getLastDamageCause().getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.PROJECTILE) return;
        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 2)); // 2.5s Slowness III
        le.getWorld().spawnParticle(Particle.SNOWFLAKE, le.getLocation().add(0,1,0), 8, 0.3, 0.5, 0.3, 0.1);
        if (attacker instanceof Player ap)
            ap.sendActionBar(Component.text("❄ Frost Armor: Slowness III for 2.5s!", NamedTextColor.AQUA));
    }

    // ── Ability 1: Glacial Shard ──────────────────────────────────────────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "GlacialShard", (long)(1.5 * MIN))) return;
        startCooldown(player, "GlacialShard", (long)(1.5 * MIN));

        Location eye = player.getEyeLocation();
        Vector dir   = eye.getDirection().normalize();
        World world  = player.getWorld();

        player.sendActionBar(Component.text("❄ GLACIAL SHARD fired!", NamedTextColor.AQUA, TextDecoration.BOLD));
        world.playSound(eye, Sound.ENTITY_SNOWBALL_THROW, 1.5f, 0.7f);

        new BukkitRunnable() {
            final Location pos = eye.clone();
            int steps = 0;
            boolean hit = false;
            @Override public void run() {
                if (hit || ++steps > 24) { cancel(); return; } // 12 blocks
                pos.add(dir);
                world.spawnParticle(Particle.SNOWFLAKE, pos, 3, 0.1, 0.1, 0.1, 0.02);
                world.spawnParticle(Particle.DUST, pos, 2, 0.05, 0.05, 0.05,
                        new Particle.DustOptions(Color.fromRGB(173, 216, 230), 1.5f));

                for (Entity e : world.getNearbyEntities(pos, 0.8, 0.8, 0.8)) {
                    if (!(e instanceof Player victim) || victim.equals(player)) continue;
                    hit = true;
                    victim.damage(5, player); // 2.5 hearts
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,  120, 3)); // 6s Slowness IV = 60% slow
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,  40, 0));
                    // Freezing overlay (powder snow effect)
                    victim.setFreezeTicks(100); // freeze ticks
                    victim.sendActionBar(Component.text("❄ Glacial Shard: Freezing for 6s!", NamedTextColor.AQUA, TextDecoration.BOLD));
                    world.spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0,1,0), 20, 0.5, 0.8, 0.5, 0.1);
                    break;
                }
                if (pos.getBlock().getType().isSolid()) { cancel(); }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    // ── Ability 2: Frost Armor ────────────────────────────────────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "FrostArmor", 2 * MIN)) return;
        startCooldown(player, "FrostArmor", 2 * MIN);

        UUID id = player.getUniqueId();
        frostArmorActive.add(id);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 1)); // Resistance II
        player.sendActionBar(Component.text("❄ FROST ARMOR – Resistance II + counter-freeze for 8s!", NamedTextColor.AQUA, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.8f);

        // Ice particle aura
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!frostArmorActive.contains(id) || !player.isOnline() || ++t > 80) { cancel(); return; }
                player.getWorld().spawnParticle(Particle.SNOWFLAKE,
                        player.getLocation().add(0, 1, 0), 4, 0.4, 0.8, 0.4, 0.05);
            }
        }.runTaskTimer(getPlugin(), 0L, 4L);

        new BukkitRunnable() {
            @Override public void run() {
                frostArmorActive.remove(id);
                if (player.isOnline()) {
                    player.removePotionEffect(PotionEffectType.RESISTANCE);
                    player.sendActionBar(Component.text("❄ Frost Armor ended", NamedTextColor.GRAY));
                }
            }
        }.runTaskLater(getPlugin(), 160L);
    }

    // ── Ultimate: Absolute Zero ────────────────────────────────────────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "AbsoluteZero", 5 * MIN)) return;
        startCooldown(player, "AbsoluteZero", 5 * MIN);

        Location center = player.getLocation().clone();
        World world     = player.getWorld();

        // Slam effect
        world.spawnParticle(Particle.SNOWFLAKE, center, 100, 6, 2, 6, 0.2);
        world.playSound(center, Sound.ENTITY_PLAYER_HURT_FREEZE, 2f, 0.5f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.4f);
        player.sendActionBar(Component.text("❄ ABSOLUTE ZERO – flash freeze!", NamedTextColor.AQUA, TextDecoration.BOLD));

        // Encase enemies in ice blocks for 4s, then deal 5 hearts true damage
        List<Player> targets = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(center, 12, 12, 12)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            targets.add(p);
            UUID pid = p.getUniqueId();

            // Place ice blocks around them
            List<Location> iceBlocks = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    for (int dy = 0; dy <= 1; dy++) {
                        Location iLoc = p.getLocation().add(dx, dy, dz);
                        if (iLoc.getBlock().getType().isAir()) {
                            iLoc.getBlock().setType(Material.ICE);
                            iceBlocks.add(iLoc);
                        }
                    }
            frozenPlayers.put(pid, iceBlocks);

            // Freeze them in place
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,  80, 255));
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
            p.setFreezeTicks(80);
            p.sendActionBar(Component.text("❄ FROZEN IN ICE for 4s!", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        }

        // After 4s: shatter ice, deal 5 hearts true damage
        new BukkitRunnable() {
            @Override public void run() {
                for (Player victim : targets) {
                    if (!victim.isOnline()) continue;
                    UUID pid = victim.getUniqueId();
                    List<Location> iceBlocks = frozenPlayers.remove(pid);
                    if (iceBlocks != null)
                        iceBlocks.forEach(l -> { if (l.getBlock().getType() == Material.ICE) l.getBlock().setType(Material.AIR); });

                    victim.removePotionEffect(PotionEffectType.SLOWNESS);
                    victim.setFreezeTicks(0);

                    // 5 hearts true damage
                    victim.setHealth(Math.max(0, victim.getHealth() - 10));
                    world.spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0,1,0), 20, 0.5, 0.5, 0.5, 0.1);
                    world.playSound(victim.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.5f, 0.8f);
                    victim.sendActionBar(Component.text("❄ Ice shattered: 5 hearts true damage!", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
                }
            }
        }.runTaskLater(getPlugin(), 80L);
    }

    @Override
    public void onDeath(Player player) {
        super.onDeath(player);
        UUID id = player.getUniqueId();
        frostArmorActive.remove(id);
        List<Location> iceBlocks = frozenPlayers.remove(id);
        if (iceBlocks != null)
            iceBlocks.forEach(l -> { if (l.getBlock().getType() == Material.ICE) l.getBlock().setType(Material.AIR); });
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
