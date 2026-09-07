package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * When one gate refuses to dial another.
 *
 * <p>Five rules stand between a dial and a wormhole, and every one of them is about not
 * dropping somebody somewhere they should not arrive: through a closed iris, into a gate
 * already carrying traffic, or into one another gate is mid-connection with.
 *
 * <p>None of it was covered. The tests that exercise dialling stub {@code
 * Stargate.dialStargate} outright, so they never reach the rules underneath.
 */
class DialRefusalTest
{
    private Stargate gate;

    @BeforeEach
    void setUp() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));

        gate = mock(Stargate.class);
        when(gate.getGateName()).thenReturn("alpha");
        when(gate.getGateActivateTaskId()).thenReturn(0);
        // Enough of a location for the forced path, which gets as far as scheduling a
        // chunk load before the local activation it is really waiting on fails.
        final World world = mock(World.class);
        final Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        when(gate.getGatePlayerTeleportLocation()).thenReturn(new Location(world, 0, 64, 0));
    }

    @AfterEach
    void tearDown() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, null);
    }

    /** A target that is fine to dial, which each test then spoils in one specific way. */
    private static Stargate dialableTarget()
    {
        final Stargate target = mock(Stargate.class);
        when(target.getGateName()).thenReturn("bravo");
        when(target.isGateIrisActive()).thenReturn(false);
        when(target.isGateActive()).thenReturn(false);
        when(target.getGateTarget()).thenReturn(null);
        return target;
    }

    /** Nothing to dial. */
    @Test
    void aNullTargetIsRefused()
    {
        assertFalse(StargateDialManager.dialStargate(gate, null, false));
        verify(gate, never()).setGateTarget(any());
    }

    /** A closed iris is the whole point of an iris. */
    @Test
    void aTargetWithItsIrisClosedIsRefused()
    {
        final Stargate target = dialableTarget();
        when(target.isGateIrisActive()).thenReturn(true);

        assertFalse(StargateDialManager.dialStargate(gate, target, false));
        verify(gate, never()).setGateTarget(any());
    }

    /** A gate already open is carrying somebody else's traffic. */
    @Test
    void aTargetAlreadyActiveIsRefused()
    {
        final Stargate target = dialableTarget();
        when(target.isGateActive()).thenReturn(true);

        assertFalse(StargateDialManager.dialStargate(gate, target, false));
        verify(gate, never()).setGateTarget(any());
    }

    /** A gate that has already picked a target is mid-connection. */
    @Test
    void aTargetThatAlreadyHasATargetIsRefused()
    {
        final Stargate target = dialableTarget();
        when(target.getGateTarget()).thenReturn(mock(Stargate.class));

        assertFalse(StargateDialManager.dialStargate(gate, target, false));
        verify(gate, never()).setGateTarget(any());
    }

    /**
     * A third gate already dialled into the target blocks this dial.
     *
     * <p>The target itself can look perfectly free -- not active, no target of its own --
     * while somebody else is already on their way into it.
     */
    @Test
    void aTargetAnotherActiveGateIsDiallingIsRefused()
    {
        final Stargate target = dialableTarget();
        final Stargate other = mock(Stargate.class);
        when(other.getGateName()).thenReturn("charlie");
        when(other.getGateTarget()).thenReturn(target);
        when(other.isGateActive()).thenReturn(true);

        try (MockedStatic<StargateManager> manager = mockStatic(StargateManager.class))
        {
            manager.when(StargateManager::getAllGates).thenReturn(Collections.singletonList(other));

            assertFalse(StargateDialManager.dialStargate(gate, target, false));
            verify(gate, never()).setGateTarget(any());
        }
    }

    /**
     * A third gate pointed at the target but not active does not block it.
     *
     * <p>A stale target on a closed gate is not traffic, and treating it as traffic would
     * make a gate undialable until somebody noticed and cleared it.
     */
    @Test
    void aTargetOnlyAnInactiveGatePointsAtIsNotBlocked()
    {
        final Stargate target = dialableTarget();
        // Lights active sends this down the "already lit" path, which returns false without
        // dialling -- enough to show the third-gate rule did not fire first.
        when(target.isGateLightsActive()).thenReturn(true);

        final Stargate stale = mock(Stargate.class);
        when(stale.getGateName()).thenReturn("charlie");
        when(stale.getGateTarget()).thenReturn(target);
        when(stale.isGateActive()).thenReturn(false);

        try (MockedStatic<StargateManager> manager = mockStatic(StargateManager.class))
        {
            manager.when(StargateManager::getAllGates).thenReturn(Collections.singletonList(stale));

            assertFalse(StargateDialManager.dialStargate(gate, target, false));

            // It got past the third-gate rule: the refusal came from the lights check, and
            // the scan never found a reason of its own.
            verify(stale).isGateActive();
        }
    }

    /**
     * The dialling gate's own stale target does not block it dialling that same gate.
     *
     * <p>Re-dialling somewhere you were already pointed at is ordinary, so the scan skips
     * the gate doing the dialling.
     */
    @Test
    void aGateIsNotBlockedByItsOwnTarget()
    {
        final Stargate target = dialableTarget();
        when(gate.getGateTarget()).thenReturn(target);
        when(gate.isGateActive()).thenReturn(true);

        try (MockedStatic<StargateManager> manager = mockStatic(StargateManager.class);
             MockedStatic<com.wormhole_xtreme.wormhole.utils.WorldUtils> world =
                 mockStatic(com.wormhole_xtreme.wormhole.utils.WorldUtils.class))
        {
            manager.when(StargateManager::getAllGates).thenReturn(Arrays.asList(gate));
            world.when(() -> com.wormhole_xtreme.wormhole.utils.WorldUtils
                .scheduleChunkLoad(any(Block.class))).thenAnswer(invocation -> null);

            StargateDialManager.dialStargate(gate, target, false);

            // Reaching the connection is the assertion. Checking only the return value would
            // prove nothing: without the skip the scan refuses and returns false, and with it
            // the dial goes ahead and still returns false when local activation fails.
            world.verify(() -> com.wormhole_xtreme.wormhole.utils.WorldUtils
                .scheduleChunkLoad(any(Block.class)));
        }
    }

    /**
     * Forcing gets past every refusal.
     *
     * <p>The target here is spoiled three ways at once -- iris closed, already active, and
     * already carrying a target -- and the dial still reaches the connection attempt.
     *
     * <p>Note the iris check is still <em>called</em> when forced: the guard reads
     * {@code target.isGateIrisActive() && !force}, so the force test comes last. Harmless,
     * but it means "was it asked" is not the same question as "did it refuse".
     */
    @Test
    void forcingSkipsTheRefusals()
    {
        final Stargate target = dialableTarget();
        when(target.isGateIrisActive()).thenReturn(true);
        when(target.isGateActive()).thenReturn(true);
        when(target.getGateTarget()).thenReturn(mock(Stargate.class));
        // Lights active would stop a normal dial here too; forced, it goes on regardless.
        when(target.isGateLightsActive()).thenReturn(true);

        try (MockedStatic<StargateManager> manager = mockStatic(StargateManager.class);
             MockedStatic<com.wormhole_xtreme.wormhole.utils.WorldUtils> world =
                 mockStatic(com.wormhole_xtreme.wormhole.utils.WorldUtils.class))
        {
            manager.when(StargateManager::getAllGates).thenReturn(Collections.emptyList());
            // Chunk loading is plumbing this test is not about; stub it out so the failure
            // that matters is the local activation, not a missing chunk.
            world.when(() -> com.wormhole_xtreme.wormhole.utils.WorldUtils
                .scheduleChunkLoad(any(Block.class))).thenAnswer(invocation -> null);

            // The dial itself still fails -- the local gate never activates against a bare
            // mock -- but it fails past the rules, not at them. Reaching the chunk load is
            // what proves that: every refusal returns before the connection is attempted.
            StargateDialManager.dialStargate(gate, target, true);

            world.verify(() -> com.wormhole_xtreme.wormhole.utils.WorldUtils
                .scheduleChunkLoad(any(Block.class)));
            verify(gate, never()).setGateTarget(any());
        }
    }
}
