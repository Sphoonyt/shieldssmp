package com.shieldssmp.systems;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.data.PlayerData;
import com.shieldssmp.items.SpecialItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class LifeSystem {

    private final ShieldsSMP plugin;
    private final SpecialItems items;

    public LifeSystem(ShieldsSMP plugin, SpecialItems items) {
        this.plugin = plugin;
        this.items  = items;
    }

    // ── On death: remove 1 life, drop Life Essence ───────────────────────────

    public void handleDeath(Player player) {
        PlayerData data = plugin.getClassManager().getPlayerData(player.getUniqueId());

        int lives = data.getLives();
        if (lives > 0) {
            data.setLives(lives - 1);
            data.save(plugin);
        }

        sendLivesBar(player, data.getLives());

        // Drop essence at death location
        dropEssence(player.getLocation(), player.getWorld());

        if (data.getLives() <= 0) {
            player.sendMessage(
                    Component.text("[ShieldsSMP] ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                             .append(Component.text("You have no lives left! You are eliminated.", NamedTextColor.RED)));
        }
    }

    // ── Pickup Life Essence (called when player right-clicks or collides) ─────

    public boolean tryGiveLife(Player player, ItemStack item) {
        if (!items.isLifeEssence(item)) return false;

        PlayerData data = plugin.getClassManager().getPlayerData(player.getUniqueId());
        data.setLives(data.getLives() + 1);
        data.save(plugin);

        sendLivesBar(player, data.getLives());
        player.sendActionBar(Component.text("✦ +1 Life! Total: " + data.getLives(), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        return true;
    }

    // ── Upgrade Core use ──────────────────────────────────────────────────────

    public boolean tryUpgrade(Player player, ItemStack item) {
        if (!items.isUpgradeCore(item)) return false;

        ClassManager cm = plugin.getClassManager();
        PlayerData data = cm.getPlayerData(player.getUniqueId());

        if (!data.hasClass()) {
            player.sendActionBar(Component.text("❌ You need a class first! Use /class <name>", NamedTextColor.RED));
            return false;
        }

        if (data.getLevel() >= 2) {
            player.sendActionBar(Component.text("✅ Already Level 2!", NamedTextColor.GREEN));
            return false;
        }

        cm.upgradeLevel(player);
        player.sendMessage(
                Component.text("[ShieldsSMP] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                         .append(Component.text("Class upgraded to ", NamedTextColor.YELLOW))
                         .append(Component.text("Level 2", NamedTextColor.GOLD, TextDecoration.BOLD))
                         .append(Component.text("! Abilities and Ultimate unlocked.", NamedTextColor.YELLOW)));
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void dropEssence(Location loc, World world) {
        ItemStack essence = items.buildLifeEssence();
        Item dropped = world.dropItemNaturally(loc, essence);
        dropped.setPickupDelay(40); // 2s before pickup
    }

    public void sendLivesBar(Player player, int lives) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PlayerData.MAX_LIVES; i++) {
            sb.append(i < lives ? "❤ " : "🖤 ");
        }
        player.sendActionBar(
                Component.text("Lives: ", NamedTextColor.RED, TextDecoration.BOLD)
                         .append(Component.text(sb.toString().trim(), NamedTextColor.LIGHT_PURPLE)));
    }

    public int getLives(UUID id) {
        return plugin.getClassManager().getPlayerData(id).getLives();
    }
}
