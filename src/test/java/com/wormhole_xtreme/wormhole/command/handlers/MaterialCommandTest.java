package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.PluginTestSupport;
import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.command.handlers.MaterialCommand.Kind;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateDBManager;
import com.wormhole_xtreme.wormhole.model.StargateManager;

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
    private CommandSender sender;

    // Every test mocks it: a change is saved at once, and the real save writes gate files
    // into the working directory.
    private MockedStatic<StargateDBManager> db;

    @BeforeEach
    void setUp() throws Exception
    {
        PluginTestSupport.install(mock(WormholeXTreme.class));
        // Not a player, so the admin node is not asked for.
        sender = mock(CommandSender.class);
        clearGates();
        db = mockStatic(StargateDBManager.class);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        db.close();
        clearGates();
        PluginTestSupport.remove();
    }

    private static void clearGates()
    {
        for (final Stargate s : new ArrayList<>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
    }

    /** A registered gate in custom mode, which is the only kind these commands will change. */
    private static Stargate customGate(final String name)
    {
        final Stargate gate = new Stargate();
        gate.setGateName(name);
        gate.setGateCustom(true);
        StargateManager.registerStargate(gate);
        return gate;
    }

    /**
     * A material it accepts is set and saved at once.
     *
     * <p>It used to reach disk only at shutdown, so a crash lost it.
     */
    @Test
    void anAcceptedMaterialIsSetAndSaved()
    {
        final Stargate gate = customGate("alpha");

        assertTrue(new MaterialCommand(Kind.IRIS).execute(sender, new String[] { "irismaterial", "alpha", "glass" }));

        assertSame(Material.GLASS, gate.getGateCustomIrisMaterial());
        db.verify(() -> StargateDBManager.saveStargate(gate));
    }

    /** A material it refuses changes nothing, so nothing is written. */
    @Test
    void aRefusedMaterialIsNotSaved()
    {
        final Stargate gate = customGate("alpha");

        assertTrue(new MaterialCommand(Kind.PORTAL).execute(sender, new String[] { "portalmaterial", "alpha", "stone" }));

        assertNull(gate.getGateCustomPortalMaterial());
        verify(sender).sendMessage(contains("Invalid portal material: stone"));
        db.verify(() -> StargateDBManager.saveStargate(any()), never());
    }

    /** Asking what the material is changes nothing, so nothing is written. */
    @Test
    void askingForTheMaterialSavesNothing()
    {
        customGate("alpha");

        assertTrue(new MaterialCommand(Kind.LIGHT).execute(sender, new String[] { "lightmaterial", "alpha" }));

        verify(sender).sendMessage(contains("light material is currently"));
        db.verify(() -> StargateDBManager.saveStargate(any()), never());
    }

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
