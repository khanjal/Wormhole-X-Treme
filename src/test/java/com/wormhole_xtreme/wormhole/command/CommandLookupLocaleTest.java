package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.command.handlers.GateEditCommand;

/**
 * Typing a command does not depend on what language the server runs in.
 *
 * <p>Every lookup here folds what the player typed before matching it against a name spelled
 * out in the source -- {@code "ring"}, {@code "iris"}. Folded in the JVM's own locale, a
 * Turkish server maps an upper-case {@code I} to a dotless {@code ı} instead of an {@code i},
 * so the folded text stops matching the literal and the command is simply not found.
 *
 * <p>The failure is quiet in the worst way: the name on screen looks perfectly correct,
 * because the mangling happens after it is read. {@code /wormhole RING} answers as though no
 * such command exists, and it reads as a typo that is not one.
 *
 * <p>Sibling of {@code SettingLookupLocaleTest}, which pins the same guarantee for
 * {@code /wormhole config}'s own path. That one was written when a reviewer caught a single
 * line of it; this one covers the rest of the plugin's command surface, where the same
 * mistake had been made 67 more times.
 *
 * <p>Note that lower case input mostly survives on a Turkish server -- {@code i} folds to
 * {@code i} there like anywhere else -- which is why nobody has ever reported this. The
 * assertions below deliberately type in upper case, the half that breaks.
 */
public class CommandLookupLocaleTest
{
    /** The locale to put back, since this is JVM-wide state. */
    private Locale before;

    @BeforeEach
    public void speakTurkish()
    {
        before = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
    }

    @AfterEach
    public void speakWhateverWeDidBefore()
    {
        Locale.setDefault(before);
    }

    @Test
    public void aSubcommandTypedInUpperCaseStillDispatchesOnATurkishServer()
    {
        // "ring" and "config" both carry an i, so both were unreachable in upper case --
        // two of the five names the plugin actually advertises.
        for (final String typed : new String[] { "RING", "CONFIG", "Ring", "gate", "BEAM" })
        {
            assertNotNull(SubCommands.find(typed),
                "\"/wormhole " + typed + "\" dispatched to nothing, so the player was told it "
                    + "was an invalid request on a server running in Turkish");
        }
    }

    @Test
    public void tabCompletionStillOffersTheCommandsWhoseNamesContainAnI()
    {
        // namesMatching folds the typed prefix but compares it against the unfolded name, so
        // a dotless i in the prefix matches nothing and the completion list comes back empty.
        assertTrue(SubCommands.namesMatching("RI").contains("ring"),
            "typing \"RI\" and pressing tab offered nothing, because the folded prefix no "
                + "longer starts \"ring\" on a server running in Turkish");
        assertTrue(SubCommands.namesMatching("CONFI").contains("config"),
            "typing \"CONFI\" and pressing tab offered nothing on a Turkish server");
    }

    @Test
    public void aGateEditFieldTypedInUpperCaseIsStillRecognised()
    {
        // /wormhole gate edit <gate> IRIS <material> -- the field name is looked up in a map
        // keyed by ASCII literals, so a folded "ırıs" answers "No such field: IRIS."
        assertTrue(GateEditCommand.isField("IRIS"),
            "\"IRIS\" was rejected as an unknown gate field on a Turkish server");
        assertTrue(GateEditCommand.isField("LIGHT"),
            "\"LIGHT\" was rejected as an unknown gate field on a Turkish server");
        assertTrue(GateEditCommand.isField("redstone"),
            "\"redstone\" was rejected as an unknown gate field");
        assertFalse(GateEditCommand.isField("banana"),
            "a field that does not exist should still be refused");
    }
}
