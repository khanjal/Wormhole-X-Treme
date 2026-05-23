// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2011 Ben Echols, Dean Bailey. See LICENSE.txt for terms.
package com.wormhole_xtreme.wormhole.utils;
import java.io.File;
import java.util.logging.Level;
import com.wormhole_xtreme.wormhole.WormholeXTreme;

/**
 * WormholeXTreme DBUpdateUtil.
 * 
 * @author Ben Echols (Lologarithm)
 */
public class DBUpdateUtil
{

    /**
     * Update db.
     * 
     * @return true, if successful
     */
    public static boolean updateDB()
    {
        final File dir = new File("plugins" + File.separator + "WormholeXTremeDB" + File.separator);
        final File dest_dir = new File("plugins" + File.separator + "WormholeXTreme" + File.separator + "WormholeXTremeDB" + File.separator);
        if (dir.exists() && dir.isDirectory())
        {
            if ( !dest_dir.exists())
            {
                try
                {
                    dest_dir.mkdir();
                }
                catch (final Exception e)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false, "Unable to make directory: " + e.getMessage());
                }
            }
            final File[] files = dir.listFiles();
            for (final File f : files)
            {
                try
                {
                    f.renameTo(new File(dest_dir, f.getName()));
                }
                catch (final Exception e)
                {
                    WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false, "Unable to rename files: " + e.getMessage());
                }
            }

            try
            {
                dir.delete();
            }
            catch (final Exception e)
            {
                WormholeXTreme.getThisPlugin().prettyLog(Level.SEVERE, false, "Unable to delete directory: " + e.getMessage());
            }
        }

        // HSQLDB support removed. Database update via embedded HSQL is no longer supported.
        WormholeXTreme.getThisPlugin().prettyLog(Level.INFO, false, "HSQLDB support removed; DB update skipped.");
        return false;
    }
}