package com.shieldssmp.listeners;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.items.ClassShieldBuilder;
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

    private final ShieldsSMP plugin;
    private final ClassManager cm;
    private final ClassShieldBuilder builder;

    /** All available class names to pick from */
    private static final List<String> ALL_CLASSES = List.of(
            "Phantom", "Randomize", "Larp", "Life",
            "Gravity", "Terrorist", "Boss", "Super"
    );

    /** Players who already received their first shield this session */
    private final Set<UUID> initialised = new HashSet<>();

    public ShieldListener(ShieldsSMP plugin) {
        this.plugin  = plugin;
        this.cm      = plugin.getClassManager();
        this.builder = plugin.getClassShieldBuilder();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SPAWN – give shield on first join & after respawn
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onFirstJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Slight delay so inventory is ready
        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) return;
                ensureHasShield(player);
            }
        }.runTaskLater(plugin, 5L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) return;
                ensureHasShield(player);
            }
        }.runTaskLater(plugin, 5L);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Ensure the player has exactly one class shield and the right class active
    // ══════════════════════════════════════════════════════════════════════════

    public void ensureHasShield(Player player) {
        PlayerInventory inv = player.getInventory();

        // Check if they already have a class shield somewhere
        String existingClass = findClassInInventory(player);

        if (existingClass != null) {
            // Re-apply the class (in case of reload/restart)
            applyClass(player, existingClass, false);
            return;
        }

        // No shield found – give a random one
        String chosen = randomClass(null);
        ItemStack shield = builder.buildShield(chosen);

        // Put in offhand; fall back to inventory
        if (inv.getItemInOffHand().getType().isAir()) {
            inv.setItemInOffHand(shield);
        } else {
            inv.addItem(shield);
        }

        applyClass(player, chosen, true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DROP PREVENTION – can't drop, throw, or move class shield out
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (builder.isClassShield(dropped)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(
                    Component.text("⛔ You cannot drop your class shield!", NamedTextColor.RED));
        }
    }

    /** Prevent moving shield out of inventory via click */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check both current item and cursor
        if (builder.isClassShield(event.getCurrentItem()) ||
            builder.isClassShield(event.getCursor())) {

            // Allow moving within own inventory, but not to another inventory (chest, etc.)
            if (event.getClickedInventory() == null) {
                event.setCancelled(true);
                return;
            }
            // If they're trying to move it to an external inventory, cancel
            if (event.getView().getTopInventory() != player.getInventory()
                    && event.getClickedInventory() == player.getInventory()) {
                event.setCancelled(true);
                player.sendActionBar(Component.text("⛔ You cannot move your class shield!", NamedTextColor.RED));
            }
        }
    }

    /** Keep shield in inventory on death – don't drop it */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        List<ItemStack> drops = event.getDrops();
        ItemStack shieldDrop = null;
        for (ItemStack item : drops) {
            if (builder.isClassShield(item)) { shieldDrop = item; break; }
        }
        if (shieldDrop != null) {
            drops.remove(shieldDrop);
            // Re-give after respawn is handled by onRespawn
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  REROLL TOTEM – right-click to get a new random class
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onReroll(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!builder.isRerollTotem(held)) return;

        event.setCancelled(true);

        // Find and remove old class shield
        String oldClass = findClassInInventory(player);
        removeClassShield(player);

        // Pick a new class (different from old)
        String newClass = randomClass(oldClass);
        ItemStack newShield = builder.buildShield(newClass);

        // Place in offhand if free, else inventory
        if (player.getInventory().getItemInOffHand().getType().isAir()) {
            player.getInventory().setItemInOffHand(newShield);
        } else {
            player.getInventory().addItem(newShield);
        }

        // Consume totem
        held.subtract(1);

        applyClass(player, newClass, true);

        // Fancy FX
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5);

        player.sendMessage(
                Component.text("[ShieldsSMP] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                         .append(Component.text("Rerolled! New class: ", NamedTextColor.GRAY))
                         .append(Component.text(newClass, NamedTextColor.YELLOW, TextDecoration.BOLD)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private void applyClass(Player player, String className, boolean announce) {
        cm.setClass(player, className, true, true);

        if (announce) {
            player.showTitle(Title.title(
                    Component.text(className, getClassColor(className), TextDecoration.BOLD),
                    Component.text("Your class has been assigned!", NamedTextColor.GRAY),
                    Title.Times.times(
                            Duration.ofMillis(200),
                            Duration.ofSeconds(3),
                            Duration.ofMillis(500))));
        }
    }

    /** Scan inventory + offhand for a class shield and return its class name */
    private String findClassInInventory(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        String cls = builder.getShieldClass(offhand);
        if (cls != null) return cls;

        for (ItemStack item : player.getInventory().getContents()) {
            cls = builder.getShieldClass(item);
            if (cls != null) return cls;
        }
        return null;
    }

    /** Remove all class shields from the player's inventory */
    private void removeClassShield(Player player) {
        PlayerInventory inv = player.getInventory();

        if (builder.isClassShield(inv.getItemInOffHand())) {
            inv.setItemInOffHand(new ItemStack(Material.AIR));
        }

        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (builder.isClassShield(contents[i])) {
                contents[i] = new ItemStack(Material.AIR);
            }
        }
        inv.setContents(contents);
    }

    private String randomClass(String exclude) {
        List<String> pool = new ArrayList<>(ALL_CLASSES);
        if (exclude != null) pool.remove(exclude);
        return pool.get(new Random().nextInt(pool.size()));
    }

    private NamedTextColor getClassColor(String className) {
        return switch (className) {
            case "Phantom"   -> NamedTextColor.DARK_PURPLE;
            case "Randomize" -> NamedTextColor.GOLD;
            case "Larp"      -> NamedTextColor.AQUA;
            case "Life"      -> NamedTextColor.GREEN;
            case "Gravity"   -> NamedTextColor.BLUE;
            case "Terrorist" -> NamedTextColor.RED;
            case "Boss"      -> NamedTextColor.DARK_RED;
            case "Super"     -> NamedTextColor.YELLOW;
            default          -> NamedTextColor.WHITE;
        };
    }
}
