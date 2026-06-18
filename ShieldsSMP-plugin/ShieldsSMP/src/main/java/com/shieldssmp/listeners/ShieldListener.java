package com.shieldssmp.listeners;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.data.PlayerData;
import com.shieldssmp.items.ClassShieldBuilder;
import com.shieldssmp.items.SpecialItems;
import com.shieldssmp.systems.ClassManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.*;

public class ShieldListener implements Listener {

    private final ShieldsSMP      plugin;
    private final ClassManager    cm;
    private final ClassShieldBuilder builder;
    private final SpecialItems    si;

    private static final List<String> ALL_CLASSES = List.of(
            "Phantom", "Randomize", "Larp", "Life",
            "Gravity", "Combustion", "Boss", "Super",
            "Blood", "Null", "Blink", "Speed Demon", "Frost");

    public ShieldListener(ShieldsSMP plugin) {
        this.plugin  = plugin;
        this.cm      = plugin.getClassManager();
        this.builder = plugin.getClassShieldBuilder();
        this.si      = plugin.getSpecialItems();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SPAWN – give shield on first join & after respawn (keep same class)
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onFirstJoin(PlayerJoinEvent event) {
        new BukkitRunnable() {
            @Override public void run() {
                if (event.getPlayer().isOnline()) ensureHasShield(event.getPlayer());
            }
        }.runTaskLater(plugin, 5L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        new BukkitRunnable() {
            @Override public void run() {
                if (event.getPlayer().isOnline()) ensureHasShield(event.getPlayer());
            }
        }.runTaskLater(plugin, 5L);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ensureHasShield – main logic for giving/restoring shield
    // ══════════════════════════════════════════════════════════════════════════

    public void ensureHasShield(Player player) {
        PlayerData data = cm.getPlayerData(player.getUniqueId());

        // Player is at 0 lives → shield is broken, don't give one
        if (data.getLives() <= 0) {
            removeAllClassShields(player); // make sure nothing lingers
            return;
        }

        // Check if player already has a valid shield in inventory
        String existingClass = findClassInInventory(player);
        if (existingClass != null) {
            applyClass(player, existingClass, false);
            return;
        }

        // No shield found. Check if they have a saved class to restore.
        String savedClass = data.getClassName();
        String classToGive;

        if (savedClass != null) {
            // Restore their saved class (e.g. after death or reconnect)
            classToGive = savedClass;
        } else {
            // Brand new player – pick random class
            classToGive = randomClass(null);
        }

        giveShield(player, classToGive, true);
    }

    /** Give a shield of the specified class and apply the class */
    public void giveShield(Player player, String className, boolean announce) {
        ItemStack shield = builder.buildShield(className);
        builder.updateShieldDurability(shield, plugin.getLifeSystem().getLives(player.getUniqueId()));

        PlayerInventory inv = player.getInventory();
        if (inv.getItemInMainHand().getType().isAir()) {
            inv.setItemInMainHand(shield);
        } else if (inv.getItemInOffHand().getType().isAir()) {
            inv.setItemInOffHand(shield);
        } else {
            inv.addItem(shield);
        }

        applyClass(player, className, announce);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DEATH – keep same class, don't drop shield, handle 0-life breakage
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Never drop the class shield on death
        event.getDrops().removeIf(i -> builder.isClassShield(i));

        // Class and shield are restored in onRespawn → ensureHasShield
        // The saved class name in PlayerData persists so the same class is given back
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  0 LIVES → BREAK SHIELD (called by LifeSystem when lives reach 0)
    // ══════════════════════════════════════════════════════════════════════════

    public void breakShield(Player player) {
        removeAllClassShields(player);

        // Unequip class so passives/abilities stop
        var cls = cm.getPlayerClass(player.getUniqueId());
        if (cls != null) cls.onUnequip(player);
        cm.getPlayerData(player.getUniqueId()).setClassName(
                cm.getPlayerData(player.getUniqueId()).getClassName()); // keep class name in data

        player.sendMessage(
                Component.text("[ShieldsSMP] ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                         .append(Component.text("Your shield has broken! Use a ", NamedTextColor.RED))
                         .append(Component.text("Revive Book", NamedTextColor.GOLD, TextDecoration.BOLD))
                         .append(Component.text(" to restore it at 5 lives.", NamedTextColor.RED)));

        player.showTitle(Title.title(
                Component.text("⚔ SHIELD BROKEN", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.text("Use a Revive Book to restore your shield", NamedTextColor.RED),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(4), Duration.ofMillis(500))));

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, 2f, 0.8f);
        player.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, player.getLocation(), 3);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  REVIVE BOOK – right-click to restore shield at 5 lives
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onReviveBook(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!si.isReviveBook(held)) return;

        event.setCancelled(true);

        PlayerData data = cm.getPlayerData(player.getUniqueId());

        // Only works when lives == 0 (shield is broken)
        if (data.getLives() > 0) {
            player.sendActionBar(Component.text(
                    "✦ Your shield isn't broken! (Lives: " + data.getLives() + ")", NamedTextColor.YELLOW));
            return;
        }

        // Consume the book
        held.subtract(1);

        // Restore to 5 lives
        data.setLives(5);
        data.save(plugin);

        // Re-give shield with saved class (or random if none)
        String classToRestore = data.getClassName() != null ? data.getClassName() : randomClass(null);
        giveShield(player, classToRestore, true);

        // Update shield durability to reflect 5 lives
        plugin.getLifeSystem().updateShield(player, 5);
        plugin.getLifeSystem().updateTabName(player);
        plugin.getLifeSystem().sendLivesBar(player, 5);

        // FX
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0,1,0), 30, 0.5, 1, 0.5);
        player.sendMessage(
                Component.text("[ShieldsSMP] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                         .append(Component.text("Shield restored! You have 5 lives.", NamedTextColor.GREEN)));
        player.showTitle(Title.title(
                Component.text("✦ REVIVED!", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("Shield restored at 5 lives", NamedTextColor.GREEN),
                Title.Times.times(Duration.ofMillis(100), Duration.ofSeconds(3), Duration.ofMillis(400))));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DROP PREVENTION
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (builder.isClassShield(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("⛔ Cannot drop your class shield!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor  = event.getCursor();

        if (!builder.isClassShield(current) && !builder.isClassShield(cursor)) return;

        var clicked = event.getClickedInventory();
        if (clicked != null && clicked.equals(player.getInventory())) return; // allow within own inv

        event.setCancelled(true);
        player.sendActionBar(Component.text("⛔ Cannot move class shield out of inventory!", NamedTextColor.RED));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PREVENT DURABILITY LOSS – shields must not take real damage
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onShieldItemDamage(PlayerItemDamageEvent event) {
        if (builder.isClassShield(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShieldBreakFallback(PlayerItemBreakEvent event) {
        if (builder.isClassShield(event.getBrokenItem())) {
            Player player = event.getPlayer();
            int lives = plugin.getLifeSystem().getLives(player.getUniqueId());
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) plugin.getLifeSystem().updateShield(player, lives);
            }, 1L);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  REROLL TOTEM
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onReroll(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!builder.isRerollTotem(held)) return;

        event.setCancelled(true);

        // Can't reroll at 0 lives
        if (plugin.getLifeSystem().getLives(player.getUniqueId()) <= 0) {
            player.sendActionBar(Component.text("⛔ Revive your shield first before rerolling!", NamedTextColor.RED));
            return;
        }

        String oldClass = findClassInInventory(player);
        removeAllClassShields(player);

        String newClass = randomClass(oldClass);
        held.subtract(1);

        // Save new class to player data
        cm.getPlayerData(player.getUniqueId()).setClassName(newClass);
        cm.getPlayerData(player.getUniqueId()).save(plugin);

        giveShield(player, newClass, true);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5);
        player.sendMessage(
                Component.text("[ShieldsSMP] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                         .append(Component.text("Rerolled → ", NamedTextColor.GRAY))
                         .append(Component.text(newClass, NamedTextColor.YELLOW, TextDecoration.BOLD)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private void applyClass(Player player, String className, boolean announce) {
        cm.setClass(player, className, true, true); // silent = no double message

        if (announce) {
            player.showTitle(Title.title(
                    Component.text(className, getClassColor(className), TextDecoration.BOLD),
                    Component.text("Class assigned!", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
        }
    }

    public String findClassInInventory(Player player) {
        String cls = builder.getShieldClass(player.getInventory().getItemInMainHand());
        if (cls != null) return cls;
        cls = builder.getShieldClass(player.getInventory().getItemInOffHand());
        if (cls != null) return cls;
        for (ItemStack item : player.getInventory().getContents()) {
            cls = builder.getShieldClass(item);
            if (cls != null) return cls;
        }
        return null;
    }

    public void removeAllClassShields(Player player) {
        PlayerInventory inv = player.getInventory();
        if (builder.isClassShield(inv.getItemInMainHand())) inv.setItemInMainHand(new ItemStack(Material.AIR));
        if (builder.isClassShield(inv.getItemInOffHand())) inv.setItemInOffHand(new ItemStack(Material.AIR));
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++)
            if (builder.isClassShield(contents[i])) contents[i] = null;
        inv.setContents(contents);
    }

    private String randomClass(String exclude) {
        List<String> pool = new ArrayList<>(ALL_CLASSES);
        if (exclude != null) pool.remove(exclude);
        return pool.get(new Random().nextInt(pool.size()));
    }

    private NamedTextColor getClassColor(String c) {
        return switch (c) {
            case "Phantom"   -> NamedTextColor.DARK_PURPLE;
            case "Randomize" -> NamedTextColor.GOLD;
            case "Larp"      -> NamedTextColor.AQUA;
            case "Life"      -> NamedTextColor.GREEN;
            case "Gravity"   -> NamedTextColor.BLUE;
            case "Combustion" -> NamedTextColor.RED;
            case "Boss"      -> NamedTextColor.DARK_RED;
            case "Super"       -> NamedTextColor.YELLOW;
            case "Blood"       -> NamedTextColor.DARK_RED;
            case "Null"        -> NamedTextColor.DARK_GRAY;
            case "Blink"       -> NamedTextColor.DARK_AQUA;
            case "Speed Demon" -> NamedTextColor.GOLD;
            case "Frost"       -> NamedTextColor.AQUA;
            default            -> NamedTextColor.WHITE;
        };
    }
}
