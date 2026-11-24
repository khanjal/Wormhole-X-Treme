package com.wormhole_xtreme.wormhole.model.mirror;

/**
 * Who sees a mirror in a right-click list (#280): its group, and whether it is hidden or private.
 *
 * <p>None of it stops a mirror being another's start: a start is chosen by whoever runs the
 * command, so it is reachable from that mirror whatever this says.
 *
 * @param group
 *            the named group it lists and is listed in, or null for the public network
 * @param hidden
 *            whether it is left out of every right-click list
 * @param restricted
 *            whether it is listed only for players holding {@link MirrorNetwork#SEE_PRIVATE}; the
 *            {@code -private} setting
 */
public record MirrorAccess(String group, boolean hidden, boolean restricted)
{
    /** The public network, listed for everybody: every mirror made before groups. */
    public static final MirrorAccess OPEN = new MirrorAccess(null, false, false);

    /** A blank group is no group. */
    public MirrorAccess
    {
        group = ((group == null) || group.isBlank()) ? null : group;
    }

    /**
     * Whether two mirrors share a group, ignoring case; two with no group share the public one.
     *
     * @param other
     *            the other mirror's access
     * @return true if both are in the same group or both in none
     */
    public boolean sameGroup(final MirrorAccess other)
    {
        return (group == null) ? (other.group == null) : group.equalsIgnoreCase(other.group);
    }

    /**
     * @param newGroup
     *            the group, or null for none
     * @return a new instance; this one is unchanged
     */
    public MirrorAccess withGroup(final String newGroup)
    {
        return new MirrorAccess(newGroup, hidden, restricted);
    }

    /**
     * @param newHidden
     *            true to leave it out of every list
     * @return a new instance; this one is unchanged
     */
    public MirrorAccess withHidden(final boolean newHidden)
    {
        return new MirrorAccess(group, newHidden, restricted);
    }

    /**
     * @param newRestricted
     *            true to list it only for holders of the private node
     * @return a new instance; this one is unchanged
     */
    public MirrorAccess withRestricted(final boolean newRestricted)
    {
        return new MirrorAccess(group, hidden, newRestricted);
    }
}
