package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;

public class GravityClass extends PlayerClass {

    private static final double HORIZON_RADIUS = 15.0;
    private final Map<UUID, Location> activeHorizons = new HashMap<>();
    private final Set<UUID> frozenInHorizon = new HashSet<>();

    /** Players currently suspended mid-air waiting for direction input */
    private final Map<UUID, Player> suspended = new HashMap<>(); // casterUUID → target

    @Override public String getName()         { return "Gravity"; }
    @Override public String getDescription()  { return "Bend the laws of physics"; }
    @Override public String getAbility1Name() { return "Vector Shift"; }
    @Override public String getAbility2Name() { return "Orbital Slam"; }
    @Override public String getUltimateName() { return "Event Horizon"; }
    @Override public String getAbility1CooldownKey() { return "VectorShift"; }
    @Override public String getAbility2CooldownKey() { return "OrbitalSlam"; }
    @Override public String getUltimateCooldownKey() { return "EventHorizon"; }

    @Override
    public void tickPassive(Player player) {
        if (player.isSneaking())
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 30, 5, false, false, false));
        else
            player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }

    // ── Ability 1: Vector Shift ────────────────────────────────────────────────
    // Step 1: Left-click fires ability → nearest enemy is SUSPENDED in air (Levitation)
    // Step 2: While suspended (3s), caster looks in desired direction and Left-clicks again → launches that way
    // If no second click within 3s → auto-launches UP
    @Override
    public void useAbility1(Player player) {
        UUID id = player.getUniqueId();

        // STEP 2: caster already has a suspended target → launch in look direction
        if (suspended.containsKey(id)) {
            Player target = suspended.remove(id);
            if (target != null && target.isOnline()) {
                target.removePotionEffect(PotionEffectType.LEVITATION);
                Vector launch = player.getLocation().getDirection().normalize().multiply(4.5);
                launch.setY(Math.max(launch.getY(), 0.5));
                target.setVelocity(launch);
                player.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().add(0,1,0), 20, 0.3, 0.5, 0.3);
                player.sendActionBar(Component.text("🌀 LAUNCHED " + target.getName() + "!", NamedTextColor.AQUA, TextDecoration.BOLD));
                target.sendActionBar(Component.text("🌀 Launched!", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
            }
            return;
        }

        if (!checkCooldown(player, "VectorShift", 2 * MIN)) return;

        Player target = getNearestPlayer(player, 20);
        if (target == null) { player.sendActionBar(Component.text("⚡ No player within 20 blocks!", NamedTextColor.RED)); return; }

        startCooldown(player, "VectorShift", 2 * MIN);

        // STEP 1: Suspend target in air
        target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 1, false, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,   60, 255, false, false, false));
        suspended.put(id, target);

        player.sendActionBar(Component.text("🌀 " + target.getName() + " suspended! Look + use ability again to launch!", NamedTextColor.AQUA, TextDecoration.BOLD));
        target.sendActionBar(Component.text("🌀 Suspended! Brace yourself...", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        showRing(target.getLocation(), 2, Particle.CRIT);

        // Auto-launch UP after 3s if no second click
        new BukkitRunnable() {
            @Override public void run() {
                if (!suspended.containsKey(id)) return; // already launched
                Player t = suspended.remove(id);
                if (t != null && t.isOnline()) {
                    t.removePotionEffect(PotionEffectType.LEVITATION);
                    t.removePotionEffect(PotionEffectType.SLOWNESS);
                    t.setVelocity(new Vector(0, 5, 0));
                    player.sendActionBar(Component.text("🌀 Auto-launched UP!", NamedTextColor.GRAY));
                }
            }
        }.runTaskLater(getPlugin(), 60L);
    }

    // ── Ability 2: Orbital Slam ────────────────────────────────────────────────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "OrbitalSlam", 2 * MIN)) return;
        Player target = getNearestPlayer(player, 25);
        if (target == null) { player.sendActionBar(Component.text("⚡ No player within 25 blocks!", NamedTextColor.RED)); return; }
        startCooldown(player, "OrbitalSlam", 2 * MIN);
        showRing(target.getLocation(), 5, Particle.CRIT);
        target.setVelocity(new Vector(0, 5, 0));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.5f, 0.5f);
        player.sendActionBar(Component.text("☄ Orbital Slam: " + target.getName() + " launched!", NamedTextColor.RED, TextDecoration.BOLD));
        target.showTitle(Title.title(
                Component.text("☄ ORBITAL SLAM!", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.text("Use abilities to survive!", NamedTextColor.RED),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2500), Duration.ofMillis(300))));
        new BukkitRunnable() {
            @Override public void run() {
                if (!target.isOnline()) return;
                target.setVelocity(new Vector(0, -7, 0));
            }
        }.runTaskLater(getPlugin(), 30L);
    }

    // ── Ultimate: Event Horizon ────────────────────────────────────────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "EventHorizon", 5 * MIN)) return;
        startCooldown(player, "EventHorizon", 5 * MIN);
        Location center = player.getLocation().clone();
        World world = player.getWorld();
        UUID casterId = player.getUniqueId();
        activeHorizons.put(casterId, center);

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (++t > 50 || !activeHorizons.containsKey(casterId)) { cancel(); return; }
                for (int i = 0; i < 48; i++) {
                    double a = (2*Math.PI/48)*i + t*0.1;
                    world.spawnParticle(Particle.DUST, center.getX()+HORIZON_RADIUS*Math.cos(a), center.getY()+0.1, center.getZ()+HORIZON_RADIUS*Math.sin(a), 1, new Particle.DustOptions(Color.BLUE, 1.5f));
                }
                for (int i = 0; i < 32; i++) {
                    double a = (2*Math.PI/32)*i - t*0.1;
                    world.spawnParticle(Particle.DUST, center.getX()+HORIZON_RADIUS*Math.cos(a), center.getY()+3, center.getZ()+HORIZON_RADIUS*Math.sin(a), 1, new Particle.DustOptions(Color.AQUA, 1.2f));
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 4L);

        List<UUID> frozenList = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(center, HORIZON_RADIUS, HORIZON_RADIUS, HORIZON_RADIUS)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            UUID pid = p.getUniqueId();
            frozenInHorizon.add(pid); frozenList.add(pid);
            p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 200, 1, false, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 255, false, false, false));
            if (!p.getGameMode().equals(GameMode.CREATIVE)) { p.setFlying(false); p.setAllowFlight(false); }
            p.sendActionBar(Component.text("🌀 Caught in Event Horizon!", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        }
        player.setAllowFlight(true); player.setFlying(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1, false, false, false));
        player.sendActionBar(Component.text("🌀 EVENT HORIZON – 10s!", NamedTextColor.AQUA, TextDecoration.BOLD));
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.4f);

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!player.isOnline() || ++t > 100) { cancel(); return; }
                if (player.getLocation().distance(center) > HORIZON_RADIUS)
                    player.setVelocity(center.toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.5));
            }
        }.runTaskTimer(getPlugin(), 0L, 2L);

        new BukkitRunnable() {
            @Override public void run() {
                activeHorizons.remove(casterId);
                frozenList.forEach(pid -> { frozenInHorizon.remove(pid); Player p = Bukkit.getPlayer(pid); if (p!=null){p.removePotionEffect(PotionEffectType.LEVITATION);p.removePotionEffect(PotionEffectType.SLOWNESS);} });
                if (player.isOnline()) { player.removePotionEffect(PotionEffectType.SPEED); if (!player.getGameMode().equals(GameMode.CREATIVE)){player.setAllowFlight(false);player.setFlying(false);} }
            }
        }.runTaskLater(getPlugin(), 200L);
    }

    private Player getNearestPlayer(Player p, double r) { Player n=null; double b=Double.MAX_VALUE; for(Entity e:p.getWorld().getNearbyEntities(p.getLocation(),r,r,r)){if(!(e instanceof Player t)||t.equals(p))continue;double d=p.getLocation().distanceSquared(t.getLocation());if(d<b){b=d;n=t;}}return n; }
    private void showRing(Location c,double r,Particle pa){World w=c.getWorld();if(w==null)return;new BukkitRunnable(){int t=0;@Override public void run(){if(++t>15){cancel();return;}for(int i=0;i<24;i++){double a=(2*Math.PI/24)*i;w.spawnParticle(pa,c.getX()+r*Math.cos(a),c.getY()+0.05,c.getZ()+r*Math.sin(a),1,0,0,0,0);}}}.runTaskTimer(getPlugin(),0L,2L);}
    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
