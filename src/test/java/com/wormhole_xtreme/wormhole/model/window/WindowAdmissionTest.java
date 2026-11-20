package com.wormhole_xtreme.wormhole.model.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The first to look at a window keep it, and the rest wait until somebody lets go (#613). */
class WindowAdmissionTest
{
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID third = UUID.randomUUID();

    @BeforeEach
    @AfterEach
    void clean()
    {
        WindowAdmission.clear();
    }

    private static WindowState window(final String name)
    {
        final WindowState window = mock(WindowState.class);
        when(window.name()).thenReturn(name);
        return window;
    }

    private static List<String> names(final List<WindowState> windows)
    {
        return windows.stream().map(WindowState::name).toList();
    }

    private static List<WindowState> seeing(final String... names)
    {
        final List<WindowState> list = new ArrayList<>();
        for (final String name : names)
        {
            list.add(window(name));
        }
        return list;
    }

    @Test
    void viewersPastTheCapAreLeftWithNothingAndTheFirstKeepTheirs()
    {
        final List<WindowState> one = seeing("a");
        final List<WindowState> two = seeing("a");
        final List<WindowState> late = seeing("a");

        WindowAdmission.limit(first, one, 2, null, 0L);
        WindowAdmission.limit(second, two, 2, null, 0L);
        WindowAdmission.limit(third, late, 2, null, 0L);

        assertEquals(List.of("a"), names(one));
        assertEquals(List.of("a"), names(two));
        assertEquals(List.of(), names(late), "the third is over the cap");
        final List<WindowState> again = seeing("a");
        WindowAdmission.limit(first, again, 2, null, 0L);
        assertEquals(List.of("a"), names(again), "one admitted stays admitted, and does not take a second place");
        assertEquals(2, WindowAdmission.count("a"));
    }

    @Test
    void lookingAwayLetsTheNextViewerIn()
    {
        WindowAdmission.limit(first, seeing("a"), 1, null, 0L);
        final List<WindowState> waiting = seeing("a");
        WindowAdmission.limit(second, waiting, 1, null, 0L);
        assertEquals(List.of(), names(waiting));

        WindowAdmission.limit(first, seeing(), 1, null, 0L);
        final List<WindowState> next = seeing("a");
        WindowAdmission.limit(second, next, 1, null, 0L);

        assertEquals(List.of("a"), names(next), "the one looking away gave it up");
    }

    @Test
    void aViewerWhoLeavesGivesTheirPlaceUp()
    {
        WindowAdmission.limit(first, seeing("a"), 1, null, 0L);

        WindowAdmission.forget(first);

        final List<WindowState> next = seeing("a");
        WindowAdmission.limit(second, next, 1, null, 0L);
        assertEquals(List.of("a"), names(next));
    }

    @Test
    void eachWindowHasItsOwnPlacesAndTheExemptOneTakesNone()
    {
        WindowAdmission.limit(first, seeing("a", "b"), 1, null, 0L);

        final List<WindowState> other = seeing("a", "b", "c");
        WindowAdmission.limit(second, other, 1, "a", 0L);

        assertEquals(List.of("a", "c"), names(other), "a is theirs by exemption, b is full, c is free");
        assertEquals(1, WindowAdmission.count("a"), "the exempt window did not count them");
    }

    @Test
    void aWindowDrawnWholeForAnAdminTakesNoPlaceOfTheirs()
    {
        WindowAdmission.limit(first, seeing("a"), 1, null, 0L);

        WindowAdmission.limit(first, seeing("a"), 1, "a", 0L);

        assertEquals(0, WindowAdmission.count("a"), "their place went back to the others");
    }

    @Test
    void aViewerWhoseRedrawEndedKeepsTheirPlaceAndOneWhoIsGoneDoesNot()
    {
        final org.bukkit.entity.Player here = mock(org.bukkit.entity.Player.class);
        WindowAdmission.limit(first, seeing("a"), 1, null, 0L);

        Windows.endView(first, here);
        assertEquals(1, WindowAdmission.count("a"), "still looking: the redraw finishing is not leaving");

        Windows.endView(first, null);
        assertEquals(0, WindowAdmission.count("a"), "gone");
    }

    /** Logging out mid-stream, going blind or changing world ends the asking, not the place: time does. */
    @Test
    void aHolderStillBeingSeenKeepsTheirPlaceWhileOneNotSeenLosesIt()
    {
        WindowAdmission.limit(first, seeing("a"), 1, null, 0L);
        WindowAdmission.limit(first, seeing("a"), 1, null, 8_000L);

        final List<WindowState> waiting = seeing("a");
        WindowAdmission.limit(second, waiting, 1, null, 12_000L);
        assertEquals(List.of(), names(waiting), "confirmed at 8 s, so still theirs at 12 s");

        final List<WindowState> later = seeing("a");
        WindowAdmission.limit(second, later, 1, null, 8_000L + WindowAdmission.STALE_MILLIS + 1L);
        assertEquals(List.of("a"), names(later), "not confirmed for over 10 s: given up, as a logged-out viewer's is");
    }

    @Test
    void aWindowTakenBackStartsFresh()
    {
        WindowAdmission.limit(first, seeing("a"), 1, null, 0L);

        WindowAdmission.forget("a");

        assertEquals(0, WindowAdmission.count("a"));
    }
}
