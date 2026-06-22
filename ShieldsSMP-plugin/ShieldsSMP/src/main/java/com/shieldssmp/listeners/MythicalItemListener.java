package com.shieldssmp.listeners;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.CooldownManager;
import com.shieldssmp.items.MythicalItems;
import com.shieldssmp.items.SpecialItems;
import com.shieldssmp.systems.ClassManager;
import com.shieldssmp.systems.LifeSystem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.Arrays;

public class MythicalItemListener implements Listener {

    private final ShieldsSMP  plugin;
    private final MythicalItems mi;
    private final SpecialItems  si;
    private final ClassManager  cm;
    private final LifeSystem    ls;
    private final CooldownManager cd = new CooldownManager();

    // State
    private final Set<UUID> chestplateActive  = new HashSet<>(); // resistance V + 2x dmg
    private final Set<UUID> bootsAirborne     = new HashSet<>(); // tracking fall height
    private final Map<UUID, Double> bootsFallStart = new HashMap<>();
    /** Rewind snapshots: uuid → state */
    private record RewindState(Location loc, double health, ItemStack[] inventory, ItemStack[] armor, ItemStack offhand) {}
    private final Map<UUID, RewindState> rewindSnapshots = new HashMap<>();

    // Extra inventory slots from leggings (virtual – we use ender chest rows as proxy)
    // Note: true extra slots require a GUI; we grant via ender chest-size expansion (not natively
    // possible without client mods). Instead we give a persistent extra row via a custom inventory
    // that auto-saves. For simplicity we notify the player and grant carry-weight bonus via speed.

    public MythicalItemListener(ShieldsSMP plugin) {
        this.plugin = plugin;
        this.mi     = plugin.getMythicalItems();
        this.si     = plugin.getSpecialItems();
        this.cm     = plugin.getClassManager();
        this.ls     = plugin.getLifeSystem();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PASSIVE TICKER – runs every 20 ticks (1s)
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        schedulePassiveTick(event.getPlayer());
    }

    private void schedulePassiveTick(Player player) {
        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                applyPassives(player);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void applyPassives(Player player) {
        PlayerInventory inv = player.getInventory();

        // Helmet: Water Breathing
        if (mi.isShieldedHelmet(inv.getHelmet())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 40, 0, false, false, false));
        }

