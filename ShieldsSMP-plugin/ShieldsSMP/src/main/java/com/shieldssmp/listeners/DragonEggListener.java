package com.shieldssmp.listeners;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.CooldownManager;
import com.shieldssmp.items.MythicalItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Dragon Egg – a standalone, freely-droppable item separate from the class
 * shield system. No levels, no Upgrade Core, no Ultimate Upgrader required;
 * all 3 abilities work immediately as long as the egg is held in main hand.
 * Passives work just from having the egg ANYWHERE in inventory.
 */
public class DragonEggListener implements Listener {

    private static final String FIREBALL_TAG = "dragon_egg_fireball";

    private final ShieldsSMP    plugin;
    private final MythicalItems mi;
    private final CooldownManager cd = new CooldownManager();
    private final Random rng = new Random();

    /** Recursion guard for the potion-duration-doubling passive */
    private final Set<UUID> doublingInProgress = new HashSet<>();

    /** Per-target hit cooldown during an active dragon ride (prevents repeat-knockback spam) */
    private final Map<UUID, Long> dragonHitCooldown = new HashMap<>();

    public DragonEggListener(ShieldsSMP plugin) {
        this.plugin = plugin;
        this.mi     = plugin.getMythicalItems();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PASSIVES (work anywhere in inventory – no need to hold the egg)
    // ══════════════════════════════════════════════════════════════════════════

    /** Golden apples grant full saturation */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Material type = event.getItem().getType();
        if (type != Material.GOLDEN_APPLE && type != Material.ENCHANTED_GOLDEN_APPLE) return;
        if (!hasDragonEgg(player)) return;

        // Top off food + saturation to full, on top of the apple's normal effects
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.sendActionBar(Component.text("🥚 Dragon Egg: Full saturation!", NamedTextColor.GOLD));
    }

