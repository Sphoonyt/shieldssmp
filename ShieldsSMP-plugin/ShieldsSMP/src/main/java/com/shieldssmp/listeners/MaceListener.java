package com.shieldssmp.listeners;

import com.shieldssmp.ShieldsSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;

public final class MaceListener implements Listener {

    // ── Plugin reference ───────────────────────────────────────────────────────
    private final ShieldsSMP plugin;

    // ── Configurable values (loaded via reloadSettings) ───────────────────────
    private double dashVelocity;
    private long   dashCooldownMs;

    private double windburstRadius;
    private double windburstForce;
    private long   windburstCooldownMs;
    private int    windburstMinAirTicks;

    private int    comboHitsRequired;
    private long   comboResetMs;
    private double launchVelocity;

    private double shockwaveRadius;
    private double shockwaveKnockback;
    private double armorDamageFraction;
    private boolean allowArmorBreak;

    // ── State maps ─────────────────────────────────────────────────────────────

    /** UUID → epoch-ms of last dash use */
    private final Map<UUID, Long> dashCooldowns      = new HashMap<>();
    /** UUID → epoch-ms of last windburst trigger */
    private final Map<UUID, Long> windburstCooldowns = new HashMap<>();

    /**
     * Per-attacker combo state.
     * Resets if: different target hit, or time since last hit > comboResetMs, or on 3rd hit.
     */
    private record ComboEntry(UUID targetId, int hits, long lastHitMs) {}
    private final Map<UUID, ComboEntry> combos = new HashMap<>();

    /** Players currently airborne from the 3-hit launch – awaiting shockwave on land */
    private final Set<UUID> launchedPlayers = new HashSet<>();

    /** Ticks each player has been continuously airborne */
    private final Map<UUID, Integer> airTicks = new HashMap<>();

    // ──────────────────────────────────────────────────────────────────────────

    public MaceListener(ShieldsSMP plugin) {
        this.plugin = plugin;
        reloadSettings();
        startGroundTracker();
    }

    /** Pull fresh values from config (also called by /macereload) */
    public void reloadSettings() {
        var cfg = plugin.getConfig();

        dashVelocity        = cfg.getDouble("mace.dash.velocity",          1.6);
        dashCooldownMs      = cfg.getLong  ("mace.dash.cooldown",          5) * 1000L;

        windburstRadius     = cfg.getDouble("mace.windburst.radius",       6.0);
        windburstForce      = cfg.getDouble("mace.windburst.force",        2.8);
        windburstCooldownMs = cfg.getLong  ("mace.windburst.cooldown",     8) * 1000L;
        windburstMinAirTicks= cfg.getInt   ("mace.windburst.min-airborne-ticks", 10);

        comboHitsRequired   = cfg.getInt   ("mace.combo.hits-required",    3);
        comboResetMs        = cfg.getLong  ("mace.combo.reset-seconds",    5) * 1000L;
        launchVelocity      = cfg.getDouble("mace.combo.launch-velocity",  3.2);

        shockwaveRadius     = cfg.getDouble("mace.shockwave.radius",       10.0);
        shockwaveKnockback  = cfg.getDouble("mace.shockwave.knockback",    2.0);
        armorDamageFraction = cfg.getDouble("mace.shockwave.armor-damage-fraction", 0.5);
        allowArmorBreak     = cfg.getBoolean("mace.shockwave.allow-break", false);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUND TRACKER  (runs every 2 ticks)
    //  Handles: airborne counter, windburst on land, shockwave for launched players
    // ══════════════════════════════════════════════════════════════════════════

    private void startGroundTracker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID id = player.getUniqueId();
                    boolean onGround = player.isOnGround();

                    if (onGround) {
                        int accumulated = airTicks.getOrDefault(id, 0);

                        if (accumulated > 0) {
                            // ── Player just touched down ───────────────────────

                            // 1) Shockwave if they were launched
                            if (launchedPlayers.remove(id)) {
                                triggerShockwave(player.getLocation(), player);
                            }

                            // 2) Windburst if holding mace + minimum air-time met
                            if (accumulated >= windburstMinAirTicks) {
                                ItemStack held = player.getInventory().getItemInMainHand();
                                if (isMace(held) && !onCooldown(windburstCooldowns, id, windburstCooldownMs)) {
                                    triggerWindburst(player);
                                    windburstCooldowns.put(id, System.currentTimeMillis());
                                }
                            }
                        }

                        airTicks.put(id, 0);
                    } else {
                        // Accumulate airborne ticks (2 per scheduler cycle)
                        airTicks.merge(id, 2, Integer::sum);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ABILITY 1 – DASH  (Right-Click with Mace)
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isMace(item)) return;

        event.setCancelled(true);

        UUID id = player.getUniqueId();

        // Cooldown check
        if (onCooldown(dashCooldowns, id, dashCooldownMs)) {
            long sec = remainingSeconds(dashCooldowns, id, dashCooldownMs);
            player.sendActionBar(Component.text("⏳ Dash ready in " + sec + "s", NamedTextColor.RED));
            return;
        }

        // Apply velocity in the look direction with a slight upward kick
        Vector dir = player.getLocation().getDirection().normalize().multiply(dashVelocity);
        dir.setY(Math.max(dir.getY(), 0.25));
        player.setVelocity(dir);

        // FX
        spawnParticleCircle(player.getLocation(), Particle.GUST, 16, 0.6);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.3f);
        player.sendActionBar(Component.text("▶▶ DASH!", NamedTextColor.AQUA, TextDecoration.BOLD));

