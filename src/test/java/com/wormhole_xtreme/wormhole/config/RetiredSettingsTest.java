package com.wormhole_xtreme.wormhole.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Which removed settings a config.yml holds, and the view depth carried over from the old three (#589).
 */
class RetiredSettingsTest
{
    /** Every retired key is one no setting answers to any more, or a file holding it would be named wrongly. */
    @Test
    void noRetiredKeyIsASettingStill()
    {
        final List<String> current = Arrays.stream(DefaultSettings.config)
            .map(s -> ConfigurationYAML.kebabKeyName(s.getName().name())).toList();
        for (final String key : RetiredSettings.KEYS)
        {
            assertFalse(current.contains(key), key + " is retired but still a setting");
        }
        assertEquals(27, RetiredSettings.KEYS.size(), "the twenty-seven settings 1.11.0 removed");
    }

    /** Found under either spelling, listed once each in a fixed order, and nothing else is. */
    @Test
    void theRetiredKeysAreFoundUnderEitherSpelling()
    {
        final Map<String, Object> file = Map.of("beam-fade-ticks", 8, "RING_HOLD_TICKS", 20, "timeout-shutdown", 38,
            "ring-max-link-height", 0);

        assertEquals(List.of("ring-max-link-height", "ring-hold-ticks", "beam-fade-ticks"), RetiredSettings.presentIn(file));
        assertEquals(List.of(), RetiredSettings.presentIn(Map.of("view-depth", 160)));
    }

    /** No old depth, or a view-depth of its own, carries nothing over. */
    @Test
    void nothingIsCarriedOverWithoutAnOldDepthOrWithANewOne()
    {
        assertNull(RetiredSettings.carriedViewDepth(Map.of("gate-view-depth", 48)), "a first step alone is no view depth");
        assertNull(RetiredSettings.carriedViewDepth(Map.of("view-depth", 96, "mirror-view-depth", 64)));
        assertNull(RetiredSettings.carriedViewDepth(Map.of("VIEW_DEPTH", 96, "mirror-view-depth", 64)));
        assertNull(RetiredSettings.carriedViewDepth(Map.of("mirror-view-depth", "deep")), "nor a depth that is no number");
    }

    /** The shallower of the mirror's depth and the gate's fill, as each was lowered to be cheaper. */
    @Test
    void theShallowerOldDepthIsCarriedOver()
    {
        assertEquals(new RetiredSettings.Carried(64, List.of("mirror-view-depth")),
            RetiredSettings.carriedViewDepth(Map.of("mirror-view-depth", 64)));
        assertEquals(new RetiredSettings.Carried(80, List.of("mirror-view-depth", "gate-view-full-depth")),
            RetiredSettings.carriedViewDepth(Map.of("MIRROR_VIEW_DEPTH", 120, "gate-view-full-depth", 80)));
        assertEquals(new RetiredSettings.Carried(160, List.of("mirror-view-depth", "gate-view-full-depth")),
            RetiredSettings.carriedViewDepth(Map.of("mirror-view-depth", 160, "gate-view-full-depth", 160)),
            "an upgraded file on the defaults: no change");
    }

    /** A fill of 0 kept a gate at its first step, so that step is the depth it asked for. */
    @Test
    void aFillOfZeroCarriesOverTheFirstStep()
    {
        assertEquals(new RetiredSettings.Carried(48, List.of("gate-view-full-depth", "gate-view-depth")),
            RetiredSettings.carriedViewDepth(Map.of("gate-view-full-depth", 0, "gate-view-depth", 48)));
        assertEquals(new RetiredSettings.Carried(32, List.of("gate-view-full-depth")),
            RetiredSettings.carriedViewDepth(Map.of("gate-view-full-depth", 0)), "the shipped first step");
    }

    /** Carried over within the range view-depth is read in. */
    @Test
    void aCarriedDepthIsKeptFromFourTo160()
    {
        assertEquals(160, RetiredSettings.carriedViewDepth(Map.of("mirror-view-depth", 500)).depth());
        assertEquals(4, RetiredSettings.carriedViewDepth(Map.of("mirror-view-depth", 1)).depth());
    }

    /** The notice names what was carried over apart from what is ignored, and says nothing for a clean file. */
    @Test
    void theNoticeSeparatesTheCarriedFromTheIgnored()
    {
        assertEquals(List.of(), RetiredSettings.notice(List.of(), null));
        assertEquals(List.of("These settings no longer exist and are ignored: ring-hold-ticks, beam-fade-ticks"),
            RetiredSettings.notice(List.of("ring-hold-ticks", "beam-fade-ticks"), null));
        assertEquals(List.of("view-depth was added at 64, carried over from mirror-view-depth; it sets how far"
            + " mirrors and gate views reach.", "These settings no longer exist and are ignored: gate-view-depth"),
            RetiredSettings.notice(List.of("gate-view-depth", "mirror-view-depth"),
                new RetiredSettings.Carried(64, List.of("mirror-view-depth"))));
        assertEquals(1, RetiredSettings.notice(List.of("mirror-view-depth"),
            new RetiredSettings.Carried(64, List.of("mirror-view-depth"))).size(), "nothing left to call ignored");
    }
}
