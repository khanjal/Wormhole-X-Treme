package com.wormhole_xtreme.wormhole.model.beam;

import org.bukkit.entity.Player;

import com.wormhole_xtreme.wormhole.utils.ChatText;
import com.wormhole_xtreme.wormhole.utils.Messages;

/**
 * What a beam tells its traveller, each line in their own language (#26).
 *
 * <p>Apart from {@link BeamAnimation} because a test cannot load that class: its effect list needs a
 * running server. Each line is built once or twice a trip, never per tick.
 */
final class BeamLines
{
    private BeamLines() {}

    /** @return the refusal, for {@code player} to read, of a second beam while one is running */
    static String alreadyBeaming(final Player player)
    {
        return ChatText.error(Messages.get(player, "beam.anim.already-beaming"));
    }

    /** @return the notice, for {@code player} to read, that their beam to {@code destination} has begun */
    static String beaming(final Player player, final String destination)
    {
        return ChatText.info(Messages.get(player, "beam.anim.beaming", ChatText.name(destination)));
    }

    /** @return the notice, for {@code player} to read, that they have arrived at {@code destination} */
    static String beamed(final Player player, final String destination)
    {
        return ChatText.info(Messages.get(player, "beam.anim.beamed", ChatText.name(destination)));
    }

    /** @return the error, for {@code player} to read, that a beam failed and they were let go */
    static String freed(final Player player)
    {
        return ChatText.error(Messages.get(player, "beam.anim.freed"));
    }
}
