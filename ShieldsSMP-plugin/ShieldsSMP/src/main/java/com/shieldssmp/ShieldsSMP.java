package com.shieldssmp;

import com.shieldssmp.commands.ClassCommands;
import com.shieldssmp.commands.MaceCooldownCommand;
import com.shieldssmp.commands.MaceCommand;
import com.shieldssmp.items.SpecialItems;
import com.shieldssmp.listeners.GlobalListener;
import com.shieldssmp.listeners.MaceListener;
import com.shieldssmp.systems.ClassManager;
import com.shieldssmp.systems.LifeSystem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShieldsSMP extends JavaPlugin {

    public static final String MACE_KEY_ID = "shields_smp_mace";

    private static ShieldsSMP instance;
    private NamespacedKey maceKey;

    private SpecialItems specialItems;
    private ClassManager classManager;
    private LifeSystem   lifeSystem;
    private MaceListener maceListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        maceKey      = new NamespacedKey(this, MACE_KEY_ID);
        specialItems = new SpecialItems(this);
        classManager = new ClassManager(this);
        lifeSystem   = new LifeSystem(this, specialItems);

        // Listeners
        maceListener = new MaceListener(this);
        getServer().getPluginManager().registerEvents(maceListener, this);
        getServer().getPluginManager().registerEvents(new GlobalListener(this), this);

        // Commands
        ClassCommands cc = new ClassCommands(this);
        registerCmd("ability",       cc);
        registerCmd("ultimate",      cc);
        registerCmd("class",         cc, cc);
        registerCmd("lives",         cc);
        registerCmd("givenullifier", cc);
        registerCmd("giveessence",   cc);
        registerCmd("giveupgrade",   cc);

        registerCmd("mace",          new MaceCommand(this));

        var cooldownCmd = new MaceCooldownCommand(maceListener);
        registerCmd("macecooldown",  cooldownCmd, cooldownCmd);

        registerCmd("macereload", (sender, cmd, label, args) -> {
            reloadConfig();
            maceListener.reloadSettings();
            sender.sendMessage(Component.text("[ShieldsSMP] Config reloaded.", NamedTextColor.GREEN));
            return true;
        });

        getLogger().info("╔═══════════════════════════════════════╗");
        getLogger().info("║  Shields SMP  –  Full Class System    ║");
        getLogger().info("║  8 Classes | Lives | Nullifier Axe    ║");
        getLogger().info("╚═══════════════════════════════════════╝");
    }

    @Override
    public void onDisable() {
        // Save all online player data
        if (classManager != null) {
            getServer().getOnlinePlayers().forEach(p -> classManager.savePlayerData(p.getUniqueId()));
        }
        getLogger().info("[ShieldsSMP] Disabled.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void registerCmd(String name, org.bukkit.command.CommandExecutor exec) {
        var c = getCommand(name);
        if (c != null) c.setExecutor(exec);
    }

    private void registerCmd(String name, org.bukkit.command.CommandExecutor exec, org.bukkit.command.TabCompleter tab) {
        var c = getCommand(name);
        if (c != null) { c.setExecutor(exec); c.setTabCompleter(tab); }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public static ShieldsSMP getInstance() { return instance; }
    public NamespacedKey getMaceKey()       { return maceKey; }
    public SpecialItems  getSpecialItems()  { return specialItems; }
    public ClassManager  getClassManager()  { return classManager; }
    public LifeSystem    getLifeSystem()    { return lifeSystem; }
}