        // Chestplate: Resistance I
        if (mi.isShieldedChest(inv.getChestplate())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, false, false));
        }

        // Boots: Speed II + Dolphin's Grace
        if (mi.isShieldedBoots(inv.getBoots())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,           40, 1, false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE,  40, 2, false, false, false));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ON HIT – Helmet pulse + Chestplate proc
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        // Cast to Player only where needed
        Player victimPlayer = victim instanceof Player p ? p : null;

        PlayerInventory inv = attacker.getInventory();

        // ── Helmet: 1/20 chance – pulse disables enemy shields for 30s + 2 hearts
        //     Has a 15s internal cooldown per attacker to prevent too-frequent procs ──
        if (victimPlayer != null && mi.isShieldedHelmet(inv.getHelmet())
                && !cd.isOnCooldown(attacker.getUniqueId(), "HelmetPulse")
                && new Random().nextInt(20) == 0) {
            cd.set(attacker.getUniqueId(), "HelmetPulse", 15_000L);
            attacker.setCooldown(org.bukkit.Material.GOLDEN_HELMET, 15 * 20);
            // Disable the victim's class shield abilities for 30s via ClassManager nullifier
            cm.disableAbilities(victimPlayer, 30_000L);
            // True damage 2 hearts
            victimPlayer.setHealth(Math.max(0, victimPlayer.getHealth() - 4));
            victimPlayer.getWorld().spawnParticle(Particle.CRIT, victimPlayer.getLocation().add(0,1,0), 15, 0.4, 0.4, 0.4);
            victimPlayer.getWorld().playSound(victimPlayer.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 1.5f);
            victimPlayer.sendActionBar(Component.text("⚡ Helmet Pulse: shields disabled 30s!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            attacker.sendActionBar(Component.text("⚡ Helmet Pulse triggered!", NamedTextColor.GOLD, TextDecoration.BOLD));
        }

        // ── Chestplate: 1/50 chance – Resistance V + 2x damage for 8s ────────────
        if (mi.isShieldedChest(inv.getChestplate())
                && !chestplateActive.contains(attacker.getUniqueId())
                && new Random().nextInt(50) == 0) {

            UUID aid = attacker.getUniqueId();
            chestplateActive.add(aid);
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 160, 4, false, true, true));
            attacker.setCooldown(org.bukkit.Material.GOLDEN_CHESTPLATE, 50 * 20); // 50-hit cooldown visual (max visual)
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,   160, 2, false, true, true)); // 2x damage proxy
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,    160, 0, false, true, true));
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.8f);
            attacker.sendActionBar(Component.text("✦ CHESTPLATE PROC! Resistance + 2x damage for 8s!", NamedTextColor.YELLOW, TextDecoration.BOLD));

            new BukkitRunnable() {
                @Override public void run() {
                    chestplateActive.remove(aid);
                    if (attacker.isOnline()) {
                        attacker.removePotionEffect(PotionEffectType.RESISTANCE);
                        attacker.removePotionEffect(PotionEffectType.STRENGTH);
                        attacker.removePotionEffect(PotionEffectType.GLOWING);
                        attacker.sendActionBar(Component.text("✦ Chestplate proc ended", NamedTextColor.GRAY));
                    }
                }
            }.runTaskLater(plugin, 160L);
        }

        // ── Reaper's Scythe ───────────────────────────────────────────────────────
        ItemStack held = attacker.getInventory().getItemInMainHand();
        if (mi.isReaperScythe(held)) {
            // Base damage: same as netherite sword (8 damage = 4 hearts)
            // Hoe base is 1, so we set it to 8 here
            double baseDmg = 8.0;
            // +25% bonus if target below 30% HP
            double victimMaxHp = victim.getMaxHealth();
            if (victim.getHealth() / victimMaxHp < 0.30) {
                baseDmg *= 1.25;
                attacker.sendActionBar(Component.text("☽ Scythe: Execute bonus +25%!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            }
            event.setDamage(baseDmg);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  BOOTS – fall damage immunity + shockwave on 50+ block fall
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!mi.isShieldedBoots(player.getInventory().getBoots())) return;

        if (!player.isOnGround()) {
            // Track highest Y reached (start of fall)
            bootsFallStart.merge(player.getUniqueId(),
                    player.getLocation().getY(), Math::max);
        } else {
            Double startY = bootsFallStart.remove(player.getUniqueId());
            if (startY != null) {
                double fallen = startY - player.getLocation().getY();
                if (fallen >= 50) {
                    triggerBootsShockwave(player);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (mi.isShieldedBoots(player.getInventory().getBoots())) {
            event.setCancelled(true);
        }
    }

    private void triggerBootsShockwave(Player player) {
        Location loc = player.getLocation();
        World world  = player.getWorld();
        double radius = 8;

        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 5, 1, 0, 1);
        world.spawnParticle(Particle.GUST, loc.clone().add(0, 0.5, 0), 40, radius * 0.5, 1, radius * 0.5);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.7f);
        world.playSound(loc, Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.5f);

        for (Entity e : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (!(e instanceof Player victim) || victim.equals(player)) continue;
            victim.setHealth(Math.max(0, victim.getHealth() - 10)); // 5 hearts
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,   30, 255, false, false, false)); // 1.5s stun
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,  30, 0, false, false, false));
            Vector kb = victim.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(2);
            kb.setY(0.3);
            victim.setVelocity(kb);
            victim.sendActionBar(Component.text("⚡ Boots Shockwave: 5 hearts + stunned!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        }

        player.sendActionBar(Component.text("⚡ SHOCKWAVE from impact!", NamedTextColor.GOLD, TextDecoration.BOLD));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  WARDEN'S POCKET WATCH – Right-Click to Rewind
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onWatchClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!mi.isPocketWatch(held)) return;

        event.setCancelled(true);
        UUID id = player.getUniqueId();

        if (cd.isOnCooldown(id, "PocketWatch")) {
            long sec = cd.remainingSeconds(id, "PocketWatch");
            player.sendActionBar(Component.text("⏱ Pocket Watch: " + sec + "s cooldown", NamedTextColor.RED));
            return;
        }

        cd.set(id, "PocketWatch", 4 * 60 * 1000L);
        player.setCooldown(org.bukkit.Material.CLOCK, 4 * 60 * 20); // visual bar

        // Snapshot current state. The watch itself is now kept in the snapshot –
        // it's the SAME item being put back into the SAME slot on rewind, not an
        // extra copy, so there's no duplication. Nulling it previously caused the
        // watch to vanish entirely after rewinding.
        ItemStack[] invSnap = Arrays.stream(player.getInventory().getContents())
                .map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
        ItemStack[] armorSnap = Arrays.stream(player.getInventory().getArmorContents())
                .map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
        ItemStack offhandSnap = player.getInventory().getItemInOffHand().clone();
        RewindState snap = new RewindState(
                player.getLocation().clone(),
                player.getHealth(),
                invSnap, armorSnap, offhandSnap);
        rewindSnapshots.put(id, snap);

        player.sendActionBar(Component.text("⏱ Rewind recorded! Rewinding in 6s…", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.5f);

        // Countdown ticks
        new BukkitRunnable() {
            int countdown = 6;
            @Override public void run() {
                if (!player.isOnline()) { rewindSnapshots.remove(id); cancel(); return; }
                if (countdown-- > 0) {
                    player.sendActionBar(Component.text("⏱ Rewinding in " + countdown + "s…", NamedTextColor.AQUA));
                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 1.5f + countdown * 0.05f);
                } else {
                    cancel();
                    RewindState state = rewindSnapshots.remove(id);
                    if (state == null) return;

                    // Apply rewind
                    player.teleport(state.loc());
                    player.setHealth(Math.min(state.health(), player.getMaxHealth()));
                    player.getInventory().setContents(state.inventory());
                    player.getInventory().setArmorContents(state.armor());
                    player.getInventory().setItemInOffHand(state.offhand());
                    player.updateInventory();

                    player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0,1,0), 40, 0.4, 0.8, 0.4, 0.1);
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
                    player.sendActionBar(Component.text("⏱ REWOUND! State restored.", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  REAPER'S SCYTHE – Life Harvest on player kill
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;

        if (!mi.isReaperScythe(killer.getInventory().getItemInMainHand())) return;

        // Life Harvest: give essence directly, suppress drop
        killer.getInventory().addItem(si.buildLifeEssence());
        killer.sendActionBar(Component.text("☽ Life Harvest: Essence collected!", NamedTextColor.DARK_RED, TextDecoration.BOLD));

        // Remove the essence from drops so it doesn't also appear on the floor
        event.getDrops().removeIf(i -> si.isLifeEssence(i));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SHIELDED TOTEM – Soul Guard on death + Right-Click active
    // ══════════════════════════════════════════════════════════════════════════

    /** Intercepts death when totem is held to prevent life loss */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTotemDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Check main hand or offhand for Shielded Totem
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off  = player.getInventory().getItemInOffHand();
        boolean mainTotem = mi.isShieldedTotem(main);
        boolean offTotem  = mi.isShieldedTotem(off);
        if (!mainTotem && !offTotem) return;

        // Cancel life loss: override LifeSystem before it processes (we call it manually later without deducting)
        // We do this by giving back the life that handleDeath will subtract
        // IMPORTANT: This event fires BEFORE GlobalListener.onDeath (if priority is lower)
        // We mark a flag so GlobalListener knows to skip deduction
        totemProtected.add(player.getUniqueId());

        // Consume the totem
        if (mainTotem) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        }

        // Suppress death drops of essence (no essence drops either)
        event.getDrops().removeIf(i -> si.isLifeEssence(i));

        // Totem activation effect
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0,1,0), 50, 0.5, 1, 0.5, 0.1);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
        player.sendMessage(Component.text("[ShieldsSMP] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("Shielded Totem shattered – Soul Guard activated! No life lost.", NamedTextColor.YELLOW)));
    }

    /** Players whose death was saved by the totem this tick */
    private final Set<UUID> totemProtected = new HashSet<>();

    /** Testing tool: clear all mythical item cooldowns for this player */
    public void resetCooldowns(Player player) {
        UUID id = player.getUniqueId();
        cd.set(id, "PocketWatch", 0L);
        cd.set(id, "HelmetPulse", 0L);
    }

    public boolean consumeTotemProtection(UUID id) {
        return totemProtected.remove(id);
    }

    /** Right-click Shielded Totem: consume → buff allies */
    @EventHandler(priority = EventPriority.HIGH)
    public void onTotemClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!mi.isShieldedTotem(held)) return;

        event.setCancelled(true);

        // Consume totem
        held.subtract(1);

        Location loc = player.getLocation();
        World world = player.getWorld();

        // Buff self + TRUSTED allies within 6 blocks
        var trust = plugin.getTrustSystem();
        int buffed = 0;
        for (Entity e : world.getNearbyEntities(loc, 6, 6, 6)) {
            if (!(e instanceof Player p)) continue;
            if (!p.equals(player) && !trust.isTrusted(player.getUniqueId(), p.getUniqueId())) continue;
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,   200, 2, false, true, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 3, false, true, true));
            p.sendActionBar(Component.text("✦ Shielded Totem: Resistance III + Regen IV for 10s!", NamedTextColor.GOLD, TextDecoration.BOLD));
            buffed++;
        }

        world.spawnParticle(Particle.HEART, loc.clone().add(0,1,0), 20, 2, 1, 2);
        // Brief visual confirmation on totem material (it's consumed so bar is cosmetic)
        player.setCooldown(org.bukkit.Material.TOTEM_OF_UNDYING, 5 * 20);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0,1,0), 30, 1, 1, 1, 0.1);
        world.playSound(loc, Sound.ITEM_TOTEM_USE, 1f, 1.2f);
        player.sendActionBar(Component.text("✦ Totem consumed! Buffed " + buffed + " allies.", NamedTextColor.GOLD, TextDecoration.BOLD));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  LEGGINGS – Big Pockets (notify + minor carry bonus)
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onLeggingsEquip(PlayerJoinEvent event) {
        checkLeggings(event.getPlayer());
    }

    @EventHandler
    public void onArmorChange(PlayerItemHeldEvent event) {
        // Re-check on any inventory interaction - lightweight
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> { if (event.getPlayer().isOnline()) checkLeggings(event.getPlayer()); }, 1L);
    }

    private void checkLeggings(Player player) {
        if (mi.isShieldedLegs(player.getInventory().getLeggings())) {
            // Grant Haste I as a proxy "carry weight" bonus (true extra slots need NMS)
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, Integer.MAX_VALUE, 0, false, false, false));
        }
    }
}
