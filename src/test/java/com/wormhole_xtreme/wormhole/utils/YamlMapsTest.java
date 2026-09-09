package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The two casts every YAML reader in this plugin now shares.
 *
 * <p>What is worth pinning down is not the cast -- that either compiles or does not -- but the
 * promise the readers were rewritten against. Each of them used to open with its own
 * {@code instanceof} guard and an early return, and those guards were deleted on the strength
 * of one claim: an unusable value comes back as something safe to iterate that yields nothing.
 * If that ever stopped being true, a hand-edited config would stop being a skipped section and
 * start being a {@code NullPointerException} on startup, in five different files at once.
 */
class YamlMapsTest
{
    /** The ordinary path: a mapping comes back as the same object, not a copy of it. */
    @Test
    void aParsedMappingComesBackAsItself()
    {
        final Object parsed = new Yaml().load("World: nether\nX: 12\n");

        final Map<String, Object> map = YamlMaps.asMap(parsed);

        assertSame(parsed, map, "a mapping should be handed straight back, not rebuilt");
        assertEquals("nether", map.get("World"));
        assertEquals(Integer.valueOf(12), map.get("X"));
    }

    /**
     * The claim the deleted guards rested on, for every shape a hand-edited file can take.
     *
     * <p>A scalar where a section was expected, a list where a mapping was expected, and a
     * file that parsed to nothing at all: all three used to be an early return written out
     * longhand at each call site, and all three now have to arrive as an empty map that a
     * for-each loop steps over without complaint.
     */
    @Test
    void anythingThatIsNotAMappingIsAnEmptyMapRatherThanNullOrAThrow()
    {
        for (final Object notAMapping : new Object[] {
            new Yaml().load("just a string"),
            new Yaml().load("- one\n- two\n"),
            new Yaml().load(""),
            null,
        })
        {
            final Map<String, Object> map = YamlMaps.asMap(notAMapping);

            assertTrue(map.isEmpty(), "an unusable value should read as nothing to load");
            // Iterated rather than only asked, because stepping over it is what the call
            // sites do now that their guards are gone.
            assertEquals(List.of(), new ArrayList<>(map.entrySet()),
                "an empty section should yield no entries to a for-each");
        }
    }

    /** The same promise for {@code asList}, which a ring pair's allow-list is read through. */
    @Test
    void anythingThatIsNotASequenceIsAnEmptyListRatherThanNullOrAThrow()
    {
        assertEquals(List.of("alice", "bob"), YamlMaps.asList(new Yaml().load("- alice\n- bob\n")));

        for (final Object notASequence : new Object[] {
            new Yaml().load("World: nether"),
            new Yaml().load("just a string"),
            null,
        })
        {
            assertTrue(YamlMaps.asList(notASequence).isEmpty(),
                "an unusable allow-list should read as nobody, not as a crash on startup");
        }
    }

    /**
     * The limit the class documents, held to on purpose so it cannot drift unnoticed.
     *
     * <p>YAML permits non-string keys, so {@code 1: one} parses to a mapping whose key is an
     * {@code Integer} and which this hands back as a {@code Map<String, Object>} regardless.
     * Reading such a key throws where it is read, exactly as it did when each caller cast for
     * itself. What that costs depends on the caller: a ring pair is read inside a try/catch and
     * one bad key skips one pair, while the beam and material-group loops have no such guard
     * and a bad key aborts the load. Neither is changed by this, and the class doc says which
     * is which. This test exists so that the throw itself cannot quietly stop happening.
     */
    // S1612 wants ((String) key)::length here. A method reference evaluates its receiver
    // eagerly, when the reference is created, so the cast would throw outside assertThrows and
    // the test would error instead of pass. Verified by applying the suggestion and running it.
    @SuppressWarnings("java:S1612")
    @Test
    void aNonStringKeyStillThrowsWhereItIsRead()
    {
        final Map<String, Object> map = YamlMaps.asMap(new Yaml().load("1: one"));

        assertEquals(1, map.size(), "the mapping itself is handed back, keys unexamined");

        // Read out as Object first: fetching the key is not what fails, treating it as a
        // String is, which is what a caller's for-each does on its behalf.
        final Object key = map.keySet().iterator().next();
        assertEquals(Integer.valueOf(1), key, "the key survives as whatever YAML made of it");
        assertThrows(ClassCastException.class,
            () -> ((String) key).length(),
            "reading a non-string key must still fail at the point of use, where a caller "
                + "catches it and skips the one entry");
    }
}
