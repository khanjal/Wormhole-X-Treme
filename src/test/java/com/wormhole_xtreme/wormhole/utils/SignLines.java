package com.wormhole_xtreme.wormhole.utils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.block.sign.SignSide;
import org.mockito.invocation.Invocation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Reads, stubs and verifies a mock sign face through whichever methods this API build gives it.
 *
 * <p>On Paper's API {@link SignText} calls {@code line} and {@code lines}, never {@code setLine}
 * or {@code getLines}, so a test naming only Spigot's would fail on the Paper legs.
 */
public final class SignLines
{
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character(LegacyComponentSerializer.SECTION_CHAR).hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    /** Static use only. */
    private SignLines()
    {
    }

    /**
     * @param side
     *            the mock written to
     * @return the last text each line was given, in legacy colour codes, null for a line given
     *         none; at least four long, and longer if a line past the fourth was written
     */
    public static String[] writtenTo(final SignSide side)
    {
        final List<String> written = new ArrayList<>(Arrays.asList(new String[4]));
        for (final Invocation call : mockingDetails(side).getInvocations())
        {
            final String method = call.getMethod().getName();
            final Object[] args = call.getArguments();
            if (args.length == 2 && ("setLine".equals(method) || "line".equals(method)))
            {
                final int line = ((Integer) args[0]).intValue();
                while (written.size() <= line)
                {
                    written.add(null);
                }
                written.set(line, args[1] instanceof Component given ? LEGACY.serialize(given) : (String) args[1]);
            }
        }
        return written.toArray(new String[0]);
    }

    /**
     * Gives a mock the lines {@link SignText#lines} reads from it on this API build.
     *
     * @param side
     *            the mock
     * @param lines
     *            its lines, in legacy colour codes, or null for a face reporting none
     * @throws ReflectiveOperationException
     *             never, on an API build that has the method it found
     */
    // Spigot's API has only the methods Paper deprecates.
    @SuppressWarnings("deprecation")
    public static void hold(final SignSide side, final String[] lines) throws ReflectiveOperationException
    {
        final Method paper = paper("lines");
        if (paper == null)
        {
            when(side.getLines()).thenReturn(lines);
        }
        else
        {
            when(paper.invoke(side)).thenReturn(lines == null ? null : Arrays.stream(lines)
                .map(line -> line == null ? null : LEGACY.deserialize(line)).toList());
        }
    }

    /**
     * Verifies, on a mock in verification mode, that its lines were read.
     *
     * @param verifying
     *            what {@code verify} or {@code InOrder.verify} returned
     * @throws ReflectiveOperationException
     *             never, on an API build that has the method it found
     */
    // Spigot's API has only the methods Paper deprecates.
    @SuppressWarnings("deprecation")
    public static void read(final SignSide verifying) throws ReflectiveOperationException
    {
        final Method paper = paper("lines");
        if (paper == null)
        {
            verifying.getLines();
        }
        else
        {
            paper.invoke(verifying);
        }
    }

    /**
     * Verifies, on a mock in verification mode, that some line was written.
     *
     * @param verifying
     *            what {@code verify} or {@code InOrder.verify} returned
     * @throws ReflectiveOperationException
     *             never, on an API build that has the method it found
     */
    // Spigot's API has only the methods Paper deprecates.
    @SuppressWarnings("deprecation")
    public static void wrote(final SignSide verifying) throws ReflectiveOperationException
    {
        final Method paper = paper("line", int.class, Component.class);
        if (paper == null)
        {
            verifying.setLine(anyInt(), anyString());
        }
        else
        {
            paper.invoke(verifying, anyInt(), any(Component.class));
        }
    }

    /** @return Paper's method on {@code SignSide}, or null on Spigot's API */
    private static Method paper(final String name, final Class<?>... types)
    {
        try
        {
            return SignSide.class.getMethod(name, types);
        }
        catch (final NoSuchMethodException spigot)
        {
            return null;
        }
    }
}
