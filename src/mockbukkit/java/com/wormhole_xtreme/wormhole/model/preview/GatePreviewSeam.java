package com.wormhole_xtreme.wormhole.model.preview;

/** Lets a MockBukkit test reach GatePreviews' package-private clear. */
public final class GatePreviewSeam
{
    private GatePreviewSeam()
    {
    }

    /** Forgets every preview, so the next MockBukkit class in the JVM starts with none. */
    public static void clear()
    {
        GatePreviews.clear();
    }
}
