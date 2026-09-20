package com.wormhole_xtreme.wormhole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;

/**
 * The shared teardown really does empty what a test class can fill.
 *
 * <p>Thirty-four classes were each leaving gates in {@link StargateManager}'s statics, which
 * outlive the class that wrote them because the suite runs in one fork. The suite only stayed
 * green because no test had yet asserted on an absolute count; the first one that did saw six
 * open gates where it had opened one.
 *
 * <p>These are the two halves that have to keep working for {@link GateStateGuard} to stay
 * quiet. A teardown that silently stopped draining one of them would put the leak back without
 * anything going red, which is exactly how it went unnoticed the first time.
 */
class GateStateResetTest
{
    /**
     * A gate opened by a test is closed again, and the flag agrees with the set.
     *
     * <p>Draining the set alone would leave the gate object still claiming to be active, so a
     * later test reusing that object -- or production code asked whether it is open -- would
     * get an answer the manager no longer agrees with.
     */
    @Test
    void theOpenSetIsDrainedThroughTheGatesOwnFlag()
    {
        final Stargate gate = new Stargate();
        gate.setGateName("leaked");
        gate.setGateActive(true);
        assertTrue(StargateManager.getOpenGates().contains(gate), "the gate opened");

        PluginTestSupport.forgetAllGates();

        assertFalse(StargateManager.getOpenGates().contains(gate), "the open set was drained");
        assertFalse(gate.isGateActive(), "and the gate itself no longer claims to be open");
    }

    /**
     * A gate registered by a test is gone from the registry and the block index with it.
     *
     * <p>The block index is keyed by world, so a leaked entry pins a mock {@code World} for the
     * rest of the run as well as the gate hanging off it.
     */
    @Test
    void theRegistryAndTheBlockIndexAreEmptiedTogether() throws Exception
    {
        final World world = mock(World.class);
        when(world.getName()).thenReturn("reset-test");
        final Stargate gate = new Stargate();
        gate.setGateName("leaked");
        gate.setGateWorld(world);
        gate.getGatePortalBlocks().add(new Location(world, 1, 2, 3));
        StargateManager.registerStargate(gate);
        assertTrue(StargateManager.getAllGatesUnsorted().contains(gate), "the gate registered");
        assertFalse(blockIndex().isEmpty(), "and its portal block was indexed under its world");

        PluginTestSupport.forgetAllGates();

        assertFalse(StargateManager.getAllGatesUnsorted().contains(gate),
            "the registry was emptied");
        assertTrue(blockIndex().isEmpty(),
            "and the block index with it, which otherwise pins the world it is keyed by");
    }

    /**
     * The manager's world-keyed block index, which has no reader that can see it is empty.
     */
    private static java.util.Map<?, ?> blockIndex() throws ReflectiveOperationException
    {
        return PrivateStatics.of(StargateManager.class, "gateBlocksByWorld");
    }
}
