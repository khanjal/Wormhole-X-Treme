package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

/**
 * Where the woosh's waves come from, now that there is only one animation path.
 *
 * <p>The woosh used to be two entirely separate implementations inside
 * {@link StargateAnimator#animateOpening}, picked between by whether the gate had any
 * authored woosh blocks. A shape with {@code :W#N} markers replayed them through one state
 * machine counting {@code gateAnimationStep3D}; a shape without them ran a second one
 * counting {@code gateAnimationStep2D}, deriving its waves by extruding the portal face
 * outward a step at a time. The split was fork lineage rather than design, and it cost a real
 * bug: the off-by-one that left a wave drawn inside the gate on every opening existed in one
 * of the two and not the other, so nothing about maintaining the second could ever have
 * surfaced it.
 *
 * <p>Both now run through the same state machine. {@link StargateAnimator#wooshWaveCount} and
 * {@link StargateAnimator#wooshWave} are the whole of what used to differ: a shape that
 * authors waves gets them read out of the shape, and one that does not gets the identical
 * outward extrusion derived on demand from its portal face. These tests pin that the derived
 * version really does reproduce what the deleted path drew, since nothing else does now.
 */
public class WooshWaveTest
{
    /**
     * A portal block at a plain coordinate. The world is deliberately null -- both
     * {@code drawBlocks} and {@code undrawBlocks} read only block coordinates off these and
     * resolve them against the gate's own world, so nothing here needs a live one.
     *
     * @param x the x
     * @param y the y
     * @param z the z
     * @return the location
     */
    private static Location at(final int x, final int y, final int z)
    {
        return new Location(null, x, y, z);
    }

    private static Stargate gateWithPortal(final BlockFace facing, final Location... portal)
    {
        final Stargate gate = new Stargate();
        gate.setGateFacing(facing);
        for (final Location l : portal)
        {
            gate.getGatePortalBlocks().add(l);
        }
        return gate;
    }

    @Test
    public void aShapeThatAuthorsItsOwnWavesIsCountedByHowManyItAuthored()
    {
        final Stargate gate = new Stargate();
        gate.getGateWooshBlocks().add(new ArrayList<Location>());
        gate.getGateWooshBlocks().add(new ArrayList<Location>());

        assertEquals(2, StargateAnimator.wooshWaveCount(gate),
            "a shape's own :W# waves are the authority on how many waves its woosh has");
    }

    @Test
    public void aShapeWithNoAuthoredWavesFallsBackToItsConfiguredDepth()
    {
        // This is the case that used to take the separate 2D path entirely. It has to keep
        // producing a woosh, or collapsing the two paths would silently delete the animation
        // for every gate built from a shape without :W# markers.
        final Stargate gate = new Stargate();
        gate.setGateCustom(true);
        gate.setGateCustomWooshDepth(4);

        assertEquals(4, StargateAnimator.wooshWaveCount(gate),
            "a shape with no authored waves must still get a woosh, derived to its configured depth");
    }

    @Test
    public void aGateWithNeitherAuthoredWavesNorADepthHasNoWooshAtAll()
    {
        // Every shipped shape declares :W# markers and none declares WOOSH_DEPTH, so this is
        // the shape of a hand-written shape file that asked for no woosh. It must read as
        // zero waves rather than as "one wave of nothing", which would draw and undraw an
        // empty layer and play a kawoosh for an animation that never happens.
        assertEquals(0, StargateAnimator.wooshWaveCount(new Stargate()),
            "no authored waves and no depth means no woosh, not an empty one");
    }

    @Test
    public void theFirstDerivedWaveSitsOneBlockOutFromThePortalFace()
    {
        // The old 2D path's very first step was the portal blocks' getRelative(facing) --
        // one block out. Index 0 has to land in exactly that spot, or every derived woosh
        // starts a block off from where it used to.
        final Stargate gate = gateWithPortal(BlockFace.NORTH, at(10, 64, 20), at(11, 64, 20));

        final ArrayList<Location> wave = StargateAnimator.wooshWave(gate, 0);

        assertEquals(2, wave.size(), "one derived block per portal block, not more or fewer");
        assertEquals(19, wave.get(0).getBlockZ(), "NORTH is -Z, so one block out is z - 1");
        assertEquals(10, wave.get(0).getBlockX(), "the extrusion must not drift sideways");
        assertEquals(64, wave.get(0).getBlockY(), "nor vertically, for a horizontally-facing gate");
        assertEquals(11, wave.get(1).getBlockX(), "every portal block extrudes, not just the first");
    }

    @Test
    public void eachLaterWaveIsOneBlockFurtherOutThanTheOneBefore()
    {
        // The old path reached wave N by extruding the *previous* wave one more step, so
        // wave N sat N blocks out. Expressed as a function of the index instead of as
        // accumulated state, that has to stay true: index 2 is three blocks out, not two.
        final Stargate gate = gateWithPortal(BlockFace.EAST, at(0, 64, 0));

        assertEquals(1, StargateAnimator.wooshWave(gate, 0).get(0).getBlockX());
        assertEquals(2, StargateAnimator.wooshWave(gate, 1).get(0).getBlockX());
        assertEquals(3, StargateAnimator.wooshWave(gate, 2).get(0).getBlockX(),
            "wave at index N must sit N+1 blocks out -- the deepest wave of a depth-3 woosh "
                + "is three blocks from the portal, matching what the old path extruded to");
    }

    @Test
    public void aGateLyingOnItsBackExtrudesUpwardsRatherThanSideways()
    {
        // The Horizontal shape faces UP, and the old path used getRelative(facing), which
        // carries the Y component like any other. Multiplying the facing's offsets by the
        // step keeps that true -- dropping getModY() here would leave every horizontal
        // gate's woosh drawing on top of its own portal.
        final Stargate gate = gateWithPortal(BlockFace.UP, at(5, 70, 5));

        final Location first = StargateAnimator.wooshWave(gate, 0).get(0);

        assertEquals(71, first.getBlockY(), "UP is +Y, so the wave rises out of the portal");
        assertEquals(5, first.getBlockX());
        assertEquals(5, first.getBlockZ());
    }

    @Test
    public void aGateWhoseFacingIsNotKnownYetDerivesNothingRatherThanGuessing()
    {
        // Facing is resolved during detection; a gate that has not been through that has no
        // direction to extrude along. Returning null puts it down the same branch an
        // authored-but-empty wave already takes, which draws nothing, rather than throwing
        // inside a scheduler tick.
        final Stargate gate = new Stargate();
        gate.getGatePortalBlocks().add(at(0, 64, 0));

        assertNull(StargateAnimator.wooshWave(gate, 0),
            "no facing means no derivable wave; the animation must skip it, not guess a direction");
    }
}