        dashCooldowns.put(id, System.currentTimeMillis());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ABILITY 2 – WINDBURST  (auto-triggers on landing)
    //  Triggered from the ground tracker above
    // ══════════════════════════════════════════════════════════════════════════

    private void triggerWindburst(Player player) {
        Location loc = player.getLocation();
        World world   = player.getWorld();

        // Particle ring at feet
        spawnParticleCircle(loc, Particle.GUST, 40, windburstRadius * 0.6);
        world.spawnParticle(Particle.GUST, loc.clone().add(0, 0.5, 0), 20,
                windburstRadius * 0.4, 0.5, windburstRadius * 0.4, 0);
        world.playSound(loc, Sound.ENTITY_BREEZE_WIND_BURST, 1.5f, 0.9f);

        player.sendActionBar(Component.text("💨 WINDBURST!", NamedTextColor.AQUA, TextDecoration.BOLD));

        // Push all nearby living entities outward
        for (Entity nearby : world.getNearbyEntities(loc, windburstRadius, windburstRadius * 0.75, windburstRadius)) {
            if (nearby.equals(player)) continue;
            if (!(nearby instanceof LivingEntity)) continue;

            Vector push = nearby.getLocation().toVector().subtract(loc.toVector());
            if (push.lengthSquared() < 0.01) push = new Vector(Math.random() - 0.5, 0.1, Math.random() - 0.5);
            push = push.normalize().multiply(windburstForce);
            push.setY(push.getY() + 0.55); // slight upward arc
            nearby.setVelocity(push);

            if (nearby instanceof Player nearPlayer) {
                nearPlayer.sendActionBar(Component.text("💨 Hit by Windburst!", NamedTextColor.GRAY));
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ABILITY 3 – 3-HIT COMBO → LAUNCH + SHOCKWAVE
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMaceHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (!isMace(item)) return;

        UUID attackerId = attacker.getUniqueId();
        UUID targetId   = target.getUniqueId();
        long now        = System.currentTimeMillis();

        // Determine current combo hits
        ComboEntry prev = combos.get(attackerId);
        int hits;
        if (prev == null
                || !prev.targetId().equals(targetId)
                || (now - prev.lastHitMs()) > comboResetMs) {
            hits = 1; // fresh combo
        } else {
            hits = prev.hits() + 1;
        }

        // Show combo HUD
        String bar = "▮".repeat(hits) + "▯".repeat(Math.max(0, comboHitsRequired - hits));
        NamedTextColor barColor = hits >= comboHitsRequired ? NamedTextColor.RED : NamedTextColor.GOLD;
        attacker.sendActionBar(
                Component.text("⚔ Combo  ", NamedTextColor.WHITE)
                         .append(Component.text(bar, barColor, TextDecoration.BOLD))
                         .append(Component.text("  " + hits + "/" + comboHitsRequired, NamedTextColor.GRAY)));

        // Hit sparks
        target.getWorld().spawnParticle(Particle.CRIT,
                target.getLocation().add(0, 1, 0), 12, 0.3, 0.3, 0.3, 0.1);

        if (hits >= comboHitsRequired) {
            combos.remove(attackerId);
            launchEntity(attacker, target);
        } else {
            combos.put(attackerId, new ComboEntry(targetId, hits, now));
        }
    }

    // ── Launch logic ──────────────────────────────────────────────────────────

    private void launchEntity(Player attacker, LivingEntity target) {
        Location loc  = target.getLocation();
        World    world = target.getWorld();

        // Straight-up launch velocity (~50 blocks with launchVelocity 3.2)
        target.setVelocity(new Vector(0, launchVelocity, 0));

        // FX burst at launch site
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 2, 0.4, 0, 0.4);
        world.spawnParticle(Particle.CRIT, loc.clone().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.3);
        world.playSound(loc, Sound.ENTITY_BREEZE_WIND_BURST, 2f, 0.55f);

        // Attacker feedback
        attacker.sendActionBar(
                Component.text("⚡ LAUNCH! ⚡", NamedTextColor.RED, TextDecoration.BOLD));
        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);

        if (target instanceof Player targetPlayer) {
            // Player: detected by ground tracker
            launchedPlayers.add(targetPlayer.getUniqueId());
            targetPlayer.showTitle(Title.title(
                    Component.text("⬆ LAUNCHED!", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("Brace for impact…", NamedTextColor.GRAY),
                    Title.Times.times(
                            Duration.ofMillis(100),
                            Duration.ofSeconds(2),
                            Duration.ofMillis(400))));
        } else {
            // Non-player entity: poll until it lands
            trackMobLanding(target, attacker);
        }
    }

    /** Poll a mob's position until it touches ground, then trigger shockwave */
    private void trackMobLanding(LivingEntity entity, Player attacker) {
        new BukkitRunnable() {
            boolean peaked = false;
            int     ticks  = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks > 400 || entity.isDead() || !entity.isValid()) { cancel(); return; }

                if (entity.getVelocity().getY() < 0) peaked = true;

                if (peaked && entity.isOnGround()) {
                    triggerShockwave(entity.getLocation(), null);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 5L, 1L);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SHOCKWAVE  (landing impact from 3-hit launch)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * @param loc     impact location
     * @param lander  the entity that landed (excluded from armor damage if non-player); may be null
     */
    private void triggerShockwave(Location loc, Entity lander) {
        World world = loc.getWorld();
        if (world == null) return;

        // ── Visual & audio ────────────────────────────────────────────────────
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 4, 1.5, 0, 1.5);
        world.spawnParticle(Particle.GUST, loc.clone().add(0, 0.5, 0),
                80, shockwaveRadius * 0.5, 1.5, shockwaveRadius * 0.5, 0);
        world.spawnParticle(Particle.CRIT, loc.clone().add(0, 0.3, 0),
                30, shockwaveRadius * 0.3, 0.3, shockwaveRadius * 0.3, 0.2);

        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE,    1.5f, 0.65f);
        world.playSound(loc, Sound.ENTITY_BREEZE_WIND_BURST,  1.0f, 0.45f);
        world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM,  0.8f, 0.9f);

