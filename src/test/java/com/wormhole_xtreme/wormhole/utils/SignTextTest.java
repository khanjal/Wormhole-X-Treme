package com.wormhole_xtreme.wormhole.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import org.bukkit.block.sign.SignSide;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * A sign's lines go through Paper's component methods where the server has them, and Bukkit's
 * string methods otherwise; a Paper call that fails is made again the Bukkit way, so no sign
 * update is lost.
 *
 * <p>Spigot's API, which most legs compile against, has no {@code line(int, Component)}, so these
 * tests install a stand-in type declaring Paper's three methods, found by reflection exactly as
 * the server's are. The fallback tests stub and verify the string methods Paper deprecates,
 * hence the suppression.
 */
@SuppressWarnings("deprecation")
class SignTextTest
{
    private static final String NAME_LINE = SignStyle.paint(ChatColour.DARK_AQUA, "-Alpha-");

    /** Paper's three methods, on a sign face type that has them on every API build. */
    interface PaperSide extends SignSide
    {
        void line(int index, Component line);

        Component line(int index);

        List<Component> lines();
    }

    private PaperSide side;

    @BeforeEach
    void setUp()
    {
        side = mock(PaperSide.class);
    }

    @AfterEach
    void tearDown()
    {
        SignText.forget();
    }

    private static void asOnPaper() throws NoSuchMethodException
    {
        SignText.lookupWith(new SignText.Lookup(PaperSide.class.getMethod("line", int.class, Component.class),
            PaperSide.class.getMethod("line", int.class), PaperSide.class.getMethod("lines")));
    }

    private static Component component(final String legacyText)
    {
        return LegacyComponentSerializer.legacySection().deserialize(legacyText);
    }

    /** Paper deprecates the string setter, so a Paper sign is given its line as a component, colours read as colours. */
    @Test
    void aPaperSignIsGivenTheLineAsAComponent() throws NoSuchMethodException
    {
        asOnPaper();

        SignText.set(side, 0, NAME_LINE);

        verify(side).line(0, component(NAME_LINE));
        verify(side, never()).setLine(anyInt(), anyString());
    }

    @Test
    void aServerWithoutComponentLinesIsGivenTheString()
    {
        SignText.lookupWith(null);

        SignText.set(side, 2, NAME_LINE);

        verify(side).setLine(2, NAME_LINE);
    }

    /** The dial sign is the gate's whole interface; a Paper that refuses the component still gets the line. */
    @Test
    void aRefusedComponentLineFallsBackToTheString() throws NoSuchMethodException
    {
        asOnPaper();
        doThrow(new IllegalStateException("refused")).when(side).line(anyInt(), any(Component.class));

        SignText.set(side, 1, NAME_LINE);

        verify(side).setLine(1, NAME_LINE);
    }

    /**
     * What is written reads back the same through Paper's components.
     *
     * <p>Line 0 of a dial sign is read back as the gate's name when the gate is detected, so a
     * coloured line, an empty one and a full fifteen characters all have to survive the trip.
     */
    @Test
    void aLineReadsBackOnPaperAsTheTextWritten() throws NoSuchMethodException
    {
        asOnPaper();
        for (final String text : new String[] { NAME_LINE, "", "FifteenLetters!" })
        {
            final PaperSide face = mock(PaperSide.class);
            final ArgumentCaptor<Component> written = ArgumentCaptor.forClass(Component.class);
            SignText.set(face, 0, text);
            verify(face).line(eq(0), written.capture());
            when(face.line(0)).thenReturn(written.getValue());

            assertEquals(text, SignText.get(face, 0), "the line would read back changed");
        }
    }

    /** A hex colour, in Bukkit's {@code §x} form, survives the trip rather than becoming the nearest named colour. */
    @Test
    void aHexColouredLineReadsBackOnPaperWithItsHexColour() throws NoSuchMethodException
    {
        asOnPaper();
        final String hex = "§x§1§2§a§b§c§dTeal";
        final ArgumentCaptor<Component> written = ArgumentCaptor.forClass(Component.class);
        SignText.set(side, 1, hex);
        verify(side).line(eq(1), written.capture());
        when(side.line(1)).thenReturn(written.getValue());

        assertEquals(hex, SignText.get(side, 1), "the hex colour would be rounded to a named one");
    }

    @Test
    void aServerWithoutComponentLinesReadsTheString()
    {
        SignText.lookupWith(null);
        when(side.getLine(0)).thenReturn(NAME_LINE);

        assertEquals(NAME_LINE, SignText.get(side, 0));
    }

    @Test
    void aRefusedComponentReadFallsBackToTheString() throws NoSuchMethodException
    {
        asOnPaper();
        when(side.line(0)).thenThrow(new IllegalStateException("refused"));
        when(side.getLine(0)).thenReturn(NAME_LINE);

        assertEquals(NAME_LINE, SignText.get(side, 0));
    }

    /** A restyled dial sign is copied line by line, so every line comes back in legacy text, in order. */
    @Test
    void aPaperSignsLinesReadAsLegacyText() throws NoSuchMethodException
    {
        asOnPaper();
        when(side.lines()).thenReturn(List.of(component(NAME_LINE), component("Beta"), Component.empty(),
            component("§7Gamma")));

        assertArrayEquals(new String[] { NAME_LINE, "Beta", "", "§7Gamma" }, SignText.lines(side));
    }

    @Test
    void aServerWithoutComponentLinesReadsTheStrings()
    {
        SignText.lookupWith(null);
        final String[] lines = { NAME_LINE, "Beta", "", "" };
        when(side.getLines()).thenReturn(lines);

        assertArrayEquals(lines, SignText.lines(side));
    }

    @Test
    void refusedComponentLinesFallBackToTheStrings() throws NoSuchMethodException
    {
        asOnPaper();
        final String[] lines = { NAME_LINE, "Beta", "", "" };
        when(side.lines()).thenThrow(new IllegalStateException("refused"));
        when(side.getLines()).thenReturn(lines);

        assertArrayEquals(lines, SignText.lines(side));
    }

    /**
     * Spigot and CraftBukkit may have no Adventure at all. Hiding {@code net.kyori} from a fresh copy
     * of the class stands in for that: the lookup fails to link, and the sign is still written.
     */
    @Test
    void aServerWithoutAdventureIsGivenTheString() throws Throwable
    {
        final Method set = new IsolatedCopy(SignText.class, "net.kyori.").loadClass(SignText.class.getName())
            .getMethod("set", SignSide.class, int.class, String.class);
        final SignSide plain = mock(SignSide.class);

        try
        {
            set.invoke(null, plain, 0, NAME_LINE);
        }
        catch (final InvocationTargetException thrown)
        {
            throw thrown.getCause();
        }

        verify(plain).setLine(0, NAME_LINE);
    }
}
