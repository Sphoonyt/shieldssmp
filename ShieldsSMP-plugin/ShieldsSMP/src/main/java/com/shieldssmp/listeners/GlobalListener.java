package com.shieldssmp.listeners;

import com.shieldssmp.ShieldsSMP;
import com.shieldssmp.classes.PlayerClass;
import com.shieldssmp.classes.impl.PhantomClass;
import com.shieldssmp.classes.impl.TerroristClass;
import com.shieldssmp.items.SpecialItems;
import com.shieldssmp.systems.ClassManager;
import com.shieldssmp.systems.LifeSystem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

public class GlobalListener implements Listener {

    private final ShieldsSMP plugin;
    private final ClassManager cm;
    private final LifeSystem lifeSystem;
    private final SpecialItems specialItems;

    public GlobalListener(ShieldsSMP plugin) {
        this.plugin       = plugin;
        this.cm           = plugin.getClassManager();
        this.lifeSystem   = plugin.getLifeSystem();
        this.specialItems = plugin.getSpecialItems();
    }

    // ── Player join / quit ────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cm.loadPlayer(event.getPlayer());
        // Shield is given/verified by ShieldListener.onFirstJoin which fires after this
        // Sync shield durability to saved lives count (in case they changed offline)
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> plugin.getLifeSystem().syncOnJoin(event.getPlayer()), 10L);
        lifeSystem.sendLivesBar(event.getPlayer(), lifeSystem.getLives(event.getPlayer().getUniqueId()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cm.unloadPlayer(event.getPlayer());
    }

    // ── Death ─────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        lifeSystem.handleDeath(player);

        // Shielded Totem Soul Guard – skip life deduction if totem saved them
        if (plugin.getMythicalItemListener().consumeTotemProtection(player.getUniqueId())) {
            // Totem handled – just call class death hook, skip life loss
            PlayerClass totemCls = cm.getPlayerClass(player.getUniqueId());
            if (totemCls != null) totemCls.onDeath(player);
            return;
        }

        // If player was Level 2, drop their Upgrade Core and revert to Level 1
        var data = cm.getPlayerData(player.getUniqueId());
        if (data.getLevel() >= 2) {
            data.setLevel(1);
            data.save(plugin);
            // Drop upgrade core at death location
            player.getWorld().dropItemNaturally(player.getLocation(), specialItems.buildUpgradeCore());
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "[ShieldsSMP] Your Upgrade Core dropped on death!", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        }

        PlayerClass cls = cm.getPlayerClass(player.getUniqueId());
        if (cls != null) cls.onDeath(player);
    }

    // ── Kill hook ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player killer = event.getEntity().getKiller();
        PlayerClass cls = cm.getPlayerClass(killer.getUniqueId());
        if (cls != null) cls.onKill(killer, event.getEntity());
    }

    // ── Damage hooks ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Nullifier Axe
        if (event.getDamager() instanceof Player attacker) {
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            if (specialItems.isNullifierAxe(weapon)) {
                if (!cm.isOnNullifierCooldown(attacker.getUniqueId())) {
                    if (event.getEntity() instanceof Player victim) {
                        cm.disableAbilities(victim, 60_000L);
                        cm.putNullifierCooldown(attacker.getUniqueId());
                        attacker.sendActionBar(net.kyori.adventure.text.Component.text(
                                "☠ Nullifier hit " + victim.getName() + "!", net.kyori.adventure.text.format.NamedTextColor.DARK_RED));
                    }
                }
            }

            // Attacker class damage hook (blocked at 0 lives)
            if (event.getEntity() instanceof org.bukkit.entity.LivingEntity le) {
                PlayerClass cls = cm.getPlayerClass(attacker.getUniqueId());
                if (cls != null && !cm.abilitiesDisabled(attacker.getUniqueId())
                        && plugin.getLifeSystem().hasLives(attacker.getUniqueId())) {
                    cls.onDealDamage(attacker, le, event.getDamage());
                }
            }
        }

        // Victim class damage hook
        if (event.getEntity() instanceof Player victim) {
            PlayerClass cls = cm.getPlayerClass(victim.getUniqueId());
            if (cls != null && !cm.abilitiesDisabled(victim.getUniqueId())
                    && plugin.getLifeSystem().hasLives(victim.getUniqueId())) {
                cls.onTakeDamage(victim, event.getDamager(), event.getDamage());
            }

            // Terrorist blast proof
            if (cls instanceof TerroristClass) {
                if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                        || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosion(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return;
        PlayerClass cls = cm.getPlayerClass(p.getUniqueId());
        if (cls instanceof TerroristClass) event.setCancelled(true);
    }

    // ── Block break – reveal Phantom ──────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onBreakBlock(BlockBreakEvent event) {
        Player player = event.getPlayer();
        PlayerClass cls = cm.getPlayerClass(player.getUniqueId());
        if (cls != null) cls.onBreakBlock(player);
    }

    // ── Item consumption (Life, Golden Apple hooks) ────────────────────────────

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Life Essence right-click pickup
        if (specialItems.isLifeEssence(item)) {
            event.setCancelled(true);
            if (lifeSystem.tryGiveLife(player, item)) {
                player.getInventory().getItemInMainHand().subtract(1);
            }
            return;
        }

        // Upgrade Core
        if (specialItems.isUpgradeCore(item)) {
            event.setCancelled(true);
            if (lifeSystem.tryUpgrade(player, item)) {
                player.getInventory().getItemInMainHand().subtract(1);
            }
            return;
        }

        // Class passive consumption hook
        PlayerClass cls = cm.getPlayerClass(player.getUniqueId());
        if (cls != null) cls.onConsumeItem(player, item);
    }

    // ── Life Essence / Upgrade Core right-click (held item, not edible) ───────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null) return;

        if (specialItems.isLifeEssence(item)) {
            event.setCancelled(true);
            if (lifeSystem.tryGiveLife(player, item)) {
                item.subtract(1);
            }
            return;
        }

        if (specialItems.isUpgradeCore(item)) {
            event.setCancelled(true);
            if (lifeSystem.tryUpgrade(player, item)) {
                item.subtract(1);
            }
        }
    }

    // ── Phantom: cancel attack-based reveal in spectral form ──────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPhantomSpectralAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        PlayerClass cls = cm.getPlayerClass(attacker.getUniqueId());
        if (cls instanceof PhantomClass phantom) {
            if (phantom.isSpectral(attacker.getUniqueId())) {
                event.setCancelled(true); // Cannot attack in spectral realm
            }
        }
    }
}