    /** All potion effect durations are doubled */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED
                && event.getAction() != EntityPotionEffectEvent.Action.CHANGED) return;

        UUID id = player.getUniqueId();
        if (doublingInProgress.contains(id)) return; // avoid recursive re-trigger
        if (!hasDragonEgg(player)) return;

        PotionEffect newEffect = event.getNewEffect();
        if (newEffect == null) return;
        if (newEffect.getDuration() <= 0 || newEffect.getDuration() >= 1_000_000) return; // skip permanent/odd effects

        event.setCancelled(true);
        PotionEffect doubled = new PotionEffect(
                newEffect.getType(), newEffect.getDuration() * 2, newEffect.getAmplifier(),
                newEffect.isAmbient(), newEffect.hasParticles(), newEffect.hasIcon());

        doublingInProgress.add(id);
        player.addPotionEffect(doubled);
        doublingInProgress.remove(id);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ABILITIES (require holding the Dragon Egg in main hand)
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!mi.isDragonEgg(held)) return;

        Action action    = event.getAction();
        boolean left     = action == Action.LEFT_CLICK_AIR  || action == Action.LEFT_CLICK_BLOCK;
        boolean right    = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean sneaking = player.isSneaking();

        if (sneaking && left) {
            event.setCancelled(true);
            useFireball(player);
        } else if (sneaking && right) {
            event.setCancelled(true);
            useWingDash(player);
        } else if (!sneaking && right) {
            event.setCancelled(true);
            useDragonMount(player);
        }
    }

    // ── Ability 1: Dragon's Wrath – fireball that leaves a 10s dragon's breath ──
    private void useFireball(Player player) {
        UUID id = player.getUniqueId();
        if (cd.isOnCooldown(id, "DragonFireball")) {
            player.sendActionBar(Component.text("🥚 Dragon's Wrath: " + cd.remainingSeconds(id, "DragonFireball") + "s", NamedTextColor.RED));
            return;
        }
        cd.set(id, "DragonFireball", 120_000L);
        player.setCooldown(Material.DRAGON_EGG, 120 * 20);

        Location eye = player.getEyeLocation();
        DragonFireball fireball = player.getWorld().spawn(eye, DragonFireball.class);
        fireball.setShooter(player);
        fireball.setVelocity(eye.getDirection().normalize().multiply(2.2));
        fireball.setMetadata(FIREBALL_TAG, new FixedMetadataValue(plugin, player.getUniqueId().toString()));

        player.sendActionBar(Component.text("🥚 DRAGON'S WRATH!", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        player.getWorld().playSound(eye, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.5f, 1f);
    }

    @EventHandler
    public void onFireballHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof DragonFireball fireball)) return;
        if (!fireball.hasMetadata(FIREBALL_TAG)) return;

        UUID casterId = UUID.fromString(fireball.getMetadata(FIREBALL_TAG).get(0).asString());
        Player caster = Bukkit.getPlayer(casterId);
        Location impact = fireball.getLocation();
        World world = fireball.getWorld();

        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.2f);
        world.spawnParticle(Particle.DRAGON_BREATH, impact, 60, 1.5, 1, 1.5, 0.1);

        var trust = plugin.getTrustSystem();

        // 10-second lingering dragon's breath zone – deals damage to non-trusted enemies inside
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (++ticks > 200) { cancel(); return; } // 10s
                world.spawnParticle(Particle.DRAGON_BREATH, impact, 8, 1.5, 0.5, 1.5, 0.02);

                if (ticks % 20 == 0) { // damage tick every second
                    for (Entity e : world.getNearbyEntities(impact, 3, 2, 3)) {
                        if (!(e instanceof Player p)) continue;
                        if (caster != null) {
                            if (p.equals(caster)) continue;
                            if (trust.isTrusted(casterId, p.getUniqueId())) continue;
                        }
                        p.damage(2, caster); // 1 heart per second
                        p.sendActionBar(Component.text("🐉 Caught in Dragon's Breath!", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Ability 2: Wing Dash – 12-block physical dash, damages nearby enemies ──
    private void useWingDash(Player player) {
        UUID id = player.getUniqueId();
        if (cd.isOnCooldown(id, "WingDash")) {
            player.sendActionBar(Component.text("🥚 Wing Dash: " + cd.remainingSeconds(id, "WingDash") + "s", NamedTextColor.RED));
            return;
        }
        cd.set(id, "WingDash", 70_000L);
        player.setCooldown(Material.DRAGON_EGG, 70 * 20);

        World world = player.getWorld();
        Vector dir = player.getLocation().getDirection().setY(0).normalize();
        var trust = plugin.getTrustSystem();
        Set<UUID> alreadyHit = new HashSet<>();

        player.sendActionBar(Component.text("🥚 WING DASH!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 1.3f);

        // Physical dash: push the player forward each tick for 12 ticks (~12 blocks),
        // not a teleport – respects collision via normal velocity-based movement.
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!player.isOnline() || ++ticks > 12) { cancel(); return; }

                player.setVelocity(dir.clone().multiply(1.4).setY(0.05));
                world.spawnParticle(Particle.CLOUD, player.getLocation(), 4, 0.2, 0.1, 0.2, 0.02);

                // Damage nearby enemies along the dash path (once each)
                for (Entity e : world.getNearbyEntities(player.getLocation(), 2, 1.5, 2)) {
                    if (!(e instanceof Player victim) || victim.equals(player)) continue;
                    if (alreadyHit.contains(victim.getUniqueId())) continue;
                    if (trust.isTrusted(player.getUniqueId(), victim.getUniqueId())) continue;

                    alreadyHit.add(victim.getUniqueId());
                    victim.damage(8, player); // 4 hearts
                    victim.sendActionBar(Component.text("🥚 Wing Dash: 4 hearts!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Ultimate: Summon Dragon Mount – ride a dragon for 15s, knockback on hit ─
    private void useDragonMount(Player player) {
        UUID id = player.getUniqueId();
        if (cd.isOnCooldown(id, "DragonMount")) {
            player.sendActionBar(Component.text("🥚 Dragon Mount: " + cd.remainingSeconds(id, "DragonMount") + "s", NamedTextColor.RED));
            return;
        }
        cd.set(id, "DragonMount", 300_000L);
        player.setCooldown(Material.DRAGON_EGG, 300 * 20);

        World world = player.getWorld();
        EnderDragon dragon = (EnderDragon) world.spawnEntity(player.getLocation(), EntityType.ENDER_DRAGON);
        dragon.setAI(false);
        dragon.setGravity(false);
        dragon.setInvulnerable(true);
        dragon.setCustomName("§5" + player.getName() + "'s Dragon");
        dragon.setCustomNameVisible(true);
        dragon.addPassenger(player);

        player.sendActionBar(Component.text("🐉 DRAGON MOUNT SUMMONED – fly by looking around! 15s", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.8f);

        var trust = plugin.getTrustSystem();

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!dragon.isValid() || !player.isOnline() || ++ticks > 300) { // 15s
                    if (dragon.isValid()) {
                        dragon.removePassenger(player);
                        dragon.remove();
                    }
                    if (player.isOnline())
                        player.sendActionBar(Component.text("🐉 Dragon Mount dissipated", NamedTextColor.GRAY));
                    cancel(); return;
                }

                // Drive the dragon's flight using the rider's look direction
                Vector dir = player.getEyeLocation().getDirection().normalize().multiply(0.9);
                dragon.teleport(dragon.getLocation().add(dir).setDirection(dir));

                // Knockback anyone the dragon collides with (excluding rider, trusted players)
                for (Entity e : world.getNearbyEntities(dragon.getLocation(), 3, 2, 3)) {
                    if (!(e instanceof Player victim) || victim.equals(player)) continue;
                    if (trust.isTrusted(player.getUniqueId(), victim.getUniqueId())) continue;

                    long now = System.currentTimeMillis();
                    UUID vid = victim.getUniqueId();
                    if (dragonHitCooldown.getOrDefault(vid, 0L) > now) continue; // 1s immunity between hits
                    dragonHitCooldown.put(vid, now + 1000L);

                    Vector knockback = victim.getLocation().toVector().subtract(dragon.getLocation().toVector());
                    if (knockback.lengthSquared() < 0.01) knockback = dir.clone();
                    knockback.normalize().multiply(4.5).setY(0.9);
                    victim.setVelocity(knockback);
                    world.playSound(victim.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.5f);
                    victim.sendActionBar(Component.text("🐉 Slammed by the Dragon Mount!", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private boolean hasDragonEgg(Player player) {
        if (mi.isDragonEgg(player.getInventory().getItemInMainHand())) return true;
        if (mi.isDragonEgg(player.getInventory().getItemInOffHand())) return true;
        for (ItemStack item : player.getInventory().getContents())
            if (mi.isDragonEgg(item)) return true;
        return false;
    }
}