        // Expanding ring effect
        new BukkitRunnable() {
            double r = 0;
            @Override public void run() {
                r += 1.2;
                spawnParticleRing(loc, Particle.GUST, (int)(r * 3), r);
                if (r >= shockwaveRadius) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // ── Affect nearby players ─────────────────────────────────────────────
        double r = shockwaveRadius;
        for (Entity entity : world.getNearbyEntities(loc, r, r / 2.0, r)) {
            if (!(entity instanceof Player victim)) continue;
            if (entity.equals(lander)) continue; // don't hit the lander themselves

            // Armor durability damage
            damageAllArmor(victim);

            // Radial knockback
            Vector push = victim.getLocation().toVector().subtract(loc.toVector());
            if (push.lengthSquared() < 0.01) push = new Vector(1, 0, 0);
            push = push.normalize().multiply(shockwaveKnockback);
            push.setY(Math.max(push.getY(), 0.35));
            victim.setVelocity(push);

            victim.showTitle(Title.title(
                    Component.text("⚠ SHOCKWAVE", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                    Component.text("Armor damaged!", NamedTextColor.RED),
                    Title.Times.times(
                            Duration.ofMillis(60),
                            Duration.ofMillis(1500),
                            Duration.ofMillis(400))));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ARMOR DAMAGE
    //  Removes (maxDurability * armorDamageFraction) from every worn armor piece
    // ══════════════════════════════════════════════════════════════════════════

    private void damageAllArmor(Player player) {
        ItemStack[] armor   = player.getInventory().getArmorContents();
        boolean    anyHit   = false;

        for (ItemStack piece : armor) {
            if (piece == null || piece.getType().isAir()) continue;
            if (!(piece.getItemMeta() instanceof Damageable dmg)) continue;

            short maxDur = piece.getType().getMaxDurability();
            if (maxDur <= 0) continue;

            int addDamage = (int) Math.ceil(maxDur * armorDamageFraction);
            int newDamage = dmg.getDamage() + addDamage;

            if (!allowArmorBreak) {
                newDamage = Math.min(newDamage, maxDur - 1); // keep 1 durability
            } else {
                newDamage = Math.min(newDamage, maxDur);     // allow full break
            }

            dmg.setDamage(newDamage);
            piece.setItemMeta((ItemMeta) dmg);
            anyHit = true;
        }

        if (anyHit) {
            player.getInventory().setArmorContents(armor);
            player.sendActionBar(Component.text("🛡 Armor damaged by shockwave!", NamedTextColor.DARK_RED));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CLEANUP on player quit
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        dashCooldowns.remove(id);
        windburstCooldowns.remove(id);
        combos.remove(id);
        launchedPlayers.remove(id);
        airTicks.remove(id);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /** Returns true if the item is the Shields SMP Mace (identified by PDC tag) */
    public boolean isMace(ItemStack item) {
        if (item == null || item.getType() != Material.MACE) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer()
                .has(plugin.getMaceKey(), PersistentDataType.BOOLEAN);
    }

    private boolean onCooldown(Map<UUID, Long> map, UUID id, long cooldownMs) {
        if (!map.containsKey(id)) return false;
        return (System.currentTimeMillis() - map.get(id)) < cooldownMs;
    }

    private long remainingSeconds(Map<UUID, Long> map, UUID id, long cooldownMs) {
        if (!map.containsKey(id)) return 0;
        long elapsed   = System.currentTimeMillis() - map.get(id);
        long remaining = cooldownMs - elapsed;
        return Math.max(0, (remaining + 999) / 1000); // ceiling seconds
    }

    /** Spawn particles evenly distributed around a horizontal circle */
    private void spawnParticleCircle(Location center, Particle particle, int count, double radius) {
        World world = center.getWorld();
        if (world == null) return;
        double step = (2 * Math.PI) / count;
        for (int i = 0; i < count; i++) {
            double angle = step * i;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            world.spawnParticle(particle, x, center.getY() + 0.1, z, 1, 0, 0, 0, 0);
        }
    }

    /** Spawn a ring of particles at a given expanding radius (used for shockwave animation) */
    private void spawnParticleRing(Location center, Particle particle, int count, double radius) {
        World world = center.getWorld();
        if (world == null) return;
        double step = (2 * Math.PI) / Math.max(count, 1);
        for (int i = 0; i < count; i++) {
            double angle = step * i;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            world.spawnParticle(particle, x, center.getY() + 0.05, z, 1, 0, 0, 0, 0);
        }
    }
}
