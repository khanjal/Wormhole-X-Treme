package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.WormholeXTreme;
import com.wormhole_xtreme.wormhole.model.Stargate;
import com.wormhole_xtreme.wormhole.model.StargateManager;
import com.wormhole_xtreme.wormhole.model.StargateShape;
import com.wormhole_xtreme.wormhole.model.StargateShapeRegistry;

/**
 * Tab completion for {@code /wormhole gate}.
 *
 * <p>Like {@code beam}, it dispatches on a verb and then on how many words have been typed,
 * and the positions differ between verbs: {@code edit} takes a gate, a field and then a value
 * whose candidates depend on which field was named, while most verbs take a gate name and
 * nothing after it.
 *
 * <p>Nothing covered any of it. The field-dependent value slot is the part worth holding --
 * offering block materials where a true/false belongs is offering a mistake.
 */
class GateTabCompletionTest
{
    private java.util.Map<String, StargateShape> savedShapes;

    @BeforeEach
    void setUp() throws Exception
    {
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, mock(WormholeXTreme.class));
        clearGates();

        // Shapes are read off disk when the plugin enables, so the registry is empty in a
        // test JVM. It is a shared static, so put back whatever the rest of the suite had.
        savedShapes = new java.util.HashMap<String, StargateShape>(
            StargateShapeRegistry.getStargateShapes());
        StargateShapeRegistry.getStargateShapes().put("Standard", new StargateShape());
    }

    @AfterEach
    void tearDown() throws Exception
    {
        clearGates();
        StargateShapeRegistry.getStargateShapes().clear();
        StargateShapeRegistry.getStargateShapes().putAll(savedShapes);
        final Field f = WormholeXTreme.class.getDeclaredField("thisPlugin");
        f.setAccessible(true);
        f.set(null, null);
    }

    private static void clearGates()
    {
        for (final Stargate s : new ArrayList<Stargate>(StargateManager.getAllGates()))
        {
            if (s != null)
            {
                StargateManager.removeStargate(s);
            }
        }
    }

    private static void gateNamed(final String name)
    {
        final Stargate gate = new Stargate();
        gate.setGateName(name);
        StargateManager.registerStargate(gate);
    }

    private static List<String> complete(final String... args)
    {
        return SubCommands.find("gate").completeArgs(args);
    }

    /** The verbs the command understands. */
    @Test
    void theVerbsAreOffered()
    {
        final List<String> verbs = complete("gate", "");

        assertTrue(verbs.contains("edit"));
        assertTrue(verbs.contains("build"));
        assertTrue(verbs.contains("shapes"));
        assertFalse(verbs.isEmpty());
    }

    /** Most verbs take a gate name and nothing after it. */
    @Test
    void anOrdinaryVerbTakesAGateNameAndNothingMore()
    {
        gateNamed("alpha");

        assertTrue(complete("gate", "info", "").contains("alpha"));
        assertTrue(complete("gate", "info", "alpha", "").isEmpty(),
            "there is nothing worth guessing at past the gate name");
    }

    /** {@code edit} takes a gate, then a field. */
    @Test
    void editTakesAGateThenAField()
    {
        gateNamed("alpha");

        assertTrue(complete("gate", "edit", "").contains("alpha"));

        final List<String> fields = complete("gate", "edit", "alpha", "");
        assertTrue(fields.contains("redstone"), "got " + fields);
        assertTrue(fields.contains("group"));
    }

    /**
     * The value slot depends on which field was named.
     *
     * <p>This is the whole reason the verb tree has a third level. A material where a
     * true/false belongs, or the other way round, is offering the player a mistake the
     * command is about to refuse.
     */
    @Test
    void theValueOfferedDependsOnTheFieldNamed()
    {
        gateNamed("alpha");

        final List<String> redstone = complete("gate", "edit", "alpha", "redstone", "");
        assertTrue(redstone.contains("true"));
        assertTrue(redstone.contains("false"));
        assertFalse(redstone.contains("obsidian"), "a material is not a redstone setting");

        final List<String> portal = complete("gate", "edit", "alpha", "portal", "");
        assertTrue(portal.contains("water"), "got " + portal.size() + " candidates");
        assertFalse(portal.contains("true"), "a boolean is not a portal material");

        final List<String> group = complete("gate", "edit", "alpha", "group", "");
        assertFalse(group.contains("true"));
    }

    /** A field with no candidates of its own offers nothing rather than guessing. */
    @Test
    void aFieldWithNoCandidatesOffersNothing()
    {
        gateNamed("alpha");

        assertTrue(complete("gate", "edit", "alpha", "name", "").isEmpty(),
            "a gate's new name is not something that can be offered");
    }

    /** Past the value, edit is done. */
    @Test
    void editOffersNothingPastItsValue()
    {
        gateNamed("alpha");

        assertTrue(complete("gate", "edit", "alpha", "redstone", "true", "").isEmpty());
    }

    /** {@code build} takes a shape name in its one slot. */
    @Test
    void buildTakesAShapeName()
    {
        assertTrue(complete("gate", "build", "").contains("Standard"),
            "the shipped shapes are offered");
        assertTrue(complete("gate", "build", "Standard", "").isEmpty());
    }

    /** {@code shapes} takes an action, then a shape name for validate. */
    @Test
    void shapesTakesAnActionThenAShape()
    {
        final List<String> actions = complete("gate", "shapes", "");
        assertTrue(actions.contains("reload"));
        assertTrue(actions.contains("validate"));

        assertTrue(complete("gate", "shapes", "validate", "").contains("Standard"));
    }

    /**
     * {@code regenerate} offers gate names and {@code -all} together.
     *
     * <p>Both belong in the same slot, and a completer cannot know in advance which the
     * admin means, so it offers both rather than picking.
     */
    @Test
    void regenerateOffersGateNamesAndAllTogether()
    {
        gateNamed("alpha");

        final List<String> candidates = complete("gate", "regenerate", "");
        assertTrue(candidates.contains("alpha"), "got " + candidates);
        assertTrue(candidates.contains("-all"), "got " + candidates);

        assertTrue(complete("gate", "regen", "").contains("-all"),
            "the short form completes the same way");
    }
}
