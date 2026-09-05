package com.wormhole_xtreme.wormhole.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * A gate's owner display name never silently becomes the owner's UUID.
 *
 * <p>{@code Stargate.getGateOwnerName()} deliberately falls back to the raw owner id when no
 * display name has been resolved, because for showing a person an id beats nothing. The bug
 * was that saving used that same getter: a gate whose owner the server had not seen yet wrote
 * its UUID into the file's {@code OwnerName} field, and the next load then saw a non-empty
 * name, took it for a real one, and never tried to resolve the UUID again. The id became the
 * gate's name permanently, on its sign and everywhere else.
 *
 * <p>It surfaced through {@code /wormhole refresh}, which rewrites the name sign and then
 * saves -- so a sign that had been showing a correct name written at build time was replaced
 * with a UUID. Refresh only made it visible; any save of such a gate did it.
 */
class StargateOwnerNameTest
{
    private static final String UUID_STR = "11111111-2222-3333-4444-555555555555";

    /**
     * An owner whose name was never resolved writes no name, rather than writing the id.
     *
     * <p>This is the write half of the bug. Writing the id here is what made it permanent,
     * because the read half could no longer tell it from a real name.
     */
    @Test
    void aGateWithNoResolvedNameWritesNoNameRatherThanTheId()
    {
        assertEquals("", StargateYamlManager.ownerNameToSave(null),
            "an unresolved owner must not write its id into the name field");
    }

    @Test
    void aRealDisplayNameIsWrittenAsItIs()
    {
        assertEquals("Notch", StargateYamlManager.ownerNameToSave("Notch"));
    }

    /**
     * A file already carrying the id as its name is treated as having no name, so it heals.
     *
     * <p>Every save written before the fix carries one, so simply writing correctly from now
     * on would leave those gates showing a UUID for ever. Reading it back as "no name" sends
     * the loader down the resolve-from-UUID path again, and the next save stores the answer.
     */
    @Test
    void aNameThatIsJustTheOwnerIdIsTreatedAsNoNameAtAll()
    {
        assertNull(StargateYamlManager.ownerNameFromSave(UUID_STR, UUID_STR),
            "a name equal to the owner id is what the bug wrote, not a name someone chose");
    }

    @Test
    void aRealNameInAFileIsUsed()
    {
        assertEquals("Notch", StargateYamlManager.ownerNameFromSave("Notch", UUID_STR));
    }

    @Test
    void anAbsentOrEmptyNameIsNoName()
    {
        assertNull(StargateYamlManager.ownerNameFromSave(null, UUID_STR));
        assertNull(StargateYamlManager.ownerNameFromSave("", UUID_STR));
    }

    /**
     * A legacy gate whose owner <em>is</em> a player name still ends up with that name.
     *
     * <p>These gates store a name where newer ones store a UUID, so the rule above reads
     * their name as "no name" -- which looks wrong and is not. The loader's next step parses
     * the owner as a UUID, fails, and sets the owner string itself as the display name, which
     * for a legacy gate is exactly the right answer. Pinned because the obvious "fix" for
     * this test is to special-case legacy gates, and nothing needs to.
     */
    @Test
    void aLegacyNameBasedOwnerIsStillHandledByTheCallersFallback()
    {
        assertNull(StargateYamlManager.ownerNameFromSave("Notch", "Notch"),
            "the rule cannot tell this apart, and does not need to -- the caller resolves it");
    }

    /**
     * The stored name and the displayed name are different questions.
     *
     * <p>Anything copying or saving the field has to ask the first one. {@code refresh} asked
     * the second and wrote the answer back onto the gate it rebuilt.
     */
    @Test
    void theStoredNameIsEmptyWhereTheDisplayedOneFallsBackToTheId()
    {
        final Stargate gate = new Stargate();
        gate.setGateOwner(UUID_STR);

        assertNull(gate.getStoredGateOwnerName(),
            "nothing has resolved a name, and the stored value has to say so");
        assertEquals(UUID_STR, gate.getGateOwnerName(),
            "for display an id still beats showing nothing");
    }

    @Test
    void aResolvedNameIsReturnedByBoth()
    {
        final Stargate gate = new Stargate();
        gate.setGateOwner(UUID_STR);
        gate.setGateOwnerName("Notch");

        assertEquals("Notch", gate.getStoredGateOwnerName());
        assertEquals("Notch", gate.getGateOwnerName());
    }
}
