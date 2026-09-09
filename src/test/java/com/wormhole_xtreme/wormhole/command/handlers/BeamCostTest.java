package com.wormhole_xtreme.wormhole.command.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.wormhole_xtreme.wormhole.model.beam.BeamDestination;
import com.wormhole_xtreme.wormhole.model.beam.BeamManager;
import com.wormhole_xtreme.wormhole.model.beam.BeamPermissions;
import com.wormhole_xtreme.wormhole.model.beam.BeamYamlManager;

/**
 * What a beam destination costs, and the difference between free and unpriced.
 *
 * <p>A destination's own cost is a {@code Double} rather than a {@code double} on purpose, and
 * {@link BeamDestination} says why: null means "whatever {@code BEAM_ECONOMY_USE_COST}
 * currently says", and zero is an explicit, permanent "this one is free" that a later change to
 * the global default cannot override.
 *
 * <p>Nothing checked that distinction. Collapsing the two makes a destination somebody
 * deliberately set free start charging the next time the server's default is raised -- and the
 * only sign of it is players quietly being billed for a trip that used to cost nothing.
 */
class BeamCostTest
{
    private static final String NAME = "hub";

    private Player admin;
    private World world;
    private MockedStatic<BeamYamlManager> yaml;

    @BeforeEach
    void setUp()
    {
        BeamManager.clear();

        world = mock(World.class);
        when(world.getName()).thenReturn("world");

        admin = mock(Player.class);
        when(admin.getName()).thenReturn("Justin");
        when(admin.getUniqueId()).thenReturn(UUID.randomUUID());
        when(admin.getWorld()).thenReturn(world);
        when(admin.getLocation()).thenReturn(new Location(world, 10.5, 64.0, 20.5));
        when(admin.isOp()).thenReturn(Boolean.FALSE);
        when(admin.hasPermission(anyString())).thenReturn(Boolean.FALSE);
        when(admin.hasPermission(BeamPermissions.ADMIN)).thenReturn(Boolean.TRUE);

        BeamManager.setPublicDestination(
            BeamDestination.fromLocation(NAME, new Location(world, 1.5, 65.0, 2.5)));

        yaml = mockStatic(BeamYamlManager.class);
    }

    @AfterEach
    void tearDown()
    {
        yaml.close();
        BeamManager.clear();
    }

    private boolean cost(final String... rest)
    {
        final String[] args = new String[rest.length + 3];
        args[0] = "beam";
        args[1] = "admin";
        args[2] = "cost";
        System.arraycopy(rest, 0, args, 3, rest.length);
        return new BeamCommand().execute(admin, args);
    }

    private static Double costOf(final String name)
    {
        final BeamDestination d = BeamManager.getPublicDestination(name);
        return d == null ? null : d.cost();
    }

    /**
     * A destination can be given a price of its own.
     *
     * <p>Stored on a replacement rather than mutated in place: {@code withCost} returns a new
     * destination, so a caller that kept the old one would appear to work and change nothing.
     */
    @Test
    void aDestinationCanBeGivenAPriceOfItsOwn()
    {
        assertTrue(cost(NAME, "12.5"));

        assertEquals(Double.valueOf(12.5), costOf(NAME), "the override is stored");
        yaml.verify(BeamYamlManager::saveAll);
        verify(admin).sendMessage(contains("now costs 12.5"));
    }

    /**
     * Zero is a price, not the absence of one.
     *
     * <p>The whole reason the field is a {@code Double}. A destination set to zero is free for
     * good; one left unpriced follows the server's default, whatever that becomes.
     */
    @Test
    void zeroIsAPriceAndNotTheAbsenceOfOne()
    {
        assertTrue(cost(NAME, "0"));

        assertNotNull(costOf(NAME), "zero is stored, not treated as unset");
        assertEquals(0.0, costOf(NAME).doubleValue(), 1.0e-9, "and it is zero");
    }

    /**
     * And "default" is the absence of one, not a price of zero.
     *
     * <p>The other half. Clearing an override hands the destination back to the configured
     * cost; setting it to zero pins it free against any later change.
     */
    @Test
    void defaultClearsThePriceRatherThanSettingItToZero()
    {
        cost(NAME, "9");
        assertNotNull(costOf(NAME), "priced to begin with");

        assertTrue(cost(NAME, "default"));

        assertNull(costOf(NAME), "unpriced again, so the configured default applies");
        verify(admin).sendMessage(contains("configured default"));
    }

    /** Said however it is capitalised, since nobody types command words carefully. */
    @Test
    void defaultIsRecognisedWhateverTheCapitalisation()
    {
        cost(NAME, "9");

        assertTrue(cost(NAME, "DeFaUlT"));

        assertNull(costOf(NAME), "the same word, said louder");
    }

    /**
     * A price below zero is refused rather than stored.
     *
     * <p>A negative cost is an economy plugin being asked to pay somebody for travelling, which
     * is not a thing this command is for.
     */
    @Test
    void aPriceBelowZeroIsRefused()
    {
        cost(NAME, "9");

        assertTrue(cost(NAME, "-1"));

        assertEquals(Double.valueOf(9.0), costOf(NAME), "the price is left as it was");
        verify(admin).sendMessage(contains("cannot be negative"));
    }

    /** Something that is not a number says so, and quotes back what was typed. */
    @Test
    void somethingThatIsNotANumberIsRefusedAndQuotedBack()
    {
        assertTrue(cost(NAME, "cheap"));

        assertNull(costOf(NAME), "nothing was stored");
        verify(admin).sendMessage(contains("\"cheap\" is not a number"));
        yaml.verify(BeamYamlManager::saveAll, never());
    }

    /** Pricing something that is not there says so rather than inventing it. */
    @Test
    void pricingADestinationThatDoesNotExistIsRefused()
    {
        assertTrue(cost("nosuchplace", "5"));

        assertNull(BeamManager.getPublicDestination("nosuchplace"), "nothing was created");
        verify(admin).sendMessage(contains("No public beam destination named"));
        yaml.verify(BeamYamlManager::saveAll, never());
    }

    /** Naming no amount shows what the command takes. */
    @Test
    void namingNoAmountShowsTheUsage()
    {
        assertTrue(cost(NAME));

        verify(admin).sendMessage(contains("<amount|default>"));
        yaml.verify(BeamYamlManager::saveAll, never());
    }

    /**
     * Repricing keeps everything else about the destination.
     *
     * <p>The replacement is built from the old one's fields, so a mistake there would move a
     * destination somewhere else as a side effect of changing its price.
     */
    @Test
    void repricingADestinationDoesNotMoveIt()
    {
        assertTrue(cost(NAME, "3"));

        final BeamDestination after = BeamManager.getPublicDestination(NAME);
        // The reprice first, or the rest of this passes just as well against a command that
        // returned early and changed nothing.
        assertEquals(Double.valueOf(3.0), after.cost(), "the price did change");
        assertEquals(1.5, after.point().x(), 1.0e-9, "still where it was");
        assertEquals(65.0, after.point().y(), 1.0e-9);
        assertEquals(2.5, after.point().z(), 1.0e-9);
        assertEquals(NAME, after.name(), "and still called the same thing");
    }

    /** Pricing is an admin job, and the node is the one the rest of admin uses. */
    @Test
    void pricingNeedsTheAdminNode()
    {
        when(admin.hasPermission(BeamPermissions.ADMIN)).thenReturn(Boolean.FALSE);

        assertTrue(cost(NAME, "5"));

        assertNull(costOf(NAME), "nothing was priced");
        verify(admin).sendMessage(contains("permission"));
    }
}
