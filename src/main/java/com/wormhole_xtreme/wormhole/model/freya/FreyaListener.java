package com.wormhole_xtreme.wormhole.model.freya;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * Everything the companion reacts to, kept apart from the gate listeners.
 *
 * <p>Besides following her owner, this takes away what a real tamed cat would give them: she
 * steps away while a creeper or phantom hunts them, and while they sleep, so there is no
 * morning gift.
 */
public class FreyaListener implements Listener
{
    /** How often to look again whether a hunt is over: five seconds. */
    private static final long HUNT_CHECK_TICKS = 100L;

    @EventHandler
    public void onJoin(final PlayerJoinEvent event)
    {
        catchUpNextTick(event.getPlayer());
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event)
    {
        FreyaCompanion.forgetOwner(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(final PlayerTeleportEvent event)
    {
        catchUpNextTick(event.getPlayer());
    }

    /**
     * Nether and end portals raise PlayerPortalEvent, which the teleport handler never sees.
     *
     * @param event
     *            the world change
     */
    @EventHandler
    public void onChangedWorld(final PlayerChangedWorldEvent event)
    {
        catchUpNextTick(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(final PlayerRespawnEvent event)
    {
        catchUpNextTick(event.getPlayer());
    }

    /**
     * Lets her own spawn through a region or plugin that refuses mob spawns, WorldGuard's
     * block-plugin-spawning among them; nobody else can see or touch her.
     *
     * @param event
     *            the spawn
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(final CreatureSpawnEvent event)
    {
        if (FreyaCompanion.isBeingSummoned() && (event.getEntity() instanceof Cat))
        {
            event.setCancelled(false);
        }
    }

    /**
     * Refuses all damage; setInvulnerable does not stop a creative-mode player, who could not
     * even see her.
     *
     * @param event
     *            the damage
     */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(final EntityDamageEvent event)
    {
        if (FreyaCompanion.isCompanion(event.getEntity()))
        {
            event.setCancelled(true);
        }
    }

    /**
     * Refuses every interaction, so she cannot be sat, leashed or renamed.
     *
     * @param event
     *            the interaction
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(final PlayerInteractEntityEvent event)
    {
        if (FreyaCompanion.isCompanion(event.getRightClicked()))
        {
            event.setCancelled(true);
        }
    }

    /**
     * Sends her away when a creeper or phantom starts hunting her owner, so it does not shy off.
     *
     * @param event
     *            the targeting
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTarget(final EntityTargetLivingEntityEvent event)
    {
        if (FreyaCompanion.scaredOfCats(event.getEntity()) && (event.getTarget() instanceof Player owner)
            && FreyaCompanion.ownerHunted(owner.getUniqueId()))
        {
            watchHunt(owner);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(final PlayerBedEnterEvent event)
    {
        if (event.getBedEnterResult() == PlayerBedEnterEvent.BedEnterResult.OK)
        {
            FreyaCompanion.ownerSleeps(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onBedLeave(final PlayerBedLeaveEvent event)
    {
        FreyaCompanion.ownerWakes(event.getPlayer().getUniqueId());
        catchUpNextTick(event.getPlayer());
    }

    /**
     * Stands her up before vanilla checks whether a cat is sitting on the chest being opened.
     *
     * @param event
     *            the click
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChestOpen(final PlayerInteractEvent event)
    {
        final Block block = event.getClickedBlock();
        if ((event.getAction() == Action.RIGHT_CLICK_BLOCK) && (block != null) && isChest(block.getType())
            && (FreyaCompanion.liveCount() > 0))
        {
            FreyaCompanion.standUpNear(block.getLocation().add(0.5, 0.5, 0.5));
        }
    }

    /**
     * @param type
     *            a block type
     * @return true for the chests a sitting cat blocks
     */
    static boolean isChest(final Material type)
    {
        return (type == Material.CHEST) || (type == Material.TRAPPED_CHEST);
    }

    /**
     * Brings her a tick after her owner arrives, so they are standing at the destination.
     *
     * @param player
     *            the player who has just joined, travelled, respawned or woken
     */
    private static void catchUpNextTick(final Player player)
    {
        if (!FreyaPreferences.isEnabled(player.getUniqueId()))
        {
            return;
        }
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), () ->
        {
            if (player.isOnline())
            {
                FreyaCompanion.catchUp(player);
            }
        }, 1L);
    }

    /**
     * Looks again every few seconds until nothing is hunting her owner, then brings her back.
     *
     * @param owner
     *            the hunted owner
     */
    private static void watchHunt(final Player owner)
    {
        WormholeXTreme.getScheduler().scheduleSyncDelayedTask(WormholeXTreme.getThisPlugin(), () ->
        {
            if (!FreyaCompanion.huntOver(owner))
            {
                watchHunt(owner);
            }
            else if (owner.isOnline())
            {
                FreyaCompanion.catchUp(owner);
            }
        }, HUNT_CHECK_TICKS);
    }
}
