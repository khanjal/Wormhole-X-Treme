package com.wormhole_xtreme.wormhole.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * bStats (#239): how counts are sent.
 *
 * <p>Counts go out as ranges, so a chart groups servers by size and no server's exact numbers
 * are sent.
 */
class MetricsSupportTest
{
    /** Each range starts where the last ends, with none skipped and none overlapping. */
    @Test
    void countsAreSentAsRanges()
    {
        assertEquals("0", MetricsSupport.range(0));
        assertEquals("1-5", MetricsSupport.range(1));
        assertEquals("1-5", MetricsSupport.range(5));
        assertEquals("6-20", MetricsSupport.range(6));
        assertEquals("6-20", MetricsSupport.range(20));
        assertEquals("21-50", MetricsSupport.range(21));
        assertEquals("21-50", MetricsSupport.range(50));
        assertEquals("51-200", MetricsSupport.range(51));
        assertEquals("51-200", MetricsSupport.range(200));
        assertEquals("200+", MetricsSupport.range(201));
    }
}
