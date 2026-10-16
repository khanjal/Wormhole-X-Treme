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

    /**
     * The refusal is decided before the time left is read, so a cooldown that runs out in between
     * reads 0, or -1 once its entry is gone; the player is still refused, so it says one second.
     */
    @Test
    void aCooldownThatRanOutMidCheckStillSaysOneSecond()
    {
        final String oneSecond = "You must wait 1 second before using a stargate again.";
        assertEquals(oneSecond, ChatMatchers.body(StargateRestrictions.cooldownRefusal(0L)), "not \"0 seconds\"");
        assertEquals(oneSecond, ChatMatchers.body(StargateRestrictions.cooldownRefusal(-1L)), "not \"-1 seconds\"");
    }
}
