package com.shieldssmp.commands;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import com.shieldssmp.items.ClassShieldBuilder;
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

    private final ShieldsSMP       plugin;
    private final ClassManager     cm;
    private final SpecialItems     si;
    private final ClassShieldBuilder shieldBuilder;
    private final com.shieldssmp.items.MythicalItems mythical;

    private static final List<String> ALL_CLASSES = List.of(
            "Phantom", "Randomize", "Mimic", "Life",
            "Gravity", "Combustion", "Boss", "Super",
            "Blood", "Null", "Blink", "Speed Demon", "Frost");

    public ClassCommands(ShieldsSMP plugin) {
        this.plugin        = plugin;
        this.cm            = plugin.getClassManager();
        this.si            = plugin.getSpecialItems();
        this.shieldBuilder = plugin.getClassShieldBuilder();
        this.mythical      = plugin.getMythicalItems();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        return switch (cmd.getName().toLowerCase()) {
            case "ability"       -> handleAbility(sender, args);
            case "ultimate"      -> handleUltimate(sender);
            case "class"         -> handleClass(sender, args);
            case "lives"         -> handleLives(sender, args);
            case "givenullifier" -> handleGiveNullifier(sender, args);
            case "giveessence"   -> handleGiveEssence(sender, args);
            case "giveupgrade"   -> handleGiveUpgrade(sender, args);
            case "givereroll"    -> handleGiveReroll(sender, args);
            case "giveshield"    -> handleGiveShield(sender, args);
            case "withdrawlife"    -> handleWithdrawLife(sender, args);
            case "withdrawupgrade"   -> handleWithdrawUpgrade(sender, args);
            case "giveultupgrader"      -> handleGiveUltUpgrader(sender, args);
            case "giveshieldedset"      -> handleGiveShieldedSet(sender, args);
            case "givepocketwatch"      -> handleGiveMythical(sender, args, "pocketwatch");
            case "givenullifieraxe"     -> handleGiveMythical(sender, args, "nullifieraxe");
            case "givereaper"           -> handleGiveMythical(sender, args, "reaper");
            case "giveshieldedtotem"    -> handleGiveMythical(sender, args, "shieldedtotem");
            case "giverevivebook"       -> handleGiveReviveBook(sender, args);
            case "trust"                -> handleTrust(sender, args);
            case "testmode"              -> handleTestMode(sender, args);
            default -> false;
        };
    }

    // ── /ability 1|2 ─────────────────────────────────────────────────────────

    private boolean handleAbility(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        if (args.length == 0) {
            player.sendActionBar(Component.text("Usage: /ability 1  or  /ability 2", NamedTextColor.RED));
            return true;
        }
        switch (args[0]) {
            case "1" -> cm.useAbility1(player);
            case "2" -> cm.useAbility2(player);
            default  -> player.sendActionBar(Component.text("Usage: /ability 1  or  /ability 2", NamedTextColor.RED));
        }
        return true;
    }

    // ── /ultimate ─────────────────────────────────────────────────────────────

    private boolean handleUltimate(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        cm.useUltimate(player);
        return true;
    }

    // ── /class [info|list] ────────────────────────────────────────────────────
    // Class selection is handled by shields — /class is read-only for players

    private boolean handleClass(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }

        String sub = args.length > 0 ? args[0].toLowerCase() : "info";

        if (sub.equals("list")) {
            player.sendMessage(Component.text("═══ Available Classes ═══", NamedTextColor.GOLD, TextDecoration.BOLD));
            for (PlayerClass cls : cm.getAllClasses()) {
                player.sendMessage(Component.text("  • ", NamedTextColor.YELLOW)
                        .append(Component.text(cls.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text(" – " + cls.getDescription(), NamedTextColor.GRAY)));
            }
            player.sendMessage(Component.text("Classes are assigned via your class shield.", NamedTextColor.DARK_GRAY));
            return true;
        }

        // /class or /class info — show current class
        PlayerClass cls = cm.getPlayerClass(player.getUniqueId());
        if (cls == null) {
            player.sendMessage(Component.text("You have no class yet. A shield will be given shortly.", NamedTextColor.YELLOW));
            return true;
        }
        int level = cm.getLevel(player.getUniqueId());
        player.sendMessage(Component.text("═══ " + cls.getName() + " (Lv" + level + ") ═══", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("  " + cls.getDescription(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("  Passive: ", NamedTextColor.AQUA).append(Component.text("Always active", NamedTextColor.GRAY)));
        if (level >= 2) {
            player.sendMessage(Component.text("  /ability 1  → ", NamedTextColor.GREEN).append(Component.text(cls.getAbility1Name(), NamedTextColor.WHITE)));
            player.sendMessage(Component.text("  /ability 2  → ", NamedTextColor.GREEN).append(Component.text(cls.getAbility2Name(), NamedTextColor.WHITE)));
            player.sendMessage(Component.text("  /ultimate   → ", NamedTextColor.RED).append(Component.text(cls.getUltimateName(), NamedTextColor.WHITE)));
        } else {
            player.sendMessage(Component.text("  Use a ⚡ Class Upgrade Core to unlock abilities!", NamedTextColor.YELLOW));
        }
        return true;
    }

    // ── /lives ────────────────────────────────────────────────────────────────

    private boolean handleLives(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Specify a player."); return true; }
            int lives = plugin.getLifeSystem().getLives(player.getUniqueId());
            player.sendMessage(Component.text("You have " + lives + " lives remaining.", NamedTextColor.LIGHT_PURPLE));
            return true;
        }
        if (!sender.hasPermission("shieldssmp.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED)); return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return true; }
        if (args.length == 3) {
            try {
                int amount = Integer.parseInt(args[2]);
                var data = cm.getPlayerData(target.getUniqueId());
                if (args[1].equalsIgnoreCase("set"))      data.setLives(amount);
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
        if (!sender.hasPermission("shieldssmp.admin")) { noPerms(sender); return true; }
        Player t = resolveTarget(sender, args, 0);
        if (t == null) return true;
        t.getInventory().addItem(si.buildNullifierAxe());
        sender.sendMessage(Component.text("Gave Nullifier Axe to " + t.getName(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleGiveEssence(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shieldssmp.admin")) { noPerms(sender); return true; }
        Player t = resolveTarget(sender, args, 0);
        if (t == null) return true;
        int amount = args.length > 1 ? parseInt(args[1], 1) : 1;
        for (int i = 0; i < amount; i++) t.getInventory().addItem(si.buildLifeEssence());
        sender.sendMessage(Component.text("Gave " + amount + "x Life Essence to " + t.getName(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleGiveUpgrade(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shieldssmp.admin")) { noPerms(sender); return true; }
        Player t = resolveTarget(sender, args, 0);
        if (t == null) return true;
        t.getInventory().addItem(si.buildUpgradeCore());
        sender.sendMessage(Component.text("Gave Upgrade Core to " + t.getName(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleGiveReroll(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shieldssmp.admin")) { noPerms(sender); return true; }
        Player t = resolveTarget(sender, args, 0);
        if (t == null) return true;
        int amount = args.length > 1 ? parseInt(args[1], 1) : 1;
        for (int i = 0; i < amount; i++) t.getInventory().addItem(shieldBuilder.buildRerollTotem());
        sender.sendMessage(Component.text("Gave " + amount + "x Reroll Totem to " + t.getName(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleGiveShield(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shieldssmp.admin")) { noPerms(sender); return true; }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /giveshield <player> <class>", NamedTextColor.RED));
            return true;
        }
        Player t = Bukkit.getPlayerExact(args[0]);
        if (t == null) { sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return true; }

        String className = matchClass(args[1]);
        if (className == null) {
            sender.sendMessage(Component.text("Unknown class. Options: " + String.join(", ", ALL_CLASSES), NamedTextColor.RED));
            return true;
        }

        // Remove old shield and assign new one
        plugin.getShieldListener().ensureHasShield(t);
        // Force-set the specific class shield
        removeOldShield(t);
        t.getInventory().addItem(shieldBuilder.buildShield(className));
        cm.setClass(t, className, true);
        sender.sendMessage(Component.text("Gave " + className + " shield to " + t.getName(), NamedTextColor.GREEN));
        return true;
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    private boolean handleWithdrawLife(CommandSender sender, String[] args) {
        // Players can withdraw their own life, admins can withdraw from others
        Player target;
        if (args.length > 0 && sender.hasPermission("shieldssmp.admin")) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return true; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("Specify a player."); return true;
        }
        plugin.getLifeSystem().withdrawLife(target);
        if (!target.equals(sender))
            sender.sendMessage(Component.text("Withdrew 1 life from " + target.getName(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleWithdrawUpgrade(CommandSender sender, String[] args) {
        Player target;
        if (args.length > 0 && sender.hasPermission("shieldssmp.admin")) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return true; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("Specify a player."); return true;
        }

        var data = cm.getPlayerData(target.getUniqueId());
        if (data.getLevel() < 2) {
            sender.sendMessage(Component.text(target.getName() + " is not Level 2!", NamedTextColor.RED));
            return true;
        }

        // Revert to level 1 and give back the Upgrade Core
        data.setLevel(1);
        data.save(plugin);
        target.getInventory().addItem(si.buildUpgradeCore());

        target.sendActionBar(Component.text("⚡ Upgrade withdrawn — back to Level 1!", NamedTextColor.YELLOW));
        if (!target.equals(sender))
            sender.sendMessage(Component.text("Withdrew upgrade from " + target.getName(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleGiveUltUpgrader(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shieldssmp.admin")) { noPerms(sender); return true; }
        Player t = resolveTarget(sender, args, 0);
        if (t == null) return true;
        int amount = args.length > 1 ? parseInt(args[1], 1) : 1;
        for (int i = 0; i < amount; i++) t.getInventory().addItem(si.buildUltimateUpgrader());
        sender.sendMessage(Component.text("Gave " + amount + "x Ultimate Upgrader to " + t.getName(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleGiveShieldedSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shieldssmp.admin")) { noPerms(sender); return true; }
        Player t = resolveTarget(sender, args, 0);
        if (t == null) return true;
        t.getInventory().addItem(mythical.buildShieldedHelmet());
        t.getInventory().addItem(mythical.buildShieldedChestplate());
        t.getInventory().addItem(mythical.buildShieldedLeggings());
        t.getInventory().addItem(mythical.buildShieldedBoots());
        sender.sendMessage(Component.text("Gave full Shielded Set to " + t.getName(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleGiveMythical(CommandSender sender, String[] args, String type) {
        if (!sender.hasPermission("shieldssmp.admin")) { noPerms(sender); return true; }
        Player t = resolveTarget(sender, args, 0);
        if (t == null) return true;
        org.bukkit.inventory.ItemStack item = switch (type) {
            case "pocketwatch"   -> mythical.buildPocketWatch();
            case "nullifieraxe"  -> plugin.getSpecialItems().buildNullifierAxe();
            case "reaper"        -> mythical.buildReaperScythe();
            case "shieldedtotem" -> mythical.buildShieldedTotem();
            default -> null;
        };
        if (item == null) return true;
        t.getInventory().addItem(item);
        sender.sendMessage(Component.text("Gave item to " + t.getName(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleGiveReviveBook(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shieldssmp.admin")) { noPerms(sender); return true; }
        Player t = resolveTarget(sender, args, 0);
        if (t == null) return true;
        int amount = args.length > 1 ? parseInt(args[1], 1) : 1;
        for (int i = 0; i < amount; i++) t.getInventory().addItem(si.buildReviveBook());
        sender.sendMessage(Component.text("Gave " + amount + "x Revive Book to " + t.getName(), NamedTextColor.GREEN));
        return true;
    }

    private boolean handleTrust(CommandSender sender, String[] args) {
        if (!(sender instanceof Player caster)) { sender.sendMessage("Players only."); return true; }
        if (args.length < 1) { sender.sendMessage(Component.text("Usage: /trust <player>", NamedTextColor.RED)); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED)); return true; }
        if (target.equals(caster)) { sender.sendMessage(Component.text("You always trust yourself.", NamedTextColor.GRAY)); return true; }

        boolean nowTrusted = plugin.getTrustSystem().toggleTrust(caster, target);
        if (nowTrusted) {
            caster.sendMessage(Component.text("✦ You now trust " + target.getName() + ". Your positive abilities will affect them; negative ones will skip them.", NamedTextColor.GREEN));
            target.sendMessage(Component.text("✦ " + caster.getName() + " now trusts you!", NamedTextColor.GREEN));
        } else {
            caster.sendMessage(Component.text("✦ You no longer trust " + target.getName() + ".", NamedTextColor.YELLOW));
        }
        return true;
    }

    private boolean handleTestMode(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shieldssmp.admin")) { noPerms(sender); return true; }
        Player t = resolveTarget(sender, args, 0);
        if (t == null) return true;

        cm.resetAllCooldowns(t);
        plugin.getMaceListener().resetCooldowns(t);
        plugin.getMythicalItemListener().resetCooldowns(t);

        // Reset all relevant vanilla item cooldown bars too
        for (org.bukkit.Material m : new org.bukkit.Material[]{
                org.bukkit.Material.SHIELD, org.bukkit.Material.MACE, org.bukkit.Material.NETHERITE_AXE,
                org.bukkit.Material.CLOCK, org.bukkit.Material.GOLDEN_HELMET, org.bukkit.Material.GOLDEN_CHESTPLATE,
                org.bukkit.Material.TOTEM_OF_UNDYING}) {
            t.setCooldown(m, 0);
        }

        t.sendMessage(Component.text("⚙ [TEST MODE] All cooldowns reset!", NamedTextColor.LIGHT_PURPLE, net.kyori.adventure.text.format.TextDecoration.BOLD));
        if (!t.equals(sender)) sender.sendMessage(Component.text("Reset cooldowns for " + t.getName(), NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase();

        if (name.equals("ability") && args.length == 1) { out.add("1"); out.add("2"); }
        if (name.equals("class")   && args.length == 1) { out.add("info"); out.add("list"); }
        if (name.equals("giveshield") && args.length == 2) out.addAll(ALL_CLASSES);
        if ((name.equals("givenullifier") || name.equals("giveessence") ||
             name.equals("giveupgrade")   || name.equals("givereroll") ||
             name.equals("giveshield")    || name.equals("lives")) && args.length == 1) {
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) out.add(p.getName());
            });
        }
        return out;
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private Player resolveTarget(CommandSender sender, String[] args, int idx) {
        if (args.length > idx) {
            Player t = Bukkit.getPlayerExact(args[idx]);
            if (t == null) sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return t;
        }
        if (sender instanceof Player p) return p;
        sender.sendMessage("Specify a player name.");
        return null;
    }

    private void removeOldShield(Player player) {
        var inv = player.getInventory();
        if (shieldBuilder.isClassShield(inv.getItemInOffHand()))
            inv.setItemInOffHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR));
        var contents = inv.getContents();
        for (int i = 0; i < contents.length; i++)
            if (shieldBuilder.isClassShield(contents[i])) contents[i] = null;
        inv.setContents(contents);
    }

    private String matchClass(String input) {
        for (String cls : ALL_CLASSES)
            if (cls.equalsIgnoreCase(input)) return cls;
        return null;
    }

    private void noPerms(CommandSender sender) {
        sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
