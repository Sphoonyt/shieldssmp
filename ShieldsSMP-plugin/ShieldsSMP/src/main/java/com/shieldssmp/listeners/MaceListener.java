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
import org.bukkit.event.entity.EntityDamageEvent;
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

    private final ShieldsSMP plugin;

    // ── Config values ──────────────────────────────────────────────────────────
    private double dashVelocity;
    private long   dashCooldownMs;

    private int    comboHitsRequired;
    private long   comboResetMs;
    private double launchVelocity;
    private long   landingCooldownMs;

    private double  shockwaveRadius;
    private double  shockwaveKnockback;
    private double  armorDamageFraction;
    private boolean allowArmorBreak;

    // ── Per-hit cooldown gate (prevents spam-click combo) ────────────────────────
    private final com.shieldssmp.classes.CooldownManager cd = new com.shieldssmp.classes.CooldownManager();

    // ── State ──────────────────────────────────────────────────────────────────
    private final Map<UUID, Long> dashCooldowns    = new HashMap<>();
    /** Full mace cooldown after landing from a launch */
    private final Map<UUID, Long> landingCooldowns = new HashMap<>();

    private record ComboEntry(int hits, long lastHitMs) {}
    private final Map<UUID, ComboEntry> combos = new HashMap<>();

    /** Players currently airborne from a 3-hit launch */
    private final Set<UUID> launchedPlayers = new HashSet<>();
    /** Fall damage is cancelled for these players */
    private final Set<UUID> fallImmune      = new HashSet<>();

    private final Map<UUID, Integer> airTicks   = new HashMap<>();
    /** Consecutive ticks each player has been on the ground (resets on lift-off) */
    private final Map<UUID, Integer> groundTicks = new HashMap<>();
    /** Players who landed a mace hit and are pending the landing cooldown */
    private final Set<UUID> maceHitPending = new HashSet<>();
    /** Players whose landing cooldown has already been triggered this grounding */
    private final Set<UUID> cooldownTriggered = new HashSet<>();
    /** 0.3 s = 6 ticks of continuous ground contact required before cooldown fires */
    private static final int GROUND_CONFIRM_TICKS = 6;

    // ──────────────────────────────────────────────────────────────────────────

    public MaceListener(ShieldsSMP plugin) {
        this.plugin = plugin;
        reloadSettings();
        startGroundTracker();
    }

    public void reloadSettings() {
        var cfg = plugin.getConfig();
        dashVelocity        = cfg.getDouble("mace.dash.velocity",                     1.6);
        dashCooldownMs      = cfg.getLong  ("mace.dash.cooldown",                     5)   * 1000L;
        comboHitsRequired   = cfg.getInt   ("mace.combo.hits-required",               3);
        comboResetMs        = cfg.getLong  ("mace.combo.reset-seconds",               5)   * 1000L;
        launchVelocity      = cfg.getDouble("mace.combo.launch-velocity",             3.2);
        landingCooldownMs   = cfg.getLong  ("mace.combo.landing-cooldown-seconds",    30)  * 1000L;
        shockwaveRadius     = cfg.getDouble("mace.shockwave.radius",                  10.0);
        shockwaveKnockback  = cfg.getDouble("mace.shockwave.knockback",               2.0);
        armorDamageFraction = cfg.getDouble("mace.shockwave.armor-damage-fraction",   0.5);
        allowArmorBreak     = cfg.getBoolean("mace.shockwave.allow-break",            false);
    }

    /** Called by /macecooldown to override the landing cooldown at runtime */
    public void setLandingCooldown(long seconds) {
        landingCooldownMs = seconds * 1000L;
        plugin.getConfig().set("mace.combo.landing-cooldown-seconds", seconds);
        plugin.saveConfig();
    }

    public long getLandingCooldownSeconds() {
        return landingCooldownMs / 1000L;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUND TRACKER  (every 2 ticks)
    // ══════════════════════════════════════════════════════════════════════════

    private void startGroundTracker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID id = player.getUniqueId();
                    boolean onGround = player.isOnGround();

                    if (onGround) {
                        int accumulated  = airTicks.getOrDefault(id, 0);
                        int groundFrames = groundTicks.merge(id, 2, Integer::sum); // +2 ticks per cycle

                        if (accumulated > 0) {
                            // First tick back on ground – reset combo and note the landing
                            combos.remove(id);
                            cooldownTriggered.remove(id); // allow fresh trigger this grounding

                            // If launched: shockwave fires immediately on first ground contact
                            boolean wasLaunched = launchedPlayers.remove(id);
                            if (wasLaunched) {
                                // Trigger shockwave first, then remove fall immunity 1 tick later
                                // so the EntityDamageEvent (FALL) fires AFTER our cancel
                                double startY = fallStartY.getOrDefault(id, player.getLocation().getY());
                                double fell = startY - player.getLocation().getY();
                                if (fell >= 10) {
                                    triggerShockwave(player.getLocation(), player);
                                } else {
                                    player.sendActionBar(Component.text("💥 Landed too low for shockwave! (need 10+ blocks)", NamedTextColor.GRAY));
                                }
                                fallStartY.remove(id);
                                final UUID fid = id;
                                new org.bukkit.scheduler.BukkitRunnable() {
                                    @Override public void run() { fallImmune.remove(fid); }
                                }.runTaskLater(plugin, 2L);
                                // Mark pending so cooldown fires once ground is confirmed
                                maceHitPending.add(id);
                            }

                            airTicks.put(id, 0);
                        }

                        // Cooldown fires only after 0.3 s of continuous ground contact
                        // and only if player had previously hit someone with the mace
                        if (groundFrames >= GROUND_CONFIRM_TICKS
                                && maceHitPending.contains(id)
                                && !cooldownTriggered.contains(id)) {
                            cooldownTriggered.add(id);
                            maceHitPending.remove(id);
        groundTicks.remove(id);
        cooldownTriggered.remove(id);
        fallStartY.remove(id);
                            landingCooldowns.put(id, System.currentTimeMillis());
                            long sec = landingCooldownMs / 1000L;
                            player.sendActionBar(Component.text(
                                    "🛑 Mace locked for " + sec + "s", NamedTextColor.RED));
                        }

                    } else {
                        // Airborne – accumulate air ticks, reset ground counter
                        int prevAir = airTicks.getOrDefault(id, 0);
                        if (prevAir == 0) {
                            // Just left the ground – record starting Y
                            fallStartY.put(id, player.getLocation().getY());
                        }
                        airTicks.merge(id, 2, Integer::sum);
                        groundTicks.put(id, 0);
                        cooldownTriggered.remove(id); // reset so next landing can trigger
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DASH  (Right-Click with Mace)
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

        // Landing cooldown blocks ALL mace use
        if (onCooldown(landingCooldowns, id, landingCooldownMs)) {
            long sec = remainingSeconds(landingCooldowns, id, landingCooldownMs);
            player.sendActionBar(Component.text("🛑 Mace locked for " + sec + "s", NamedTextColor.RED));
            return;
        }

        if (onCooldown(dashCooldowns, id, dashCooldownMs)) {
            long sec = remainingSeconds(dashCooldowns, id, dashCooldownMs);
            player.sendActionBar(Component.text("⏳ Dash ready in " + sec + "s", NamedTextColor.YELLOW));
            return;
        }

        Vector dir = player.getLocation().getDirection().normalize().multiply(dashVelocity);
        dir.setY(Math.max(dir.getY(), 0.25));
        player.setVelocity(dir);

        spawnParticleCircle(player.getLocation(), Particle.GUST, 16, 0.6);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.3f);
        player.sendActionBar(Component.text("▶▶ DASH!", NamedTextColor.AQUA, TextDecoration.BOLD));

        dashCooldowns.put(id, System.currentTimeMillis());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  3-HIT COMBO → LAUNCH ATTACKER + SHOCKWAVE ON LAND
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMaceHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (!isMace(item)) return;

        UUID attackerId = attacker.getUniqueId();
        long now        = System.currentTimeMillis();

        // Landing cooldown blocks ALL mace attacks
        if (onCooldown(landingCooldowns, attackerId, landingCooldownMs)) {
            event.setCancelled(true);
            long sec = remainingSeconds(landingCooldowns, attackerId, landingCooldownMs);
            attacker.sendActionBar(Component.text("🛑 Mace locked for " + sec + "s", NamedTextColor.RED));
            return;
        }

        // Each successful hit resets the dash cooldown and arms the landing cooldown
        dashCooldowns.remove(attackerId);
        maceHitPending.add(attackerId);

        // Anti-spam: require at least 0.4s between combo increments (8 ticks)
        // This ensures only real hits (with swing animation delay) count
        if (cd.isOnCooldown(attackerId, "ComboHitGate")) return;
        cd.set(attackerId, "ComboHitGate", 400L); // 0.4s gate

        // Update combo – any entity counts, only resets on timeout or mace cooldown
        ComboEntry prev = combos.get(attackerId);
        int hits;
        if (prev == null || (now - prev.lastHitMs()) > comboResetMs) {
            hits = 1;
        } else {
            hits = prev.hits() + 1;
        }

        // Combo HUD
        String bar = "▮".repeat(hits) + "▯".repeat(Math.max(0, comboHitsRequired - hits));
        NamedTextColor barColor = hits >= comboHitsRequired ? NamedTextColor.RED : NamedTextColor.GOLD;
        attacker.sendActionBar(
                Component.text("⚔ Combo  ", NamedTextColor.WHITE)
                         .append(Component.text(bar, barColor, TextDecoration.BOLD))
                         .append(Component.text("  " + hits + "/" + comboHitsRequired, NamedTextColor.GRAY)));

        target.getWorld().spawnParticle(Particle.CRIT,
                target.getLocation().add(0, 1, 0), 12, 0.3, 0.3, 0.3, 0.1);

        if (hits >= comboHitsRequired) {
            combos.remove(attackerId);
            launchAttacker(attacker);
        } else {
            combos.put(attackerId, new ComboEntry(hits, now));
        }
    }

    // ── Launch the attacker ~50 blocks upward ─────────────────────────────────

    private void launchAttacker(Player attacker) {
        Location loc   = attacker.getLocation();
        World    world = attacker.getWorld();

        UUID launchId = attacker.getUniqueId();
        launchedPlayers.add(launchId);
        fallImmune.add(launchId);

        // Delay 1 tick so Wind Burst enchantment fires first, then we ADD on top of it
        // This means Wind Burst's upward boost is preserved and stacks with our launch
        new BukkitRunnable() {
            @Override public void run() {
                if (!attacker.isOnline()) return;
                // Add our launch velocity ON TOP of whatever Wind Burst already applied
                Vector current = attacker.getVelocity();
                attacker.setVelocity(new Vector(current.getX(), launchVelocity + Math.max(current.getY(), 0), current.getZ()));
            }
        }.runTaskLater(plugin, 1L);

        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 2, 0.4, 0, 0.4);
        world.spawnParticle(Particle.GUST, loc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0);
        world.playSound(loc, Sound.ENTITY_BREEZE_WIND_BURST, 2f, 0.55f);
        world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);

        attacker.showTitle(Title.title(
                Component.text("⬆ LAUNCHED!", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("Land for a shockwave!", NamedTextColor.GRAY),
                Title.Times.times(
                        Duration.ofMillis(100),
                        Duration.ofSeconds(2),
                        Duration.ofMillis(400))));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CANCEL FALL DAMAGE for launched players
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (fallImmune.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SHOCKWAVE
    // ══════════════════════════════════════════════════════════════════════════

    private void triggerShockwave(Location loc, Player lander) {
        World world = loc.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 4, 1.5, 0, 1.5);
        world.spawnParticle(Particle.GUST, loc.clone().add(0, 0.5, 0),
                80, shockwaveRadius * 0.5, 1.5, shockwaveRadius * 0.5, 0);
        world.spawnParticle(Particle.CRIT, loc.clone().add(0, 0.3, 0),
                30, shockwaveRadius * 0.3, 0.3, shockwaveRadius * 0.3, 0.2);

        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE,   1.5f, 0.65f);
        world.playSound(loc, Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 0.45f);
        world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 0.9f);

        new BukkitRunnable() {
            double r = 0;
            @Override public void run() {
                r += 1.2;
                spawnParticleRing(loc, Particle.GUST, (int)(r * 3), r);
                if (r >= shockwaveRadius) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);

        double r = shockwaveRadius;
        for (Entity entity : world.getNearbyEntities(loc, r, r / 2.0, r)) {
            if (!(entity instanceof Player victim)) continue;
            if (entity.equals(lander)) continue;

            damageAllArmor(victim);

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

        lander.sendActionBar(Component.text("💥 Shockwave!", NamedTextColor.GOLD, TextDecoration.BOLD));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ARMOR DAMAGE
    // ══════════════════════════════════════════════════════════════════════════

    private void damageAllArmor(Player player) {
        ItemStack[] armor  = player.getInventory().getArmorContents();
        boolean     anyHit = false;

        for (ItemStack piece : armor) {
            if (piece == null || piece.getType().isAir()) continue;
            if (!(piece.getItemMeta() instanceof Damageable dmg)) continue;

            short maxDur = piece.getType().getMaxDurability();
            if (maxDur <= 0) continue;

            // Damage = half of REMAINING durability (current health of the item)
            int currentDamage  = dmg.getDamage();
            int remainingDur   = maxDur - currentDamage;
            int addDamage      = Math.max(1, remainingDur / 2); // half of what's left
            int newDamage      = currentDamage + addDamage;

            newDamage = allowArmorBreak
                    ? Math.min(newDamage, maxDur)
                    : Math.min(newDamage, maxDur - 1);

            dmg.setDamage(newDamage);
            piece.setItemMeta((ItemMeta) dmg);
            anyHit = true;
        }

        if (anyHit) {
            player.getInventory().setArmorContents(armor);
            player.sendActionBar(Component.text("🛡 Armor durability halved!", NamedTextColor.DARK_RED));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CLEANUP
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        dashCooldowns.remove(id);
        landingCooldowns.remove(id);
        combos.remove(id);
        launchedPlayers.remove(id);
        fallImmune.remove(id);
        airTicks.remove(id);
        maceHitPending.remove(id);
        groundTicks.remove(id);
        cooldownTriggered.remove(id);
        fallStartY.remove(id);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    public boolean isMace(ItemStack item) {
        if (item == null || item.getType() != Material.MACE) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer()
                .has(plugin.getMaceKey(), PersistentDataType.BOOLEAN);
    }

    private boolean onCooldown(Map<UUID, Long> map, UUID id, long ms) {
        if (!map.containsKey(id)) return false;
        return (System.currentTimeMillis() - map.get(id)) < ms;
    }

    private long remainingSeconds(Map<UUID, Long> map, UUID id, long ms) {
        if (!map.containsKey(id)) return 0;
        long remaining = ms - (System.currentTimeMillis() - map.get(id));
        return Math.max(0, (remaining + 999) / 1000);
    }

    private void spawnParticleCircle(Location center, Particle particle, int count, double radius) {
        World world = center.getWorld();
        if (world == null) return;
        double step = (2 * Math.PI) / count;
        for (int i = 0; i < count; i++) {
            double angle = step * i;
            world.spawnParticle(particle,
                    center.getX() + radius * Math.cos(angle),
                    center.getY() + 0.1,
                    center.getZ() + radius * Math.sin(angle),
                    1, 0, 0, 0, 0);
        }
    }

    private void spawnParticleRing(Location center, Particle particle, int count, double radius) {
        World world = center.getWorld();
        if (world == null) return;
        double step = (2 * Math.PI) / Math.max(count, 1);
        for (int i = 0; i < count; i++) {
            double angle = step * i;
            world.spawnParticle(particle,
                    center.getX() + radius * Math.cos(angle),
                    center.getY() + 0.05,
                    center.getZ() + radius * Math.sin(angle),
                    1, 0, 0, 0, 0);
        }
    }
}
