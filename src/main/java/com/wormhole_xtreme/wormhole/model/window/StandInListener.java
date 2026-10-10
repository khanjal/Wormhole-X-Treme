package com.wormhole_xtreme.wormhole.model.window;

import java.util.function.Function;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

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
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
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
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
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
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
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
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
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
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
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
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
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
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPress(final EntityInteractEvent event)
    {
        refuse(event, event.getEntity());
    }

    /**
     * A stand-in that reaches a nether or end portal stays: through it, it would be a real, inert
     * copy in another world, which nobody holds.
     *
     * @param event
     *            the trip
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPortal(final EntityPortalEvent event)
    {
        refuse(event, event.getEntity());
    }

    /** The split event's name from 26.x, where {@code SlimeSplitEvent} is deprecated and only a subclass of it. */
    static final String CUBE_SPLIT = "org.bukkit.event.entity.CubeMobSplitEvent";

    /** The split event's name before 26.x, named rather than imported so 26.x builds see no deprecated use. */
    static final String SLIME_SPLIT = "org.bukkit.event.entity.SlimeSplitEvent";

    /** Refuses a split of a stand-in: a slime one killed anyway would split into small slimes nobody holds. */
    static final EventExecutor REFUSE_SPLIT = (listener, event) ->
    {
        if ((event instanceof EntityEvent split) && (event instanceof Cancellable cancellable)
            && StandIns.isStandIn(split.getEntity()))
        {
            cancellable.setCancelled(true);
        }
    };

    /**
     * Registers the listener, and its split refusal for whichever split event this server fires.
     *
     * <p>Not an annotated handler: on Spigot 26.x {@code SlimeSplitEvent} is deprecated in favour of
     * {@code CubeMobSplitEvent}, and the server warns at startup about a handler for a deprecated event.
     *
     * @param manager
     *            the plugin manager
     * @param plugin
     *            this plugin
     */
    public static void register(final PluginManager manager, final Plugin plugin)
    {
        final StandInListener listener = new StandInListener();
        manager.registerEvents(listener, plugin);
        final Class<? extends Event> split = splitEvent(StandInListener::classNamed);
        if (split != null)
        {
            manager.registerEvent(split, listener, EventPriority.LOWEST, REFUSE_SPLIT, plugin, true);
        }
    }

    /**
     * The split event to listen for: {@code CubeMobSplitEvent} where the server has it, else
     * {@code SlimeSplitEvent}.
     *
     * @param lookup
     *            finds a class by name, or null where there is none
     * @return the event class, or null on a server with neither
     */
    static Class<? extends Event> splitEvent(final Function<String, Class<?>> lookup)
    {
        final Class<? extends Event> cube = event(lookup.apply(CUBE_SPLIT));
        return (cube != null) ? cube : event(lookup.apply(SLIME_SPLIT));
    }

    /** The class as an event class, or null for none or one that is not an event. */
    private static Class<? extends Event> event(final Class<?> found)
    {
        return ((found != null) && Event.class.isAssignableFrom(found)) ? found.asSubclass(Event.class) : null;
    }

    /** A class by name, or null where this server has none. */
    private static Class<?> classNamed(final String name)
    {
        try
        {
            return Class.forName(name);
        }
        catch (final ClassNotFoundException | LinkageError absent)
        {
            return null;
        }
    }

    /**
     * A chicken stand-in lays no eggs, and no other drops what its creature did not.
     *
     * @param event
     *            the drop
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(final EntityDropItemEvent event)
    {
        refuse(event, event.getEntity());
    }

    /**
     * A stand-in changes no block: no trampled farmland, no eaten grass, nothing picked up.
     *
     * @param event
     *            the change
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChangeBlock(final EntityChangeBlockEvent event)
    {
        refuse(event, event.getEntity());
    }

    /**
     * A snow golem's trail and the like: a stand-in forms no block. Snow golems are not copied, so
     * this is the second line.
     *
     * @param event
     *            the forming
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onForm(final EntityBlockFormEvent event)
    {
        refuse(event, event.getEntity());
    }

    /**
     * Damage again, last: a plugin at a normal priority that uncancels damage must not expose one.
     *
     * @param event
     *            the damage, cancelled or not
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageAgain(final EntityDamageEvent event)
    {
        refuse(event, event.getEntity());
    }

    /**
     * Targeting again, last, for the same reason.
     *
     * @param event
     *            the targeting, cancelled or not
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTargetAgain(final EntityTargetEvent event)
    {
        refuse(event, event.getTarget());
    }

    /**
     * One killed anyway, by a command, drops nothing: what it wears was copied, not earned.
     *
     * @param event
     *            the death
     */
    @EventHandler(priority = EventPriority.HIGHEST)
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

    /**
     * A world unloading takes the chunks held in it for a view with it, and nothing keeps counting them.
     *
     * @param event
     *            the unload
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(final WorldUnloadEvent event)
    {
        FarChunkHolds.forgetWorld(event.getWorld().getName());
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
