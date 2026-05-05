package com.shieldssmp.commands;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import com.shieldssmp.items.SpecialItems;
import com.shieldssmp.systems.ClassManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ClassCommands implements CommandExecutor, TabCompleter {

    private final ShieldsSMP plugin;
    private final ClassManager cm;
    private final SpecialItems si;

    public ClassCommands(ShieldsSMP plugin) {
        this.plugin = plugin;
        this.cm     = plugin.getClassManager();
        this.si     = plugin.getSpecialItems();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase();

        return switch (name) {
            case "ability"  -> handleAbility(sender, args);
            case "ultimate" -> handleUltimate(sender);
            case "class"    -> handleClass(sender, args);
            case "lives"    -> handleLives(sender, args);
            case "givenullifier" -> handleGiveNullifier(sender, args);
            case "giveessence"   -> handleGiveEssence(sender, args);
            case "giveupgrade"   -> handleGiveUpgrade(sender, args);
            default -> false;
        };
    }

    // ── /ability 1 | /ability 2 ───────────────────────────────────────────────

    private boolean handleAbility(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use abilities."); return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /ability 1 or /ability 2", NamedTextColor.RED));
            return true;
        }
        switch (args[0]) {
            case "1" -> cm.useAbility1(player);
            case "2" -> cm.useAbility2(player);
            default  -> player.sendMessage(Component.text("Usage: /ability 1 or /ability 2", NamedTextColor.RED));
        }
        return true;
    }

    // ── /ultimate ─────────────────────────────────────────────────────────────

    private boolean handleUltimate(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use ultimates."); return true;
        }
        cm.useUltimate(player);
        return true;
    }

    // ── /class [name] | /class info ───────────────────────────────────────────

    private boolean handleClass(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /class."); return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            player.sendMessage(Component.text("═══ Available Classes ═══", NamedTextColor.GOLD, TextDecoration.BOLD));
            for (PlayerClass cls : cm.getAllClasses()) {
                player.sendMessage(Component.text("  • ", NamedTextColor.YELLOW)
                        .append(Component.text(cls.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text(" – " + cls.getDescription(), NamedTextColor.GRAY)));
            }
            player.sendMessage(Component.text("Use /class <name> to select a class.", NamedTextColor.GRAY));
            return true;
        }

        if (args[0].equalsIgnoreCase("info")) {
            PlayerClass cls = cm.getPlayerClass(player.getUniqueId());
            if (cls == null) {
                player.sendMessage(Component.text("You have no class. Use /class <name>.", NamedTextColor.RED));
                return true;
            }
            int level = cm.getLevel(player.getUniqueId());
            player.sendMessage(Component.text("═══ " + cls.getName() + " (Lv" + level + ") ═══", NamedTextColor.GOLD, TextDecoration.BOLD));
            player.sendMessage(Component.text("  Passive: ", NamedTextColor.AQUA).append(Component.text("Always active", NamedTextColor.GRAY)));
            if (level >= 2) {
                player.sendMessage(Component.text("  /ability 1  ", NamedTextColor.GREEN).append(Component.text(cls.getAbility1Name(), NamedTextColor.WHITE)));
                player.sendMessage(Component.text("  /ability 2  ", NamedTextColor.GREEN).append(Component.text(cls.getAbility2Name(), NamedTextColor.WHITE)));
                player.sendMessage(Component.text("  /ultimate   ", NamedTextColor.RED).append(Component.text(cls.getUltimateName(), NamedTextColor.WHITE)));
            } else {
                player.sendMessage(Component.text("  Use a [Class Upgrade Core] to unlock abilities!", NamedTextColor.YELLOW));
            }
            return true;
        }

        // Set class
        if (!sender.hasPermission("shieldssmp.class")) {
            player.sendMessage(Component.text("You don't have permission to change classes.", NamedTextColor.RED));
            return true;
        }
        boolean ok = cm.setClass(player, args[0], true);
        if (!ok) {
            player.sendMessage(Component.text("Unknown class: " + args[0] + ". Use /class list.", NamedTextColor.RED));
        }
        return true;
    }

    // ── /lives [player] ───────────────────────────────────────────────────────

    private boolean handleLives(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Specify a player: /lives <player>"); return true;
            }
            int lives = plugin.getLifeSystem().getLives(player.getUniqueId());
            player.sendMessage(Component.text("You have " + lives + " lives remaining.", NamedTextColor.LIGHT_PURPLE));
            return true;
        }

        if (!sender.hasPermission("shieldssmp.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED)); return true;
        }

        // Admin: /lives <player> [set|add] [amount]
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return true;
        }
        if (args.length == 3) {
            try {
                int amount = Integer.parseInt(args[2]);
                var data = cm.getPlayerData(target.getUniqueId());
                if (args[1].equalsIgnoreCase("set")) data.setLives(amount);
                else if (args[1].equalsIgnoreCase("add")) data.setLives(data.getLives() + amount);
                data.save(plugin);
                sender.sendMessage(Component.text("Set " + target.getName() + "'s lives to " + data.getLives(), NamedTextColor.GREEN));
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid number.", NamedTextColor.RED));
            }
        } else {
            int lives = plugin.getLifeSystem().getLives(target.getUniqueId());
            sender.sendMessage(Component.text(target.getName() + " has " + lives + " lives.", NamedTextColor.LIGHT_PURPLE));
        }
        return true;
    }

    // ── Admin give commands ────────────────────────────────────────────────────

    private boolean handleGiveNullifier(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shieldssmp.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED)); return true;
        }
        Player target = args.length > 0 ? Bukkit.getPlayerExact(args[0])
                : (sender instanceof Player p ? p : null);
        if (target == null) { sender.sendMessage("Player not found."); return true; }
        target.getInventory().addItem(si.buildNullifierAxe());
        sender.sendMessage(Component.text("Gave Nullifier Axe to " + target.getName(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleGiveEssence(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shieldssmp.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED)); return true;
        }
        Player target = args.length > 0 ? Bukkit.getPlayerExact(args[0])
                : (sender instanceof Player p ? p : null);
        int amount = args.length > 1 ? parseInt(args[1], 1) : 1;
        if (target == null) { sender.sendMessage("Player not found."); return true; }
        for (int i = 0; i < amount; i++) target.getInventory().addItem(si.buildLifeEssence());
        sender.sendMessage(Component.text("Gave " + amount + "x Life Essence to " + target.getName(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleGiveUpgrade(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shieldssmp.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED)); return true;
        }
        Player target = args.length > 0 ? Bukkit.getPlayerExact(args[0])
                : (sender instanceof Player p ? p : null);
        if (target == null) { sender.sendMessage("Player not found."); return true; }
        target.getInventory().addItem(si.buildUpgradeCore());
        sender.sendMessage(Component.text("Gave Upgrade Core to " + target.getName(), NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("class") && args.length == 1) {
            for (PlayerClass cls : cm.getAllClasses())
                if (cls.getName().toLowerCase().startsWith(args[0].toLowerCase()))
                    out.add(cls.getName());
            out.add("list"); out.add("info");
        }
        if (cmd.getName().equalsIgnoreCase("ability") && args.length == 1) {
            out.add("1"); out.add("2");
        }
        return out;
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
