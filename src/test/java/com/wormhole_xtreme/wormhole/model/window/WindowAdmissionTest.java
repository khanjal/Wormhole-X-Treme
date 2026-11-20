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

        WindowAdmission.limit(first, one, 2, null);
        WindowAdmission.limit(second, two, 2, null);
        WindowAdmission.limit(third, late, 2, null);

        assertEquals(List.of("a"), names(one));
        assertEquals(List.of("a"), names(two));
        assertEquals(List.of(), names(late), "the third is over the cap");
        final List<WindowState> again = seeing("a");
        WindowAdmission.limit(first, again, 2, null);
        assertEquals(List.of("a"), names(again), "one admitted stays admitted, and does not take a second place");
        assertEquals(2, WindowAdmission.count("a"));
    }

    @Test
    void lookingAwayLetsTheNextViewerIn()
    {
        WindowAdmission.limit(first, seeing("a"), 1, null);
        final List<WindowState> waiting = seeing("a");
        WindowAdmission.limit(second, waiting, 1, null);
        assertEquals(List.of(), names(waiting));

        WindowAdmission.limit(first, seeing(), 1, null);
        final List<WindowState> next = seeing("a");
        WindowAdmission.limit(second, next, 1, null);

        assertEquals(List.of("a"), names(next), "the one looking away gave it up");
    }

    @Test
    void aViewerWhoLeavesGivesTheirPlaceUp()
    {
        WindowAdmission.limit(first, seeing("a"), 1, null);

        WindowAdmission.forget(first);

        final List<WindowState> next = seeing("a");
        WindowAdmission.limit(second, next, 1, null);
        assertEquals(List.of("a"), names(next));
    }

    @Test
    void eachWindowHasItsOwnPlacesAndTheExemptOneTakesNone()
    {
        WindowAdmission.limit(first, seeing("a", "b"), 1, null);

        final List<WindowState> other = seeing("a", "b", "c");
        WindowAdmission.limit(second, other, 1, "a");

        assertEquals(List.of("a", "c"), names(other), "a is theirs by exemption, b is full, c is free");
        assertEquals(1, WindowAdmission.count("a"), "the exempt window did not count them");
    }

    @Test
    void aWindowTakenBackStartsFresh()
    {
        WindowAdmission.limit(first, seeing("a"), 1, null);

        WindowAdmission.forget("a");

        assertEquals(0, WindowAdmission.count("a"));
    }
}
