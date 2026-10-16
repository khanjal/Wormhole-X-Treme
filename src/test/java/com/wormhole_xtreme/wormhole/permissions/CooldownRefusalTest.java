package com.wormhole_xtreme.wormhole.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.wormhole_xtreme.wormhole.utils.ChatMatchers;
import com.wormhole_xtreme.wormhole.utils.ChatText;

/**
 * A player still under the gate cooldown is told how long is left in one line (#438).
 *
 * <p>It used to be two error lines, "You must wait longer before using a stargate." and then
 * "Current Wait (in seconds): 28", the number glued to the end of a constant. One sentence with
 * the seconds in it says the same thing, and the number is a value, in the palette's white.
 */
class CooldownRefusalTest
{
    @Test
    void theRefusalSaysHowManySecondsAreLeftAsAValue()
    {
        assertEquals(ChatText.ERROR_HEADER + "You must wait " + ChatText.COMMAND_COLOUR + "28 seconds"
            + ChatText.BODY_COLOUR + " before using a stargate again.", StargateRestrictions.cooldownRefusal(28L));
    }

    @Test
    void oneSecondLeftIsSaidInTheSingular()
    {
        assertEquals("You must wait 1 second before using a stargate again.",
            ChatMatchers.body(StargateRestrictions.cooldownRefusal(1L)), "not \"1 seconds\"");
    }
}
