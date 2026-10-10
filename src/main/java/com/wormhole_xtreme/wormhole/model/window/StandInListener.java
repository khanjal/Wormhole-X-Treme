package com.wormhole_xtreme.wormhole.model.window;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Keeps a view's stand-ins (#296) out of the game: they are real entities, so without this they
 * could be hurt, clicked, set alight, turned into something else, hunted, or press a plate, and
 * a viewer who leaves keeps none.
 */
public class StandInListener implements Listener
{
    /**
     * Refuses all damage; invulnerable does not stop a creative-mode player or the void.
     *
     * @param event
     *            the damage
     */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(final EntityDamageEvent event)
    {
        refuse(event, event.getEntity());
    }

    /**
     * Refuses a click on one: no leash, shears, saddle, name tag or trade.
     *
     * @param event
     *            the click
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteract(final PlayerInteractEntityEvent event)
    {
        refuse(event, event.getRightClicked());
    }

    /**
     * The same click aimed at a point on it, which Bukkit raises separately.
     *
     * @param event
     *            the click
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteractAt(final PlayerInteractAtEntityEvent event)
    {
        refuse(event, event.getRightClicked());
    }

    /**
     * A zombie stand-in in daylight would burn, though its creature stands in the dark.
     *
     * @param event
     *            the fire
     */
    @EventHandler(ignoreCancelled = true)
    public void onCombust(final EntityCombustEvent event)
    {
        refuse(event, event.getEntity());
    }

    /**
     * A piglin shown in the overworld would turn into a zombified one, a new entity nobody holds.
     *
     * @param event
     *            the change
     */
    @EventHandler(ignoreCancelled = true)
    public void onTransform(final EntityTransformEvent event)
    {
        refuse(event, event.getEntity());
    }

    /**
     * Real mobs see a stand-in as real: a zombie would hunt a villager's behind the wall.
     *
     * @param event
     *            the targeting
     */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(final EntityTargetEvent event)
    {
        refuse(event, event.getTarget());
    }

    /**
     * A stand-in standing on a pressure plate or a tripwire behind the wall must not trip it.
     *
     * @param event
     *            the press
     */
    @EventHandler(ignoreCancelled = true)
    public void onPress(final EntityInteractEvent event)
    {
        refuse(event, event.getEntity());
    }

    /**
     * One killed anyway, by a command, drops nothing: what it wears was copied, not earned.
     *
     * @param event
     *            the death
     */
    @EventHandler
    public void onDeath(final EntityDeathEvent event)
    {
        if (StandIns.isStandIn(event.getEntity()))
        {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    /**
     * A viewer who dies stops seeing the far room's creatures until they look again.
     *
     * @param event
     *            the death
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onViewerDeath(final PlayerDeathEvent event)
    {
        Windows.dropStandIns(event.getEntity().getUniqueId());
    }

    /**
     * A viewer who leaves takes their stand-ins with them, rather than at the next sweep.
     *
     * @param event
     *            the quit
     */
    @EventHandler
    public void onQuit(final PlayerQuitEvent event)
    {
        Windows.dropStandIns(event.getPlayer().getUniqueId());
    }

    /**
     * @param event
     *            the respawn
     */
    @EventHandler
    public void onRespawn(final PlayerRespawnEvent event)
    {
        Windows.dropStandIns(event.getPlayer().getUniqueId());
    }

    /**
     * Stand-ins stay in the world they were spawned in, which the viewer has left.
     *
     * @param event
     *            the world change
     */
    @EventHandler
    public void onChangedWorld(final PlayerChangedWorldEvent event)
    {
        Windows.dropStandIns(event.getPlayer().getUniqueId());
    }

    /** Cancels an event about a stand-in. */
    private static void refuse(final Cancellable event, final Entity entity)
    {
        if (StandIns.isStandIn(entity))
        {
            event.setCancelled(true);
        }
    }
}
