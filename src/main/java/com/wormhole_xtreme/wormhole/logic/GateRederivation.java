package com.wormhole_xtreme.wormhole.logic;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.Stargate3DShape;

/**
 * Works a gate's furniture out from its shape again, for a gate whose shape file has changed
 * since it was built.
 *
 * <p>A gate records where its markers are once, at detection, and never re-reads the shape.
 * That is fine until the shape file gains a marker: #28 added {@code [RD]} and {@code [RA]} to
 * two shipped shapes, and every gate already standing kept the empty marker positions it was
 * detected with. No amount of wiring will fire such a gate, because
 * {@code StargateBlockSetup.setupRedstone} places blocks at positions that are already known
 * and derives none, and {@code /wormhole regenerate} guards that call with
 * {@code isGateRedstonePowered()} -- which is false on exactly the gates that need fixing.
 *
 * <p>So the missing step is re-derivation, and detection already knows how to do it. The gate
 * stores both of detection's inputs: the block a player clicked ({@code getGateDialLeverBlock})
 * and the direction it faces. Handing those back to {@link StargateHelper#checkStargate} with
 * the shape as it is <em>now</em> reproduces the detection that built the gate, against today's
 * file.
 *
 * <h2>What it copies, and what it deliberately does not</h2>
 *
 * <p>Only the furniture: the three redstone markers, the iris lever, the dial sign and the name
 * sign holder. Not the frame, the portal, the animation waves or the arrival point.
 *
 * <p>That line is where it is because re-derivation runs unattended here, unlike
 * {@code /wormhole refresh}, which is always a player standing at one gate clicking its button.
 * Rewriting a gate's frame and portal lists is what {@code refresh} exists for and what it takes
 * a deliberate act to ask for; the arrival point has its own recomputation in
 * {@code RegenerateCommand} already. Markers are the part that a shape file changing actually
 * invalidates.
 *
 * <p>And a marker is only ever added or moved, never cleared: a fresh detection that finds no
 * {@code :IA} leaves an iris lever a gate already has alone. A shape that has <em>lost</em> a
 * marker is a different question -- the block is still standing in the world, and taking it up
 * silently would be a surprise -- so this reports what it changed and leaves that decision to
 * whoever reads it.
 */
public final class GateRederivation
{
    /** How far re-derivation got. */
    public enum Result
    {
        /** The gate has no 3D shape to re-read -- either none was resolved, or it is 2D. */
        NO_SHAPE,

        /** No dial lever block or no facing, so detection has nothing to anchor on. */
        NO_ANCHOR,

        /** Detection ran and did not find the gate, so nothing was touched. */
        NOT_DETECTED,

        /** Detection ran and the gate's markers were brought up to date. */
        REDERIVED
    }

    /**
     * What re-derivation came to, and what it moved.
     *
     * @param result
     *            how far it got
     * @param changes
     *            one line per marker that was added or moved, empty when nothing changed
     */
    public record Outcome(Result result, List<String> changes)
    {
        /** @return true if anything actually moved */
        public boolean changedAnything()
        {
            return !changes.isEmpty();
        }
    }

    /** Static helpers only. */
    private GateRederivation()
    {
    }

    /**
     * Re-reads the gate's shape and brings its markers up to date.
     *
     * @param gate
     *            the gate to re-derive, which is modified in place only on success
     * @return what happened, and what moved
     */
    public static Outcome rederive(final Stargate gate)
    {
        if (!(gate.getGateShape() instanceof Stargate3DShape shape))
        {
            return new Outcome(Result.NO_SHAPE, List.of());
        }
        final Block button = gate.getGateDialLeverBlock();
        final BlockFace facing = gate.getGateFacing();
        if ((button == null) || (facing == null))
        {
            return new Outcome(Result.NO_ANCHOR, List.of());
        }
        // Detection reads the world, so this needs the gate's chunk. That is acceptable
        // because regenerate is an admin naming one gate; it is why there is no -all form.
        final Stargate fresh = StargateHelper.checkStargate(button, facing, shape);
        if (fresh == null)
        {
            return new Outcome(Result.NOT_DETECTED, List.of());
        }
        return new Outcome(Result.REDERIVED, copyMarkers(gate, fresh));
    }

