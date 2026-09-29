package com.wormhole_xtreme.wormhole.plugin.map;

import java.util.Map;

/**
 * Everything a web map should show, at one moment.
 *
 * <p>Records all the way down, so two snapshots of an unchanged server are {@code equals} and
 * that comparison is the whole change detector. Each map is keyed by the mark's id, which is
 * derived from names and ids rather than positions, so a map can tell a moved gate from a new
 * one.
 *
 * @param gates
 *            one mark per gate
 * @param gateLinks
 *            one line per dialled pair of gates
 * @param rings
 *            one mark per ring end
 * @param ringLinks
 *            one line per ring pair
 * @param beams
 *            one mark per public beam destination
 * @param mirrors
 *            one mark per quantum mirror
 */
public record MapSnapshot(Map<String, GateMark> gates, Map<String, LineMark> gateLinks,
    Map<String, RingMark> rings, Map<String, LineMark> ringLinks, Map<String, BeamMark> beams,
    Map<String, MirrorMark> mirrors)
{
    /** Nothing to show. */
    public static final MapSnapshot EMPTY =
        new MapSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    /**
     * Copies each map, so a snapshot cannot change after it is compared.
     *
     * @param gates
     *            one mark per gate
     * @param gateLinks
     *            one line per dialled pair of gates
     * @param rings
     *            one mark per ring end
     * @param ringLinks
     *            one line per ring pair
     * @param beams
     *            one mark per public beam destination
     * @param mirrors
     *            one mark per quantum mirror
     */
    public MapSnapshot
    {
        gates = Map.copyOf(gates);
        gateLinks = Map.copyOf(gateLinks);
        rings = Map.copyOf(rings);
        ringLinks = Map.copyOf(ringLinks);
        beams = Map.copyOf(beams);
        mirrors = Map.copyOf(mirrors);
    }

    /**
     * A block span on the ground, for drawing a gate's opening as an area.
     *
     * @param minX
     *            west edge
     * @param minZ
     *            north edge
     * @param maxX
     *            east edge, past the last block
     * @param maxZ
     *            south edge, past the last block
     * @param minY
     *            bottom
     * @param maxY
     *            top, past the last block
     */
    public record Footprint(double minX, double minZ, double maxX, double maxZ, double minY, double maxY)
    {
    }

    /**
     * A gate.
     *
     * @param id
     *            stable id, from the gate's name
     * @param world
     *            world name
     * @param name
     *            the gate's name
     * @param network
     *            its network, or null for none
     * @param owner
     *            its owner's name, or null for none
     * @param open
     *            whether a wormhole is open through it
     * @param x
     *            point to mark, east-west
     * @param y
     *            point to mark, height
     * @param z
     *            point to mark, north-south
     * @param footprint
     *            its opening, or null when it has no portal blocks to measure
     */
    public record GateMark(String id, String world, String name, String network, String owner,
        boolean open, double x, double y, double z, Footprint footprint)
    {
        /**
         * This mark with the wormhole open or shut, and nothing else changed.
         *
         * @param isOpen
         *            whether a wormhole is open through it
         * @return the mark
         */
        public GateMark withOpen(final boolean isOpen)
        {
            return new GateMark(id, world, name, network, owner, isOpen, x, y, z, footprint);
        }
    }

    /**
     * A straight line between two points in one world.
     *
     * @param id
     *            stable id
     * @param world
     *            world name
     * @param label
     *            what the line is called
     * @param x1
     *            first end, east-west
     * @param y1
     *            first end, height
     * @param z1
     *            first end, north-south
     * @param x2
     *            second end, east-west
     * @param y2
     *            second end, height
     * @param z2
     *            second end, north-south
     */
    public record LineMark(String id, String world, String label,
        double x1, double y1, double z1, double x2, double y2, double z2)
    {
    }

    /**
     * One end of a ring pair.
     *
     * @param id
     *            stable id, from the pair id and which end
     * @param world
     *            world name
     * @param name
     *            what the end is called
     * @param pair
     *            how the whole pair reads
     * @param owner
     *            the pair's owner's name, or null for none
     * @param x
     *            anchor, east-west
     * @param y
     *            anchor, height
     * @param z
     *            anchor, north-south
     */
    public record RingMark(String id, String world, String name, String pair, String owner,
        double x, double y, double z)
    {
    }

    /**
     * A public beam destination.
     *
     * @param id
     *            stable id, from the destination's name
     * @param world
     *            world name
     * @param name
     *            the destination's name
     * @param x
     *            east-west
     * @param y
     *            height
     * @param z
     *            north-south
     */
    public record BeamMark(String id, String world, String name, double x, double y, double z)
    {
    }

    /**
     * A quantum mirror, at its banner.
     *
     * @param id
     *            stable id, from the mirror's name
     * @param world
     *            world name
     * @param name
     *            the mirror's name
     * @param x
     *            east-west
     * @param y
     *            height
     * @param z
     *            north-south
     */
    public record MirrorMark(String id, String world, String name, double x, double y, double z)
    {
    }
}
