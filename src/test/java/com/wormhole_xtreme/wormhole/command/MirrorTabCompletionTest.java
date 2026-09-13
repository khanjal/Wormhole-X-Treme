package com.wormhole_xtreme.wormhole.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.model.mirror.MirrorBlock;
import com.wormhole_xtreme.wormhole.model.mirror.MirrorManager;
import com.wormhole_xtreme.wormhole.model.mirror.QuantumMirror;

/**
 * What {@code /wormhole mirror} offers as you type it.
 *
 * <p>Completion is the only documentation most admins read, so the thing worth pinning is that
 * it offers names that exist where a name is wanted, and does not offer them where one is not.
 *
 * <p>{@code set} is the deliberate omission. It names a <em>new</em> mirror, and completing it
 * from the existing ones would put rebinding a mirror one tab away from creating one -- which
 * is a mistake an admin would only notice after the old banner stopped working.
 */
class MirrorTabCompletionTest
{
    @BeforeEach
    void setUp()
    {
        MirrorManager.clear();
        MirrorManager.add(new QuantumMirror("museum", new MirrorBlock("world", 1, 64, 1), null));
        MirrorManager.add(new QuantumMirror("lobby", new MirrorBlock("world", 2, 64, 2), null));
    }

    @AfterEach
    void tearDown()
    {
        MirrorManager.clear();
    }

    private static List<String> complete(final String... args)
    {
        return SubCommands.find("mirror").completeArgs(null, args);
    }

    /** The verbs, and only the verbs, at the first position. */
    @Test
    void theFirstArgumentOffersTheVerbs()
    {
        final List<String> verbs = complete("mirror", "");

        assertTrue(verbs.contains("set"), "got " + verbs);
        assertTrue(verbs.contains("target"));
        assertTrue(verbs.contains("link"));
        assertTrue(verbs.contains("remove"));
        assertTrue(verbs.contains("list"));
    }

    /** And filters by what has been typed. */
    @Test
    void theVerbsFilterOnThePrefix()
    {
        // Declaration order, which is the order the usage line prints them in.
        assertEquals(List.of("link", "list"), complete("mirror", "l"),
            "only the two verbs beginning with l");
    }

    /** A verb that acts on an existing mirror completes from the ones that exist. */
    @Test
    void targetAndRemoveCompleteFromExistingMirrors()
    {
        assertTrue(complete("mirror", "target", "").contains("museum"));
        assertTrue(complete("mirror", "remove", "").contains("lobby"));
        assertEquals(List.of("lobby"), complete("mirror", "remove", "lo"));
    }

    /** link names two of them, so both positions complete. */
    @Test
    void linkCompletesBothOfItsNames()
    {
        assertTrue(complete("mirror", "link", "").contains("museum"),
            "the mirror being pointed");
        assertTrue(complete("mirror", "link", "lobby", "").contains("museum"),
            "and the one it is pointed at");
    }

    /**
     * set offers nothing, on purpose.
     *
     * <p>It names a new mirror. Offering the existing names here would make rebinding one a
     * tab away from creating one, and the mistake only shows up later as a banner that has
     * quietly stopped working.
     */
    @Test
    void setOffersNoNames()
    {
        assertTrue(complete("mirror", "set", "").isEmpty(),
            "completing set from existing names would invite rebinding one by accident");
    }

    /** list takes nothing, so it offers nothing. */
    @Test
    void listOffersNoNames()
    {
        assertTrue(complete("mirror", "list", "").isEmpty());
    }

    /** Past the arguments a verb takes, there is nothing more to offer. */
    @Test
    void thereIsNothingBeyondTheArgumentsAVerbTakes()
    {
        assertTrue(complete("mirror", "remove", "museum", "").isEmpty(),
            "remove takes one name, not two");
        assertTrue(complete("mirror", "link", "lobby", "museum", "").isEmpty());
    }

    /**
     * A verb nobody has offers nothing, rather than the mirror names.
     *
     * <p>The completer names the verbs that take one instead of excluding the two that do
     * not. Falling through meant a typo still offered the mirror list, which reads as though
     * the typo were a real command.
     */
    /**
     * The word where the name is optional also offers what replaces it.
     *
     * <p>{@code display}, {@code mode} and {@code stamp} act on the banner being looked at when
     * no name is given. Offering only mirror names there would hide that, which is most of what
     * makes the shorter form findable at all.
     */
    @Test
    void theOptionalNamePositionAlsoOffersWhatReplacesIt()
    {
        assertTrue(complete("mirror", "display", "").contains("proximity"),
            "got " + complete("mirror", "display", ""));
        assertTrue(complete("mirror", "display", "").contains("museum"),
            "and the names are still there, since the name is optional rather than gone");
        assertTrue(complete("mirror", "mode", "").contains("dynamic"));
        assertTrue(complete("mirror", "stamp", "").contains("museum"));
    }

    @Test
    void anUnknownVerbOffersNothing()
    {
        assertTrue(complete("mirror", "frobnicate", "").isEmpty(),
            "a verb that does not exist should not look like one that does");
        assertFalse(complete("mirror", "frobnicate", "").contains("museum"));
    }
}
