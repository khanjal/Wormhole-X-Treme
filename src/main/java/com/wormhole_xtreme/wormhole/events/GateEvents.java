package com.wormhole_xtreme.wormhole.events;

import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * Sends this plugin's gate events to anything listening for them.
 *
 * <p>Everything goes through here rather than calling the plugin manager at each site, for
 * two reasons. Firing is only safe when a server is actually running, and gate creation and
 * removal are also exercised without one. And a listener belonging to some other plugin is
 * code this plugin does not control: if it throws, that must not take down the gate
 * operation that was only telling it something had happened.
 */
public final class GateEvents
{
    /**
     * Where events are delivered, so tests can watch without a running server.
     *
     * <p>Whether an event is <em>raised</em>, and from where, is the part of this that
     * actually breaks: an event nothing fires, or one fired on a path that is not really a
     * removal, both look like a working feature until somebody writes a listener. Bukkit
     * cannot dispatch without a server, so tests substitute the delivery step and assert on
     * what arrives. Null means deliver through Bukkit, which is what production does.
     */
    private static volatile java.util.function.Consumer<Event> dispatcher = null;

    /** Static helpers only. */
    private GateEvents()
    {
    }

    /**
     * Redirects delivery, for tests.
     *
     * @param replacement
     *            where to send events, or null to deliver through Bukkit as normal
     */
    static void setDispatcherForTest(final java.util.function.Consumer<Event> replacement)
    {
        dispatcher = replacement;
    }

    /**
     * Announces that a gate has been built, named and registered.
     *
     * @param stargate
     *            the gate that was created
     * @param builder
     *            the player who completed it, or null if not a player
     */
    public static void fireCreated(final Stargate stargate, final Player builder)
    {
        fire(new StargateCreatedEvent(stargate, builder));
    }

    /**
     * Announces that a gate is being removed, while it can still be read.
     *
     * @param stargate
     *            the gate being removed
     * @param remover
     *            the player who removed it, or null if not a player
     */
    public static void fireRemoved(final Stargate stargate, final Player remover)
    {
        fire(new StargateRemovedEvent(stargate, remover));
    }

    /**
     * Delivers one event, if there is a server to deliver it through.
     *
     * @param event
     *            the event to send
     */
    private static void fire(final Event event)
    {
        try
        {
            final java.util.function.Consumer<Event> replacement = dispatcher;
            if (replacement != null)
            {
                replacement.accept(event);
                return;
            }
            if (Bukkit.getServer() == null)
            {
                return;
            }
            Bukkit.getPluginManager().callEvent(event);
        }
        catch (final RuntimeException e)
        {
            // A listener in another plugin threw, or there is no server behind this call.
            // Either way the gate operation that raised the event has already happened and
            // must not be undone by whoever was being told about it.
            report(event, e);
        }
    }

    /**
     * Notes a listener failure without letting the reporting itself throw.
     *
     * @param event
     *            the event being delivered
     * @param cause
     *            what went wrong
     */
    private static void report(final Event event, final RuntimeException cause)
    {
        try
        {
            final WormholeXTreme plugin = WormholeXTreme.getThisPlugin();
            if (plugin != null)
            {
                plugin.prettyLog(Level.WARNING, false,
                    "A listener for " + event.getEventName() + " failed: " + cause.getMessage());
            }
        }
        catch (final RuntimeException ignore)
        {
            // No plugin to log through, which is the case this whole method exists inside.
        }
    }
}
