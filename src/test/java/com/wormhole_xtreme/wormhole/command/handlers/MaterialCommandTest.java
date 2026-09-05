package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.command.handlers.MaterialCommand.Kind;
import com.wormhole_xtreme.wormhole.model.Stargate;

/**
 * The three material commands still accept what they always accepted, and each still edits
 * its own field.
 *
 * <p>{@code PortalMaterialCommand}, {@code IrisMaterialCommand} and {@code LightMaterialCommand}
 * were three copies of one 97-line file, differing in a material whitelist, a noun, and a
 * getter/setter pair. Folding them into {@link MaterialCommand} moved all three whitelists into
 * one enum and replaced the hand-written getter/setter calls with method references — which is
 * exactly the kind of change that silently points one entry at another's field. A gate's iris
 * quietly setting its portal material would not fail to compile, and nothing else in the suite
 * touches these accessors.
 *
 * <p>So these tests pin the two things the consolidation could have broken: the accepted set per
 * kind, transcribed from what each of the three deleted files actually checked, and which field
 * each kind reads and writes.
 */
class MaterialCommandTest
{
    /**
     * The portal override accepts exactly what PortalMaterialCommand accepted.
     *
     * <p>Transcribed from the deleted file's condition, not from the new enum — copying the new
     * value here would make the test agree with whatever the code says, which is no test at all.
     */
    @Test
    void thePortalOverrideAcceptsTheSameMaterialsItAlwaysDid()
    {
        assertEquals(setOf(Material.WATER, Material.LAVA, Material.AIR, Material.NETHER_PORTAL),
            Kind.PORTAL.allowed(),
            "a material that used to be settable on a gate's portal no longer is, or one that "
                + "was refused now gets through");
    }

    /** The iris override accepts exactly what IrisMaterialCommand accepted. */
    @Test
    void theIrisOverrideAcceptsTheSameMaterialsItAlwaysDid()
    {
        assertEquals(setOf(Material.DIAMOND_BLOCK, Material.GLASS, Material.IRON_BLOCK,
            Material.BEDROCK, Material.STONE, Material.LAPIS_BLOCK),
            Kind.IRIS.allowed(),
            "a material that used to be settable on a gate's iris no longer is, or one that "
                + "was refused now gets through");
    }

    /** The light override accepts exactly what LightMaterialCommand accepted. */
    @Test
    void theLightOverrideAcceptsTheSameMaterialsItAlwaysDid()
    {
        assertEquals(setOf(Material.GLOWSTONE, Material.REDSTONE_ORE), Kind.LIGHT.allowed(),
            "a material that used to be settable on a gate's lights no longer is, or one that "
                + "was refused now gets through");
    }

    /**
     * Each kind reads and writes its own field and leaves the other two alone.
     *
     * <p>This is the method-reference wiring check. Setting through one kind and then reading
     * all three catches a {@code Kind} entry pointing at the wrong accessor, which is the one
     * mistake this refactor could make that still compiles.
     */
    @Test
    void eachKindEditsItsOwnFieldAndNoOther()
    {
        for (final Kind kind : Kind.values())
        {
            final Stargate gate = new Stargate();
            final Material chosen = kind.allowed().iterator().next();
            kind.setter().accept(gate, chosen);

            assertSame(chosen, kind.getter().apply(gate),
                kind + " did not read back what it just wrote, so its getter and setter are "
                    + "not looking at the same field");

            for (final Kind other : Kind.values())
            {
                if (other != kind)
                {
                    assertNull(other.getter().apply(gate),
                        "setting the " + kind + " material also changed the " + other
                            + " material, so one of those two Kind entries points at the "
                            + "wrong accessor");
                }
            }
        }
    }

    /**
     * The advice a player is shown lists exactly what the check will accept.
     *
     * <p>This is the property the three copies lost: each spelled its whitelist once as code and
     * again as English, and nothing kept them in step. It holds by construction now — both come
     * off the same set — and this test is what stops someone reintroducing a hand-written list.
     */
    @Test
    void theListedMaterialsAreTheAcceptedMaterials()
    {
        for (final Kind kind : Kind.values())
        {
            assertEquals(kind.allowed().size(), kind.allowedNames().size(),
                kind + " lists a different number of materials than it accepts");
            for (final Material material : kind.allowed())
            {
                assertTrue(kind.allowedNames().contains(material.name()),
                    kind + " accepts " + material + " but never tells anyone it does");
            }
        }
    }

    /** The subcommand names are the ones players already type. */
    @Test
    void theSubcommandNamesAreUnchanged()
    {
        assertEquals("portalmaterial", Kind.PORTAL.command());
        assertEquals("irismaterial", Kind.IRIS.command());
        assertEquals("lightmaterial", Kind.LIGHT.command());
    }

    private static Set<Material> setOf(final Material... values)
    {
        return new LinkedHashSet<>(Arrays.asList(values));
    }
}
