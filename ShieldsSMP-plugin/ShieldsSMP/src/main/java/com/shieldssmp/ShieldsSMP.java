package com.shieldssmp;

import com.shieldssmp.commands.ClassCommands;
import com.shieldssmp.commands.MaceCooldownCommand;
import com.shieldssmp.commands.MaceCommand;
import com.shieldssmp.items.ClassShieldBuilder;
import com.shieldssmp.items.MythicalItems;
import com.shieldssmp.listeners.MythicalItemListener;
import com.shieldssmp.items.SpecialItems;
import com.shieldssmp.listeners.GlobalListener;
import com.shieldssmp.listeners.MaceListener;
import com.shieldssmp.listeners.ShieldListener;
import com.shieldssmp.systems.ClassManager;
import com.shieldssmp.systems.LifeSystem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShieldsSMP extends JavaPlugin {

    public static final String MACE_KEY_ID = "shields_smp_mace";

    private static ShieldsSMP instance;
    private NamespacedKey maceKey;

    private SpecialItems       specialItems;
    private MythicalItems      mythicalItems;
    private MythicalItemListener mythicalItemListener;
    private com.shieldssmp.systems.TrustSystem trustSystem;
    private ClassShieldBuilder classShieldBuilder;
    private ClassManager       classManager;
    private LifeSystem         lifeSystem;
    private MaceListener       maceListener;
    private ShieldListener     shieldListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        maceKey            = new NamespacedKey(this, MACE_KEY_ID);
        specialItems       = new SpecialItems(this);
        mythicalItems      = new MythicalItems(this);
        trustSystem        = new com.shieldssmp.systems.TrustSystem(this);
        classShieldBuilder = new ClassShieldBuilder(this);
        classManager       = new ClassManager(this);
        lifeSystem         = new LifeSystem(this, specialItems);

        // Listeners
        maceListener   = new MaceListener(this);
        shieldListener = new ShieldListener(this);
        getServer().getPluginManager().registerEvents(maceListener,                        this);
        getServer().getPluginManager().registerEvents(shieldListener,                      this);
        getServer().getPluginManager().registerEvents(new GlobalListener(this),            this);
        mythicalItemListener = new MythicalItemListener(this);
        getServer().getPluginManager().registerEvents(mythicalItemListener, this);
        getServer().getPluginManager().registerEvents(new com.shieldssmp.listeners.AbilityKeybindListener(this), this);
        getServer().getPluginManager().registerEvents(new com.shieldssmp.listeners.DragonEggListener(this), this);
        getServer().getPluginManager().registerEvents(new com.shieldssmp.listeners.AbilityHUDListener(this), this);

        // Commands
        ClassCommands cc = new ClassCommands(this);
        registerCmd("ability",       cc);
        registerCmd("ultimate",      cc);
        registerCmd("class",         cc, cc);
        registerCmd("lives",         cc);
        registerCmd("givenullifier", cc);
        registerCmd("giveessence",   cc);
        registerCmd("giveupgrade",   cc);
        registerCmd("givereroll",    cc);
        registerCmd("giveshield",    cc);
        registerCmd("withdrawlife",  cc);
        registerCmd("withdrawupgrade",  cc);
        registerCmd("giveultupgrader",   cc);
        registerCmd("giveshieldedset",   cc);
        registerCmd("givepocketwatch",   cc);
        registerCmd("givenullifieraxe",  cc);
        registerCmd("givereaper",        cc);
        registerCmd("giveshieldedtotem", cc);
        registerCmd("giverevivebook",    cc);
        registerCmd("trust",             cc);
        registerCmd("testmode",          cc);
        registerCmd("givedragonegg",      cc);

        registerCmd("mace", new MaceCommand(this));

        var cooldownCmd = new MaceCooldownCommand(maceListener);
        registerCmd("macecooldown", cooldownCmd, cooldownCmd);

        registerCmd("macereload", (sender, cmd, label, args) -> {
            reloadConfig();
            maceListener.reloadSettings();
            sender.sendMessage(Component.text("[ShieldsSMP] Config reloaded.", NamedTextColor.GREEN));
            return true;
        });

        getLogger().info("╔═══════════════════════════════════════╗");
        getLogger().info("║  Shields SMP  –  Full Class System    ║");
        getLogger().info("║  8 Classes | Shields | Lives | Mace   ║");
        getLogger().info("╚═══════════════════════════════════════╝");
    }

    @Override
    public void onDisable() {
        if (classManager != null) {
            getServer().getOnlinePlayers().forEach(p -> classManager.savePlayerData(p.getUniqueId()));
        }
        getLogger().info("[ShieldsSMP] Disabled.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void registerCmd(String name, CommandExecutor exec) {
        var c = getCommand(name);
        if (c != null) c.setExecutor(exec);
    }

    private void registerCmd(String name, CommandExecutor exec, TabCompleter tab) {
        var c = getCommand(name);
        if (c != null) { c.setExecutor(exec); c.setTabCompleter(tab); }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public static ShieldsSMP    getInstance()          { return instance; }
    public NamespacedKey        getMaceKey()           { return maceKey; }
    public SpecialItems         getSpecialItems()      { return specialItems; }
    public ClassShieldBuilder   getClassShieldBuilder(){ return classShieldBuilder; }
    public ClassManager         getClassManager()      { return classManager; }
    public LifeSystem           getLifeSystem()        { return lifeSystem; }
    public ShieldListener            getShieldListener()       { return shieldListener; }
    public MythicalItems             getMythicalItems()        { return mythicalItems; }
    public com.shieldssmp.systems.TrustSystem getTrustSystem()  { return trustSystem; }
    public MythicalItemListener      getMythicalItemListener() { return mythicalItemListener; }
    public MaceListener              getMaceListener()         { return maceListener; }
}