    /**
     * Moves the freshly detected marker positions onto the gate that is really registered.
     *
     * <p>The fresh gate is a detached object detection just built; it is read for its marker
     * positions and thrown away. Copying markers rather than swapping the object is what keeps
     * the gate's identity, network, owner, IDC and open state exactly as they were -- none of
     * which detection knows anything about.
     *
     * @param gate
     *            the registered gate
     * @param fresh
     *            what detection found against the current shape
     * @return one line per marker that moved
     */
    private static List<String> copyMarkers(final Stargate gate, final Stargate fresh)
    {
        final List<String> changes = new ArrayList<>();

        if (moved(gate.getGateRedstoneDialActivationBlock(), fresh.getGateRedstoneDialActivationBlock()))
        {
            gate.setGateRedstoneDialActivationBlock(fresh.getGateRedstoneDialActivationBlock());
            changes.add("redstone dial input");
        }
        if (moved(gate.getGateRedstoneSignActivationBlock(), fresh.getGateRedstoneSignActivationBlock()))
        {
            gate.setGateRedstoneSignActivationBlock(fresh.getGateRedstoneSignActivationBlock());
            changes.add("redstone sign cycler");
        }
        if (moved(gate.getGateRedstoneGateActivatedBlock(), fresh.getGateRedstoneGateActivatedBlock()))
        {
            gate.setGateRedstoneGateActivatedBlock(fresh.getGateRedstoneGateActivatedBlock());
            changes.add("redstone gate-activated output");
        }
        // Set from the presence of a dial input, the same way detection sets it. A gate that
        // has just gained one is redstone powered now whether or not it was when it was built.
        if (fresh.isGateRedstonePowered() && !gate.isGateRedstonePowered())
        {
            gate.setGateRedstonePowered(true);
        }
        if (moved(gate.getGateIrisLeverBlock(), fresh.getGateIrisLeverBlock()))
        {
            gate.setGateIrisLeverBlock(fresh.getGateIrisLeverBlock());
            changes.add("iris lever");
        }
        if (moved(gate.getGateNameBlockHolder(), fresh.getGateNameBlockHolder()))
        {
            gate.setGateNameBlockHolder(fresh.getGateNameBlockHolder());
            changes.add("name sign");
        }
        copyDialSign(gate, fresh, changes);
        return changes;
    }

    /**
     * Reattaches the DHD sign, which is the case #54 asked for.
     *
     * <p>A sign that is standing but not bound to the gate -- because it was placed after the
     * gate was built, or because the gate was detected from a shape that had no {@code :D}
     * -- leaves the gate unable to choose a destination and saying nothing about why. Detection
     * only records a dial sign when a real wall sign is actually there, so a non-null result
     * here means one is standing now.
     *
     * @param gate
     *            the registered gate
     * @param fresh
     *            what detection found
     * @param changes
     *            collects a line if the sign moved or arrived
     */
    private static void copyDialSign(final Stargate gate, final Stargate fresh, final List<String> changes)
    {
        if (fresh.getGateDialSignBlock() == null)
        {
            return;
        }
        if (!moved(gate.getGateDialSignBlock(), fresh.getGateDialSignBlock()))
        {
            return;
        }
        gate.setGateDialSignBlock(fresh.getGateDialSignBlock());
        gate.setGateDialSign(fresh.getGateDialSign());
        gate.setGateSignPowered(true);
        changes.add("dial sign");
    }

    /**
     * Whether a freshly derived marker is somewhere the gate does not already have it.
     *
     * <p>False when detection found nothing, which is what makes this only ever add or move a
     * marker: a shape that no longer declares one leaves what the gate already has alone.
     *
     * @param current
     *            where the gate has the marker, or null if it has none
     * @param derived
     *            where detection puts it now, or null if the shape declares none
     * @return true if the gate should take the derived position
     */
    private static boolean moved(final Block current, final Block derived)
    {
        if (derived == null)
        {
            return false;
        }
        if (current == null)
        {
            return true;
        }
        return (current.getX() != derived.getX())
            || (current.getY() != derived.getY())
            || (current.getZ() != derived.getZ())
            || !sameWorld(current, derived);
    }

    /**
     * Whether two blocks are in the same world, by name.
     *
     * <p>By name rather than by reference because a gate reloaded from disk and a block read
     * fresh out of the server can be handed different {@code World} objects for the same world.
     *
     * @param current
     *            one block
     * @param derived
     *            the other
     * @return true if both name the same world, or neither has one
     */
    private static boolean sameWorld(final Block current, final Block derived)
    {
        if ((current.getWorld() == null) || (derived.getWorld() == null))
        {
            return current.getWorld() == derived.getWorld();
        }
        return current.getWorld().getName().equals(derived.getWorld().getName());
    }
}
