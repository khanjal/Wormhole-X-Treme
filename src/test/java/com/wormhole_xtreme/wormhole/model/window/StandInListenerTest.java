package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * A view's stand-ins (#296) are real entities kept out of the game, while real creatures are left
 * alone.
 *
 * <p>Without this a stand-in could be hurt or killed for its copied armour, leashed or traded with,
 * burn in daylight its creature never sees, turn into a new entity nobody holds, draw a zombie to a
 * villager's stand-in behind the wall, or press a plate it stands on. The events are mocked: several
 * have constructors that differ across the supported versions.
 */
class StandInListenerTest
{
    private final StandInListener listener = new StandInListener();
    private Zombie standIn;
    private Zombie real;

    @BeforeEach
    void setUp()
    {
        standIn = zombie();
        real = zombie();
        StandIns.track(standIn);
    }

    @AfterEach
    void tearDown()
    {
        StandIns.untrack(standIn);
    }

    /** Every event that would act on a stand-in is cancelled for it, and for nothing else. */
    @Test
    void whatWouldActOnAStandInIsRefusedAndOnARealCreatureIsNot()
    {
        check("damage", EntityDamageEvent.class, EntityDamageEvent::getEntity, StandInListener::onDamage);
        check("a click", PlayerInteractEntityEvent.class, PlayerInteractEntityEvent::getRightClicked, StandInListener::onInteract);
        check("a click at a point", PlayerInteractAtEntityEvent.class, PlayerInteractAtEntityEvent::getRightClicked,
            StandInListener::onInteractAt);
        check("catching fire", EntityCombustEvent.class, EntityCombustEvent::getEntity, StandInListener::onCombust);
        check("turning into something else", EntityTransformEvent.class, EntityTransformEvent::getEntity,
            StandInListener::onTransform);
        check("being hunted", EntityTargetEvent.class, EntityTargetEvent::getTarget, StandInListener::onTarget);
        check("pressing a plate", EntityInteractEvent.class, EntityInteractEvent::getEntity, StandInListener::onPress);
        check("going through a portal", EntityPortalEvent.class, EntityPortalEvent::getEntity, StandInListener::onPortal);
        final Slime slime = mock(Slime.class);
        when(slime.getUniqueId()).thenReturn(UUID.randomUUID());
        StandIns.track(slime);
        try
        {
            check("splitting", SlimeSplitEvent.class, SlimeSplitEvent::getEntity, StandInListener::onSplit, slime,
                mock(Slime.class));
        }
        finally
        {
            StandIns.untrack(slime);
        }
        check("dropping something", EntityDropItemEvent.class, EntityDropItemEvent::getEntity, StandInListener::onDrop);
        check("changing a block", EntityChangeBlockEvent.class, EntityChangeBlockEvent::getEntity,
            StandInListener::onChangeBlock);
    }

    /** A stand-in killed anyway, by a command, drops nothing; a real creature's drops are left. */
    @Test
    void aStandInKilledAnywayDropsNothing()
    {
        final List<ItemStack> standInDrops = new ArrayList<>(List.of(mock(ItemStack.class)));
        final EntityDeathEvent standInDeath = mock(EntityDeathEvent.class);
        when(standInDeath.getEntity()).thenReturn(standIn);
        when(standInDeath.getDrops()).thenReturn(standInDrops);
        final List<ItemStack> realDrops = new ArrayList<>(List.of(mock(ItemStack.class)));
        final EntityDeathEvent realDeath = mock(EntityDeathEvent.class);
        when(realDeath.getEntity()).thenReturn(real);
        when(realDeath.getDrops()).thenReturn(realDrops);

        listener.onDeath(standInDeath);
        listener.onDeath(realDeath);

        assertTrue(standInDrops.isEmpty(), "the copied armour is not loot");
        verify(standInDeath).setDroppedExp(0);
        assertEquals(1, realDrops.size(), "a real zombie drops what it drops");
        verify(realDeath, never()).setDroppedExp(0);
    }

    /**
     * Every handler is one Bukkit calls, and every refusal runs first, so a plugin listening at the
     * normal priority already sees the event cancelled.
     *
     * <p>A handler that lost its annotation compiles, passes every test that calls it directly, and
     * is never called by the server.
     */
    @Test
    void everyHandlerIsAnEventHandlerAndEveryRefusalRunsFirst()
    {
        int handlers = 0;
        int refusals = 0;
        for (final Method method : StandInListener.class.getDeclaredMethods())
        {
            if (Modifier.isPublic(method.getModifiers()) && method.getName().startsWith("on"))
            {
                handlers++;
                final EventHandler handler = method.getAnnotation(EventHandler.class);
                assertNotNull(handler, method.getName() + " is never called without @EventHandler");
                if (Cancellable.class.isAssignableFrom(method.getParameterTypes()[0]))
                {
                    refusals++;
                    assertEquals(EventPriority.LOWEST, handler.priority(), method.getName() + " should refuse first");
                    assertTrue(handler.ignoreCancelled(), method.getName() + " has nothing to refuse once cancelled");
                }
            }
        }
        assertEquals(16, handlers, "every handler counted, so a renamed one is not skipped");
        assertEquals(11, refusals, "the eleven that cancel");
    }

    /** The plugin registers the listener with the others, or none of it runs. */
    @Test
    void thePluginRegistersTheListener() throws Exception
    {
        final WormholeXTreme plugin = PluginTestSupport.install();
        try
        {
            final Server server = mock(Server.class);
            final PluginManager manager = mock(PluginManager.class);
            when(plugin.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(manager);

            WormholeXTreme.registerEvents();

            final ArgumentCaptor<Listener> registered = ArgumentCaptor.forClass(Listener.class);
            verify(manager, atLeastOnce()).registerEvents(registered.capture(), eq(plugin));
            assertTrue(registered.getAllValues().stream().anyMatch(StandInListener.class::isInstance),
                "a StandInListener among " + registered.getAllValues().size() + " registered");
        }
        finally
        {
            PluginTestSupport.remove();
        }
    }

    private <E extends Cancellable> void check(final String what, final Class<E> type, final Function<E, Entity> about,
        final BiConsumer<StandInListener, E> handler)
    {
        check(what, type, about, handler, standIn, real);
    }

    private <E extends Cancellable, T extends Entity> void check(final String what, final Class<E> type,
        final Function<E, T> about, final BiConsumer<StandInListener, E> handler, final T onStandInEntity, final T onRealEntity)
    {
        final E onStandIn = mock(type);
        final E onReal = mock(type);
        when(about.apply(onStandIn)).thenReturn(onStandInEntity);
        when(about.apply(onReal)).thenReturn(onRealEntity);

        handler.accept(listener, onStandIn);
        handler.accept(listener, onReal);

        verify(onStandIn, description(what + " on a stand-in")).setCancelled(true);
        verify(onReal, never().description(what + " on a real zombie")).setCancelled(anyBoolean());
    }

    private static Zombie zombie()
    {
        final Zombie zombie = mock(Zombie.class);
        when(zombie.getUniqueId()).thenReturn(UUID.randomUUID());
        return zombie;
    }
}
