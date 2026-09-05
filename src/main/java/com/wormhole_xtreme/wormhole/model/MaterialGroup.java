package com.wormhole_xtreme.wormhole.model;

import org.bukkit.Material;

/**
 * A named set of materials a gate is built from — the Standard obsidian gate, the
 * Atlantis lapis one, and so on.
 *
 * <p>Palettes are deliberately separate from {@link StargateShape}: a shape describes
 * geometry, a group describes what that geometry is made of. Before this split, every
 * material variant needed its own {@code .shape} file duplicating the entire layout, and
 * because gate detection scans every registered shape in turn, each variant added a full
 * extra geometry scan to every detection attempt. Groups are resolved with a single map
 * lookup keyed on the structure material actually found in the world, so a server can
 * offer any number of palettes at no per-detection cost.
 *
 * <p>Instances are immutable and safe to share across threads.
 */
public final class MaterialGroup
{
    /** The group name as written in config.yml. */
    private final String name;

    /** Material the gate frame is built from. Identifies the group during detection. */
    private final Material structureMaterial;

    /** Material shown in the gate interior while a wormhole is open. */
    private final Material portalMaterial;

    /** Material the iris is made of when engaged. */
    private final Material irisMaterial;

    /** Material the light blocks become while the gate is active. */
    private final Material lightMaterial;

    /** Wall-sign material used for the gate's name sign. */
    private final Material signMaterial;

    /**
     * Material an unlit chevron is built from, or null if this palette has none.
     *
     * <p>The only optional material here, and the only one that is not purely a styling
     * choice: it changes what a player has to build for the gate to be found at all. A
     * palette that names one widens what detection accepts at a chevron position rather
     * than replacing it, so gates built before it was set are unaffected either way.
     */
    private final Material chevronMaterial;

    /**
     * Instantiates a new material group with no distinct chevrons.
     *
     * @param name
     *            the group name
     * @param structureMaterial
     *            the frame material, which identifies this group during detection
     * @param portalMaterial
     *            the open-wormhole material
     * @param irisMaterial
     *            the engaged-iris material
     * @param lightMaterial
     *            the active-light material
     * @param signMaterial
     *            the wall-sign material for the gate name sign
     */
    public MaterialGroup(final String name, final Material structureMaterial, final Material portalMaterial,
        final Material irisMaterial, final Material lightMaterial, final Material signMaterial)
    {
        this(name, structureMaterial, portalMaterial, irisMaterial, lightMaterial, signMaterial, null);
    }

    /**
     * Instantiates a new material group.
     *
     * @param name
     *            the group name
     * @param structureMaterial
     *            the frame material, which identifies this group during detection
     * @param portalMaterial
     *            the open-wormhole material
     * @param irisMaterial
     *            the engaged-iris material
     * @param lightMaterial
     *            the active-light material
     * @param signMaterial
     *            the wall-sign material for the gate name sign
     * @param chevronMaterial
     *            the unlit-chevron material, or null if this palette has no distinct chevrons
     */
    public MaterialGroup(final String name, final Material structureMaterial, final Material portalMaterial,
        final Material irisMaterial, final Material lightMaterial, final Material signMaterial,
        final Material chevronMaterial)
    {
        this.name = name;
        this.structureMaterial = structureMaterial;
        this.portalMaterial = portalMaterial;
        this.irisMaterial = irisMaterial;
        this.lightMaterial = lightMaterial;
        this.signMaterial = signMaterial;
        this.chevronMaterial = chevronMaterial;
    }

    /**
     * Gets the group name.
     *
     * @return the name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Gets the frame material. This is what identifies the group during gate detection,
     * so it must be unique across groups.
     *
     * @return the structure material
     */
    public Material getStructureMaterial()
    {
        return structureMaterial;
    }

    /**
     * Gets the open-wormhole material.
     *
     * @return the portal material
     */
    public Material getPortalMaterial()
    {
        return portalMaterial;
    }

    /**
     * Gets the engaged-iris material.
     *
     * @return the iris material
     */
    public Material getIrisMaterial()
    {
        return irisMaterial;
    }

    /**
     * Gets the active-light material.
     *
     * @return the light material
     */
    public Material getLightMaterial()
    {
        return lightMaterial;
    }

    /**
     * Gets the wall-sign material used for the gate's name sign. A nether-themed palette
     * looks wrong with an oak sign, so this belongs to the palette like everything else.
     *
     * @return the sign material
     */
    public Material getSignMaterial()
    {
        return signMaterial;
    }

    /**
     * Gets the material an unlit chevron is built from.
     *
     * @return the chevron material, or null if this palette has no distinct chevrons
     */
    public Material getChevronMaterial()
    {
        return chevronMaterial;
    }

    @Override
    public String toString()
    {
        return "MaterialGroup[" + name + " structure=" + structureMaterial + "]";
    }
}
