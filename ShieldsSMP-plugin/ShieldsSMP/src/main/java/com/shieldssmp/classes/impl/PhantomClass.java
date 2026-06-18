package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;

public class PhantomClass extends PlayerClass {

    private final Set<UUID> phasing  = new HashSet<>();
    private final Set<UUID> spectral = new HashSet<>();
    private final Set<UUID> revealed = new HashSet<>();
    /** Stored armor/shield per player during spectral realm */
    private final Map<UUID, ItemStack[]> storedArmor = new HashMap<>();
    private final Map<UUID, ItemStack>   storedOffhand = new HashMap<>();
    private final Map<UUID, ItemStack>   storedMainhand = new HashMap<>();

    @Override public String getName()         { return "Phantom"; }
    @Override public String getDescription()  { return "A shadow that slips between worlds"; }
    @Override public String getAbility1Name() { return "Phase"; }
    @Override public String getAbility2Name() { return "Void Blink"; }
    @Override public String getUltimateName() { return "Spectral Realm"; }
    @Override public String getAbility1CooldownKey() { return "Phase"; }
    @Override public String getAbility2CooldownKey() { return "VoidBlink"; }
    @Override public String getUltimateCooldownKey() { return "SpectralRealm"; }

    @Override
    public void tickPassive(Player player) {
        if (spectral.contains(player.getUniqueId())) return;
        if (player.isSneaking() && !revealed.contains(player.getUniqueId())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30, 0, false, false, false));
        } else if (!player.isSneaking()) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

    @Override public void onBreakBlock(Player player) { reveal(player); }
    @Override public void onDealDamage(Player attacker, LivingEntity victim, double damage) { reveal(attacker); }

    private void reveal(Player player) {
        UUID id = player.getUniqueId();
        if (revealed.contains(id)) return;
        revealed.add(id);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        new BukkitRunnable() {
            @Override public void run() { revealed.remove(id); }
        }.runTaskLater(getPlugin(), 40L);
    }

    // ── Ability 1: Phase – actually teleports through blocks each tick ─────────
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "Phase", 2 * MIN)) return;
        startCooldown(player, "Phase", 2 * MIN);

        UUID id = player.getUniqueId();
        phasing.add(id);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 1, false, false, false));
        player.sendActionBar(Component.text("👻 PHASE – Sneak to fly through blocks! 8s", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.5f);

        // Grant creative-like flight so player can move freely in any direction
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFlySpeed(0.15f); // slightly slower than creative

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!player.isOnline() || !phasing.contains(id) || ++ticks > 160) {
                    phasing.remove(id);
                    if (player.isOnline()) {
                        player.removePotionEffect(PotionEffectType.SPEED);
                        if (!player.getGameMode().equals(GameMode.CREATIVE) && !player.getGameMode().equals(GameMode.SPECTATOR)) {
                            player.setAllowFlight(false);
                            player.setFlying(false);
                            player.setFlySpeed(0.1f);
                        }
                    }
                    cancel(); return;
                }

                // While sneaking: push player in look direction through any blocks
                if (player.isSneaking()) {
                    Vector dir = player.getLocation().getDirection().normalize().multiply(0.6);
                    Location next = player.getLocation().clone().add(dir);
                    next.setYaw(player.getLocation().getYaw());
                    next.setPitch(player.getLocation().getPitch());
                    player.teleport(next);
                    player.getWorld().spawnParticle(Particle.PORTAL, next, 2, 0.1, 0.1, 0.1, 0.03);
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);

        new BukkitRunnable() {
            @Override public void run() {
                phasing.remove(id);
                if (!player.isOnline()) return;
                player.removePotionEffect(PotionEffectType.SPEED);
                if (!player.getGameMode().equals(GameMode.CREATIVE) && !player.getGameMode().equals(GameMode.SPECTATOR)) {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                    player.setFlySpeed(0.1f);
                }
                player.sendActionBar(Component.text("👻 Phase ended", NamedTextColor.GRAY));
            }
        }.runTaskLater(getPlugin(), 160L);
    }

    public boolean isPhasing(UUID id) { return phasing.contains(id); }

    // ── Ability 2: Void Blink ─────────────────────────────────────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "VoidBlink", (long)(1.5 * MIN))) return;
        startCooldown(player, "VoidBlink", (long)(1.5 * MIN));

        Location eye = player.getEyeLocation();
        Vector step = eye.getDirection().normalize().multiply(0.5);
        Location dest = eye.clone(), safe = player.getLocation().clone();
        boolean wentThrough = false;
        for (int i = 0; i < 24; i++) {
            dest.add(step);
            if (!dest.getBlock().getType().isSolid()) {
                safe = dest.clone(); safe.setYaw(player.getLocation().getYaw()); safe.setPitch(player.getLocation().getPitch());
            } else { wentThrough = true; }
        }
        player.teleport(safe);
        player.getWorld().spawnParticle(Particle.PORTAL, safe, 30, 0.3, 0.5, 0.3, 0.1);
        player.getWorld().playSound(safe, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.8f);
        if (wentThrough) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            player.sendActionBar(Component.text("⚫ Void Blink – clipped block! Slowness", NamedTextColor.DARK_PURPLE));
        } else { player.sendActionBar(Component.text("⚫ VOID BLINK!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)); }
    }

    // ── Ultimate: Spectral Realm – hides armor + shield, phases, invulnerable ──
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "SpectralRealm", 5 * MIN)) return;
        startCooldown(player, "SpectralRealm", 5 * MIN);

        UUID id = player.getUniqueId();
        spectral.add(id);
        phasing.add(id);

        // Hide armor and items so the player is truly invisible
        ItemStack[] armor   = player.getInventory().getArmorContents();
        ItemStack   mainHand = player.getInventory().getItemInMainHand().clone();
        ItemStack   offHand  = player.getInventory().getItemInOffHand().clone();
        storedArmor.put(id, armor.clone());
        storedMainhand.put(id, mainHand);
        storedOffhand.put(id, offHand);

        // Clear worn items so armor is invisible
        player.getInventory().setArmorContents(new ItemStack[]{null, null, null, null});
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));

        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 160, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,   160, 255, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,        160, 2, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 160, 0, false, false, false));
        player.setAllowFlight(true);
        player.setFlying(true);

        player.showTitle(Title.title(
                Component.text("✦ SPECTRAL REALM", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text("8s – Sneak to fly through any block!", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(3), Duration.ofMillis(400))));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 2f);

        // Spectral Realm: sneak to phase-fly in look direction
        player.setFlySpeed(0.2f); // faster than regular phase
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!spectral.contains(id) || !player.isOnline() || ++t > 160) { cancel(); return; }

                // Sneak = phase fly in look direction (through any block)
                if (player.isSneaking()) {
                    Vector dir = player.getLocation().getDirection().normalize().multiply(0.7);
                    Location next = player.getLocation().clone().add(dir);
                    next.setYaw(player.getLocation().getYaw());
                    next.setPitch(player.getLocation().getPitch());
                    player.teleport(next);
                }
                // Constant portal particle aura
                if (t % 3 == 0)
                    player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0,1,0), 4, 0.3, 0.5, 0.3, 0.05);
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);

        new BukkitRunnable() {
            @Override public void run() {
                spectral.remove(id); phasing.remove(id);
                if (!player.isOnline()) { storedArmor.remove(id); storedMainhand.remove(id); storedOffhand.remove(id); return; }

                // Restore armor and items
                ItemStack[] savedArmor = storedArmor.remove(id);
                if (savedArmor != null) player.getInventory().setArmorContents(savedArmor);
                ItemStack savedMain = storedMainhand.remove(id);
                if (savedMain != null && !savedMain.getType().isAir()) player.getInventory().setItemInMainHand(savedMain);
                ItemStack savedOff = storedOffhand.remove(id);
                if (savedOff != null && !savedOff.getType().isAir()) player.getInventory().setItemInOffHand(savedOff);

                for (PotionEffectType t : List.of(PotionEffectType.INVISIBILITY, PotionEffectType.RESISTANCE,
                        PotionEffectType.SPEED, PotionEffectType.NIGHT_VISION))
                    player.removePotionEffect(t);
                player.setFlySpeed(0.1f); // reset fly speed
                if (!player.getGameMode().equals(GameMode.CREATIVE) && !player.getGameMode().equals(GameMode.SPECTATOR)) {
                    player.setAllowFlight(false); player.setFlying(false);
                }
                player.sendActionBar(Component.text("✦ Spectral Realm ended – armor restored", NamedTextColor.GRAY));
            }
        }.runTaskLater(getPlugin(), 160L);
    }

    public boolean isSpectral(UUID id) { return spectral.contains(id); }

    @Override
    public void onDeath(Player player) {
        super.onDeath(player);
        UUID id = player.getUniqueId();
        // Restore armor on death so it drops
        ItemStack[] savedArmor = storedArmor.remove(id);
        if (savedArmor != null && player.getInventory().getArmorContents()[0] == null)
            player.getInventory().setArmorContents(savedArmor);
        storedMainhand.remove(id);
        storedOffhand.remove(id);
        spectral.remove(id);
        phasing.remove(id);
        phaseOrigin.remove(id);
    }

    /** Starting positions when phase was activated – for 20-block cap */
    private final Map<UUID, Location> phaseOrigin = new HashMap<>();

    /**
     * Core phase movement: while sneaking, noclip in exact look direction (incl. ground).
     * Hard cap: cannot travel more than 20 blocks from where phase started.
     */
    private void phaseMovement(Player player, UUID id) {
        if (player.isSneaking()) {
            // Record origin on first sneak tick
            phaseOrigin.putIfAbsent(id, player.getLocation().clone());

            // Check 20-block cap from origin
            Location origin = phaseOrigin.get(id);
            if (origin != null && player.getLocation().distance(origin) >= 20) {
                player.setAllowFlight(false);
                player.sendActionBar(Component.text("👻 Phase limit reached (20 blocks)!",
                        NamedTextColor.GRAY));
                return;
            }

            // Noclip in exact look direction – works through floors, ceilings, walls
            player.setAllowFlight(true);
            player.setFlying(true); // true so gravity doesn't fight the teleport

            Vector look = player.getLocation().getDirection().normalize().multiply(0.45);
            Location next = player.getLocation().clone().add(look);
            next.setYaw(player.getLocation().getYaw());
            next.setPitch(player.getLocation().getPitch());
            player.teleport(next);
            player.getWorld().spawnParticle(Particle.PORTAL, next, 2, 0.1, 0.1, 0.1, 0.03);
        } else {
            // Not sneaking: reset origin so cap resets each sneak burst
            phaseOrigin.remove(id);
            player.setAllowFlight(false);
            player.setFlying(false);
            // If currently inside a solid block (e.g. wall), push forward to escape
            if (player.getLocation().getBlock().getType().isSolid()) {
                Vector dir = player.getLocation().getDirection().normalize().multiply(0.4);
                Location next = player.getLocation().clone().add(dir);
                next.setYaw(player.getLocation().getYaw());
                next.setPitch(player.getLocation().getPitch());
                player.teleport(next);
            }
        }
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
