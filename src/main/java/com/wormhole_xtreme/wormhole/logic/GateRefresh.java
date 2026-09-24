package com.wormhole_xtreme.wormhole.logic;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.wormhole_xtreme.wormhole.command.CommandUtilities;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateNetwork;

/**
 * Detects a gate again from scratch and puts the fresh geometry in place of the old, keeping
 * everything that belongs to the gate rather than its blocks: name, owner, iris code, network.
 *
 * <p>What {@code /wormhole refresh} did on a DHD click, now the first step of
 * {@code /wormhole gate regenerate}. Nothing in the world is touched: the old registration is
 * dropped without destroying a block, and the fresh gate is registered and saved.
 */
public final class GateRefresh
{
    /** Static helpers only. */
    private GateRefresh()
    {
    }

    /**
     * Re-detects a gate at its DHD and, if the whole frame matches a shape, takes that geometry.
     *
     * @param existing
     *            the gate as registered
     * @param button
     *            its DHD button, or the block a player clicked
     * @param facing
     *            the facing to try first, or null; the four horizontal facings are tried after it
     * @return the fresh gate, now registered and saved in the old one's place, or null if the
     *         gate is open or dialling, or no shape matches its whole frame (nothing changed)
     */
    public static Stargate refresh(final Stargate existing, final Block button, final BlockFace facing)
    {
        if ((existing == null) || (button == null) || existing.isGateActive() || existing.isGateLightsActive())
        {
            // An open gate's partner holds this object; swapping it mid-wormhole would strand that link.
            return null;
        }
        final Stargate fresh = redetect(button, facing);
        if (fresh == null)
        {
            return null;
        }
        // Read first: removing an iris-coded gate opens its iris, so afterwards it always reads open.
        final boolean irisWasShut = existing.isGateIrisActive();
        // Not announced: the gate is registered again straight away, so telling listeners it was
        // removed would have them discard their records on every regenerate.
        CommandUtilities.gateRemove(existing, false, false);
        carryOverMetadata(existing, fresh, irisWasShut);
        StargateManager.registerStargate(fresh);
        StargateDBManager.saveStargate(fresh);
        return fresh;
    }

    /**
     * Detects the gate at this block, from scratch, trying the given facing and then the four
     * horizontal ones: somebody standing at a gate may well click its side.
     */
    private static Stargate redetect(final Block button, final BlockFace facing)
    {
        if (facing != null)
        {
            final Stargate found = StargateHelper.checkStargate(button, facing);
            if (found != null)
            {
                return found;
            }
        }
        for (final BlockFace face : new BlockFace[] { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST })
        {
            if (face == facing)
            {
                continue;
            }
            final Stargate found = StargateHelper.checkStargate(button, face);
            if (found != null)
            {
                return found;
            }
        }
        return null;
    }

    /**
     * Every setting {@code gate edit} can make (#440): custom materials and timings, woosh depth,
     * redstone, the iris default, the ring pattern, the iris animation and a chosen group.
     */
    static void carryOverSettings(final Stargate existing, final Stargate fresh)
    {
        // Only a frame material the frame is still built from. Older versions snapshotted the
        // shape's default into this field, and `regen -fill` would lay that into the new frame.
        final org.bukkit.Material structure = existing.getGateCustomStructureMaterial();
        final boolean frameMatches = structure == fresh.getEffectiveStructureMaterial();
        fresh.setGateCustom(existing.isGateCustom());
        fresh.setGateCustomStructureMaterial(frameMatches ? structure : null);
        fresh.setGateCustomPortalMaterial(existing.getGateCustomPortalMaterial());
        fresh.setGateCustomLightMaterial(existing.getGateCustomLightMaterial());
        fresh.setGateCustomIrisMaterial(existing.getGateCustomIrisMaterial());
        fresh.setGateCustomWooshTicks(existing.getGateCustomWooshTicks());
        fresh.setGateCustomLightTicks(existing.getGateCustomLightTicks());
        fresh.setGateCustomWooshDepth(existing.getGateCustomWooshDepth());
        fresh.setGateCustomWooshDepthSquared(existing.getGateCustomWooshDepthSquared());
        fresh.setGateRedstonePowered(existing.isGateRedstonePowered());
        fresh.setGateIrisDefaultActive(existing.isGateIrisDefaultActive());
        fresh.setGateDialSpin(existing.getGateDialSpin());
        fresh.setGateIrisAnimation(existing.getGateIrisAnimation());
        if (existing.isGateMaterialGroupChosen())
        {
            fresh.chooseGateMaterialGroup(existing.getGateMaterialGroup());
        }
    }

    /**
     * Copies everything that belongs to the gate rather than to its blocks. The fresh gate is saved
     * straight afterwards, so anything dropped here is dropped for good.
     *
     * @param irisWasShut
     *            whether the old gate's iris was shut before it was removed
     */
    static void carryOverMetadata(final Stargate existing, final Stargate fresh, final boolean irisWasShut)
    {
        final String oldName = existing.getGateName();
        final String oldIdc = existing.getGateIrisDeactivationCode();
        final StargateNetwork oldNet = existing.getGateNetwork();

        fresh.setGateName(oldName);
        fresh.setGateOwner(existing.getGateOwner());
        // Stored, not displayed: copying the fallback would set the owner id as this gate's
        // display name.
        fresh.setGateOwnerName(existing.getStoredGateOwnerName());
        carryOverSettings(existing, fresh);
        // After the settings: completeGate sets up the redstone the flag above asks for.
        fresh.completeGate(oldName, (oldIdc != null) ? oldIdc : "");
        // A shut iris stays shut; an idle gate's iris is only ever shut by choice (#440). A toggle,
        // not a set: it shuts only because the guard has just seen it open. False keeps the default.
        if (irisWasShut && !fresh.isGateIrisActive())
        {
            fresh.toggleIrisActive(false);
        }
        if (oldNet != null)
        {
            fresh.setGateNetwork(oldNet);
            StargateManager.addGateToNetwork(fresh, oldNet.getNetworkName());
        }
    }
}
