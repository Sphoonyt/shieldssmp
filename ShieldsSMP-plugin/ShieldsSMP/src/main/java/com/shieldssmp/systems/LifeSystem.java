package com.shieldssmp.systems;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.data.PlayerData;
import com.shieldssmp.items.ClassShieldBuilder;
import com.shieldssmp.items.SpecialItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;

public class LifeSystem {

    private final ShieldsSMP      plugin;
    private final SpecialItems    items;
    private final ClassShieldBuilder shieldBuilder;

    public LifeSystem(ShieldsSMP plugin, SpecialItems items) {
        this.plugin        = plugin;
        this.items         = items;
        this.shieldBuilder = plugin.getClassShieldBuilder();
    }

    // ── On death: lose 1 life, update shield, drop essence ───────────────────

    public void handleDeath(Player player) {
        PlayerData data = plugin.getClassManager().getPlayerData(player.getUniqueId());

        if (data.getLives() > 0) {
            data.setLives(data.getLives() - 1);
            data.save(plugin);
        }

        updateShield(player, data.getLives());
        sendLivesBar(player, data.getLives());
        updateTabName(player);
        dropEssence(player.getLocation(), player.getWorld());

        if (data.getLives() <= 0) {
            player.sendMessage(
                Component.text("[ShieldsSMP] ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                         .append(Component.text("You have 0 lives — abilities and passives disabled!", NamedTextColor.RED)));
        }
    }

    // ── Add a life (from essence pickup) ─────────────────────────────────────

    public boolean tryGiveLife(Player player, ItemStack item) {
        if (!items.isLifeEssence(item)) return false;

        PlayerData data = plugin.getClassManager().getPlayerData(player.getUniqueId());
        if (data.getLives() >= PlayerData.MAX_LIVES) {
            player.sendActionBar(Component.text("✦ Lives already at maximum (" + PlayerData.MAX_LIVES + ")!", NamedTextColor.YELLOW));
            return false;
        }

        data.setLives(data.getLives() + 1);
        data.save(plugin);

        updateShield(player, data.getLives());
        sendLivesBar(player, data.getLives());
        updateTabName(player);
        player.sendActionBar(Component.text("✦ +1 Life! Total: " + data.getLives() + "/" + PlayerData.MAX_LIVES, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        return true;
    }

    // ── Withdraw a life (admin command or mechanic) ────────────────────────────

    public boolean withdrawLife(Player player) {
        PlayerData data = plugin.getClassManager().getPlayerData(player.getUniqueId());
        if (data.getLives() <= 0) {
            player.sendActionBar(Component.text("✦ No lives to withdraw!", NamedTextColor.RED));
            return false;
        }

        data.setLives(data.getLives() - 1);
        data.save(plugin);

        updateShield(player, data.getLives());
        sendLivesBar(player, data.getLives());
        updateTabName(player);

        // Give a Life Essence into their inventory
        player.getInventory().addItem(items.buildLifeEssence());
        player.sendActionBar(Component.text("✦ Withdrew 1 life. Lives: " + data.getLives() + "/" + PlayerData.MAX_LIVES, NamedTextColor.YELLOW, TextDecoration.BOLD));
        return true;
    }

    // ── Upgrade Core use ──────────────────────────────────────────────────────

    public boolean tryUpgrade(Player player, ItemStack item) {
        if (!items.isUpgradeCore(item)) return false;

        ClassManager cm = plugin.getClassManager();
        PlayerData data = cm.getPlayerData(player.getUniqueId());

        if (!data.hasClass()) {
            player.sendActionBar(Component.text("❌ You need a class first!", NamedTextColor.RED));
            return false;
        }
        if (data.getLevel() >= 2) {
            player.sendActionBar(Component.text("✅ Already Level 2!", NamedTextColor.GREEN));
            return false;
        }

        cm.upgradeLevel(player);
        player.sendMessage(
            Component.text("[ShieldsSMP] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                     .append(Component.text("Upgraded to ", NamedTextColor.YELLOW))
                     .append(Component.text("Level 2", NamedTextColor.GOLD, TextDecoration.BOLD))
                     .append(Component.text("! Abilities unlocked.", NamedTextColor.YELLOW)));
        return true;
    }

    // ── Sync shield durability to lives ───────────────────────────────────────

    public void updateShield(Player player, int lives) {
        PlayerInventory inv = player.getInventory();

        // Check offhand first, then main inventory
        if (shieldBuilder.isClassShield(inv.getItemInOffHand())) {
            shieldBuilder.updateShieldDurability(inv.getItemInOffHand(), lives);
            player.updateInventory();
            return;
        }
        for (ItemStack item : inv.getContents()) {
            if (shieldBuilder.isClassShield(item)) {
                shieldBuilder.updateShieldDurability(item, lives);
                player.updateInventory();
                return;
            }
        }
    }

    // ── Sync durability on join (in case lives changed offline) ──────────────

    public void syncOnJoin(Player player) {
        int lives = getLives(player.getUniqueId());
        updateShield(player, lives);
        sendLivesBar(player, lives);
        updateTabName(player);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void dropEssence(Location loc, World world) {
        Item dropped = world.dropItemNaturally(loc, items.buildLifeEssence());
        dropped.setPickupDelay(40);
    }

    public void sendLivesBar(Player player, int lives) {
        StringBuilder sb = new StringBuilder("Lives: ");
        for (int i = 0; i < PlayerData.MAX_LIVES; i++) {
            sb.append(i < lives ? "❤ " : "🖤 ");
        }
        NamedTextColor color = lives <= 0 ? NamedTextColor.DARK_RED
                             : lives <= 3 ? NamedTextColor.RED
                             : NamedTextColor.LIGHT_PURPLE;
        player.sendActionBar(Component.text(sb.toString().trim(), color, TextDecoration.BOLD));
    }

    public int getLives(UUID id) {
        return plugin.getClassManager().getPlayerData(id).getLives();
    }

    public boolean hasLives(UUID id) {
        return getLives(id) > 0;
    }

    /** Update this player's tab-list display name to show lives */
    public void updateTabName(Player player) {
        int lives = getLives(player.getUniqueId());
        net.kyori.adventure.text.format.NamedTextColor heartColor =
                lives <= 0 ? net.kyori.adventure.text.format.NamedTextColor.DARK_RED
              : lives <= 3 ? net.kyori.adventure.text.format.NamedTextColor.RED
              : net.kyori.adventure.text.format.NamedTextColor.GREEN;

        String hearts = "❤".repeat(Math.max(0, lives)) + (lives < PlayerData.MAX_LIVES ? "🖤".repeat(PlayerData.MAX_LIVES - lives) : "");
        Component tabName = Component.text(player.getName(), net.kyori.adventure.text.format.NamedTextColor.WHITE)
                .append(Component.text(" [", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY))
                .append(Component.text(String.valueOf(lives), heartColor,
                        net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(Component.text("/" + PlayerData.MAX_LIVES + "]", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
        player.playerListName(tabName);
    }
}
