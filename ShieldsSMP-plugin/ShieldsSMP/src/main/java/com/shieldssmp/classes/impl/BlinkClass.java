package com.shieldssmp.classes.impl;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class BlinkClass extends PlayerClass {

    private final Set<UUID> trueInvisActive = new HashSet<>();
    /** Stored armor/items during True Invis */
    private final Map<UUID, ItemStack[]> storedArmor   = new HashMap<>();
    private final Map<UUID, ItemStack>   storedMain     = new HashMap<>();
    private final Map<UUID, ItemStack>   storedOffhand  = new HashMap<>();
    /** Active clones */
    private final Map<UUID, Entity> clones = new HashMap<>();

    @Override public String getName()         { return "Blink"; }
    @Override public String getDescription()  { return "Displacement and deception"; }
    @Override public String getAbility1Name() { return "Blink Strike"; }
    @Override public String getAbility2Name() { return "True Invis"; }
    @Override public String getUltimateName() { return "Gemini Paradox"; }
    @Override public String getAbility1CooldownKey() { return "BlinkStrike"; }
    @Override public String getAbility2CooldownKey() { return "TrueInvis"; }
    @Override public String getUltimateCooldownKey() { return "GeminiParadox"; }

    // ── Passive: Phase Shift – 5% dodge ──────────────────────────────────────
    // Applied in GlobalListener via isDodge()
    private final Random rng = new Random();
    public boolean rollDodge() { return rng.nextInt(20) == 0; }

    // ── True Invis – take 2x damage while active ───────────────────────────────
    public boolean isTrueInvisActive(UUID id) { return trueInvisActive.contains(id); }

    @Override
    public void onTakeDamage(Player victim, Entity attacker, double damage) {
        // 2x damage during True Invis handled in GlobalListener
    }

    // ── Ability 1: Blink Strike – teleport 12 blocks forward, stun + 3 hearts ──
    @Override
    public void useAbility1(Player player) {
        if (!checkCooldown(player, "BlinkStrike", 2 * MIN)) return;
        startCooldown(player, "BlinkStrike", 2 * MIN);

        Location eye = player.getEyeLocation();
        Vector step  = eye.getDirection().normalize().multiply(0.5);
        Location dest = eye.clone();
        Location safe = player.getLocation().clone();

        for (int i = 0; i < 24; i++) {
            dest.add(step);
            if (!dest.getBlock().getType().isSolid()) {
                safe = dest.clone();
                safe.setYaw(player.getLocation().getYaw());
                safe.setPitch(player.getLocation().getPitch());
            }
        }

        player.teleport(safe);
        player.getWorld().spawnParticle(Particle.PORTAL, safe, 30, 0.5, 0.8, 0.5, 0.1);
        player.getWorld().playSound(safe, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 2f);

        // Stun + damage enemies at destination
        for (Entity e : player.getWorld().getNearbyEntities(safe, 2, 2, 2)) {
            if (!(e instanceof Player victim) || victim.equals(player)) continue;
            victim.damage(6, player); // 3 hearts
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,   60, 255)); // 3s stun
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,  60, 0));
            victim.sendActionBar(Component.text("⚡ Blink Strike: Stunned 3s!", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        }
        player.sendActionBar(Component.text("⚡ BLINK STRIKE!", NamedTextColor.AQUA, TextDecoration.BOLD));
    }

    // ── Ability 2: True Invis – hide all gear, 1.5x damage / 2x taken, 8s ────
    @Override
    public void useAbility2(Player player) {
        if (!checkCooldown(player, "TrueInvis", (long)(1.5 * MIN))) return;
        startCooldown(player, "TrueInvis", (long)(1.5 * MIN));

        UUID id = player.getUniqueId();
        trueInvisActive.add(id);

        // Strip armor/items
        storedArmor.put(id, player.getInventory().getArmorContents().clone());
        storedMain.put(id, player.getInventory().getItemInMainHand().clone());
        storedOffhand.put(id, player.getInventory().getItemInOffHand().clone());
        player.getInventory().setArmorContents(new ItemStack[]{null,null,null,null});
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));

        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 160, 0, false, false, false));
        player.sendActionBar(Component.text("⚡ TRUE INVIS – 1.5x dmg / 2x taken for 8s!", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));

        new BukkitRunnable() {
            @Override public void run() {
                trueInvisActive.remove(id);
                if (!player.isOnline()) { restoreGear(id, player); return; }
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                restoreGear(id, player);
                player.sendActionBar(Component.text("⚡ True Invis ended", NamedTextColor.GRAY));
            }
        }.runTaskLater(getPlugin(), 160L);
    }

    private void restoreGear(UUID id, Player player) {
        ItemStack[] armor = storedArmor.remove(id);
        if (armor != null) player.getInventory().setArmorContents(armor);
        ItemStack main = storedMain.remove(id);
        if (main != null && !main.getType().isAir()) player.getInventory().setItemInMainHand(main);
        ItemStack off = storedOffhand.remove(id);
        if (off != null && !off.getType().isAir()) player.getInventory().setItemInOffHand(off);
    }

    // ── Ultimate: Gemini Paradox ───────────────────────────────────────────────
    @Override
    public void useUltimate(Player player) {
        if (!checkCooldown(player, "GeminiParadox", 5 * MIN)) return;

        Player target = getTargetPlayer(player);
        if (target == null) { player.sendActionBar(Component.text("⚡ Look at a player!", NamedTextColor.RED)); return; }

        startCooldown(player, "GeminiParadox", 5 * MIN);

        Location swapLoc = player.getLocation().clone();
        UUID id = player.getUniqueId();

        // Spawn armor-stand clone at player's old location
        Entity clone = player.getWorld().spawnEntity(swapLoc, EntityType.ARMOR_STAND);
        if (clone instanceof org.bukkit.entity.ArmorStand stand) {
            stand.setCustomName("§b" + player.getName());
            stand.setCustomNameVisible(true);
            stand.setVisible(true);
            stand.setGravity(false);
            // Copy armor appearance
            stand.getEquipment().setHelmet(player.getInventory().getHelmet());
            stand.getEquipment().setChestplate(player.getInventory().getChestplate());
            stand.getEquipment().setLeggings(player.getInventory().getLeggings());
            stand.getEquipment().setBoots(player.getInventory().getBoots());
            stand.getEquipment().setItemInMainHand(player.getInventory().getItemInMainHand());
        }
        clones.put(id, clone);

        // Swap player to a spot 15 blocks away to the side
        Vector right = player.getLocation().getDirection().crossProduct(new Vector(0,1,0)).normalize().multiply(15);
        Location swapTo = player.getLocation().clone().add(right);
        swapTo.setYaw(player.getLocation().getYaw());
        swapTo.setPitch(player.getLocation().getPitch());
        player.teleport(swapTo);

        // Make player invisible
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 120, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 2)); // Speed III

        player.sendActionBar(Component.text("⚡ GEMINI PARADOX – clone active, Speed III for 6s!", NamedTextColor.AQUA, TextDecoration.BOLD));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 1.8f);

        new BukkitRunnable() {
            @Override public void run() {
                Entity c = clones.remove(id);
                if (c != null && c.isValid()) c.remove();
                if (player.isOnline()) {
                    player.removePotionEffect(PotionEffectType.INVISIBILITY);
                    player.removePotionEffect(PotionEffectType.SPEED);
                    player.sendActionBar(Component.text("⚡ Gemini Paradox ended", NamedTextColor.GRAY));
                }
            }
        }.runTaskLater(getPlugin(), 120L);
    }

    private Player getTargetPlayer(Player player) {
        Player closest = null; double bestDot = 0.7;
        var dir = player.getLocation().getDirection().normalize();
        for (Entity e : player.getWorld().getNearbyEntities(player.getLocation(), 20, 20, 20)) {
            if (!(e instanceof Player p) || p.equals(player)) continue;
            double dot = dir.dot(e.getLocation().toVector().subtract(player.getLocation().toVector()).normalize());
            if (dot > bestDot) { bestDot = dot; closest = p; }
        }
        return closest;
    }

    @Override
    public void onDeath(Player player) {
        super.onDeath(player);
        UUID id = player.getUniqueId();
        trueInvisActive.remove(id);
        restoreGear(id, player);
        Entity c = clones.remove(id);
        if (c != null && c.isValid()) c.remove();
    }

    private org.bukkit.plugin.java.JavaPlugin getPlugin() { return ShieldsSMP.getInstance(); }
}
