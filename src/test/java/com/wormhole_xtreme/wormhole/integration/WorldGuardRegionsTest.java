package com.wormhole_xtreme.wormhole.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;

import com.wormhole_xtreme.wormhole.integration.RegionFlags.Action;

/**
 * Claiming the two flags in WorldGuard's registry.
 *
 * <p>WorldGuard keeps its registry across a {@code /reload} while this plugin's classes are
 * replaced, so the flag is often there already. Registering it again throws, and a plugin that
 * took that as failure would stop restricting anything after the first reload.
 */
class WorldGuardRegionsTest
{
    private final FlagRegistry registry = mock(FlagRegistry.class);

    /** A fresh flag is registered as a state flag that allows by default, so installing denies nothing. */
    @Test
    void aFreshFlagIsRegisteredAllowingByDefault()
    {
        final StateFlag flag = WorldGuardRegions.claim(registry, Action.USE);

        verify(registry).register(flag);
        assertEquals("wormhole-use", flag.getName());
        assertEquals(StateFlag.State.ALLOW, flag.getDefault(),
            "a region with no value set must allow, or installing this closes every gate");
    }

    /** A state flag already under the name, left by a reload, is used rather than registered again. */
    @Test
    void aStateFlagAlreadyThereIsReused()
    {
        final StateFlag ours = new StateFlag("wormhole-build", true);
        doReturn(ours).when(registry).get("wormhole-build");

        assertSame(ours, WorldGuardRegions.claim(registry, Action.BUILD));
        verify(registry, never()).register(any());
    }

    /** One registered between the look and the register is picked up after the conflict. */
    @Test
    void aFlagRegisteredMeanwhileIsReusedAfterTheConflict()
    {
        final StateFlag theirs = new StateFlag("wormhole-use", true);
        doReturn(null, theirs).when(registry).get("wormhole-use");
        doThrow(new FlagConflictException("taken")).when(registry).register(any());

        assertSame(theirs, WorldGuardRegions.claim(registry, Action.USE));
    }

    /** A flag of another kind under the name cannot be asked allow-or-deny, so the hook is refused. */
    @Test
    void aFlagOfAnotherKindUnderTheNameIsRefused()
    {
        doReturn(new StringFlag("wormhole-use")).when(registry).get("wormhole-use");

        assertThrows(IllegalStateException.class, () -> WorldGuardRegions.claim(registry, Action.USE));
        verify(registry, never()).register(any());
    }
}
