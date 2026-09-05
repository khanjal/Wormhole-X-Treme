package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * When a player-placed dial sign gets converted to the gate's own sign material.
 *
 * <p>The dial sign is the one sign this plugin does not place -- a player puts it on the
 * {@code [D]} block in whatever wood they were holding -- so a themed gate ended up with an
 * oak dial sign on a crimson frame. Matching it means replacing a block somebody else placed,
 * which is why the rule for when to do that is worth pinning rather than leaving implicit.
 */
public class DialSignMaterialMatchTest
{
    @Test
    public void aSignOfTheWrongWoodIsConverted()
    {
        assertTrue(StargateBlockSetup.shouldMatchDialSignMaterial(
            Material.OAK_WALL_SIGN, Material.CRIMSON_WALL_SIGN, true));
    }

    /**
     * A sign that already matches is left alone.
     *
     * <p>Not just an optimisation. Converting is a block replacement that reads the sign's
     * text and writes it back, and this runs on every complete and every regenerate -- so a
     * no-op that still replaced the block would be a chance to lose a line of text for
     * nothing, every time.
     */
    @Test
    public void aSignThatAlreadyMatchesIsLeftAlone()
    {
        assertFalse(StargateBlockSetup.shouldMatchDialSignMaterial(
            Material.CRIMSON_WALL_SIGN, Material.CRIMSON_WALL_SIGN, true));
    }

    @Test
    public void nothingHappensWhenTheServerHasTurnedThisOff()
    {
        assertFalse(StargateBlockSetup.shouldMatchDialSignMaterial(
            Material.OAK_WALL_SIGN, Material.CRIMSON_WALL_SIGN, false),
            "a server that wants players' own signs left alone must get exactly that");
    }

    /**
     * A gate whose sign material is not a wall sign converts nothing.
     *
     * <p>Config and shape files both name this material by hand, so it can be anything. Turning
     * a working dial sign into a block that cannot hold text would not restyle the gate, it
     * would stop the gate being dialable at all.
     */
    @Test
    public void aGateAskingForSomethingThatIsNotAWallSignIsRefused()
    {
        assertFalse(StargateBlockSetup.shouldMatchDialSignMaterial(
            Material.OAK_WALL_SIGN, Material.STONE, true),
            "converting to a non-sign would break dialling, not restyle it");
        assertFalse(StargateBlockSetup.shouldMatchDialSignMaterial(
            Material.OAK_WALL_SIGN, Material.OAK_SIGN, true),
            "a standing sign is not a wall sign and cannot hang on the [D] block");
    }

    /**
     * A block that is not a wall sign is never converted into one.
     *
     * <p>The dial sign block is whatever detection found there. If something has since
     * replaced it, replacing that with a sign would be the plugin putting a block back that a
     * player had deliberately removed.
     */
    @Test
    public void aBlockThatIsNoLongerASignIsNotTurnedIntoOne()
    {
        assertFalse(StargateBlockSetup.shouldMatchDialSignMaterial(
            Material.AIR, Material.CRIMSON_WALL_SIGN, true));
        assertFalse(StargateBlockSetup.shouldMatchDialSignMaterial(
            Material.STONE, Material.CRIMSON_WALL_SIGN, true));
    }

    @Test
    public void aGateWithNoSignMaterialResolvedConvertsNothing()
    {
        assertFalse(StargateBlockSetup.shouldMatchDialSignMaterial(
            Material.OAK_WALL_SIGN, null, true));
    }
}
