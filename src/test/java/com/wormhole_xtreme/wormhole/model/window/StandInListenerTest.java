package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Cancellable;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    private <E extends Cancellable> void check(final String what, final Class<E> type, final Function<E, Entity> about,
        final BiConsumer<StandInListener, E> handler)
    {
        final E onStandIn = mock(type);
        final E onReal = mock(type);
        when(about.apply(onStandIn)).thenReturn(standIn);
        when(about.apply(onReal)).thenReturn(real);

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
