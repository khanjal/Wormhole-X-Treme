package wxperf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;

/**
 * A measuring plugin for scripts/perf-boot.sh, installed beside Wormhole and in the control run
 * without it: every tick's duration between {@code perftick start <label>} and {@code perftick stop},
 * and the heap after a full collection at each end. Never shipped; compiled at run time against the
 * server's own API.
 */
public final class WxPerfTick extends JavaPlugin implements Listener
{
    /** Ticks after start that are not counted: the one that ran the command and its collection. */
    private static final int SKIP = 3;

    private double[] ticks = new double[24_000];
    private int count;
    private int skip;
    private boolean recording;
    private String label = "run";
    private long heapStart;
    private long heapPeak;

    @Override
    public void onEnable()
    {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, () ->
        {
            if (recording)
            {
                heapPeak = Math.max(heapPeak, used());
            }
        }, 20L, 20L);
        getLogger().info("WXPERF ready, pid " + ProcessHandle.current().pid());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickEnd(final ServerTickEndEvent event)
    {
        if (!recording)
        {
            return;
        }
        if (skip > 0)
        {
            skip--;
            return;
        }
        if (count == ticks.length)
        {
            ticks = Arrays.copyOf(ticks, ticks.length * 2);
        }
        ticks[count++] = event.getTickDuration();
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String alias, final String[] args)
    {
        if ((args.length >= 1) && "start".equals(args[0]))
        {
            label = (args.length >= 2) ? args[1] : "run";
            System.gc();
            heapStart = used();
            heapPeak = heapStart;
            count = 0;
            skip = SKIP;
            recording = true;
            getLogger().info("WXPERF started " + label + ", heap after GC " + mb(heapStart) + " MB");
            return true;
        }
        if ((args.length >= 1) && "stop".equals(args[0]))
        {
            recording = false;
            final double[] run = Arrays.copyOf(ticks, count);
            System.gc();
            final long heapEnd = used();
            getLogger().info("WXPERF result " + summary(run, heapEnd));
            write(run);
            return true;
        }
        sender.sendMessage("perftick start <label> | perftick stop");
        return true;
    }

    /** The run as one line of JSON: tick statistics in ms, the ten longest ticks by second into the run, heap in MB. */
    private String summary(final double[] run, final long heapEnd)
    {
        final double[] sorted = run.clone();
        Arrays.sort(sorted);
        double sum = 0;
        int over50 = 0;
        int over100 = 0;
        for (final double t : run)
        {
            sum += t;
            over50 += (t > 50.0) ? 1 : 0;
            over100 += (t > 100.0) ? 1 : 0;
        }
        final Integer[] order = new Integer[run.length];
        for (int i = 0; i < run.length; i++)
        {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(run[b], run[a]));
        final StringBuilder worst = new StringBuilder();
        double elapsed = 0;
        final double[] at = new double[run.length];
        for (int i = 0; i < run.length; i++)
        {
            // Seconds into the run, taking a tick as at least 50 ms: a long tick delays the next.
            at[i] = elapsed / 1000.0;
            elapsed += Math.max(50.0, run[i]);
        }
        for (int i = 0; i < Math.min(10, order.length); i++)
        {
            worst.append(i == 0 ? "" : ",").append(String.format(Locale.ROOT, "[%.1f,%.2f]", at[order[i]], run[order[i]]));
        }
        return String.format(Locale.ROOT,
            "{\"label\":\"%s\",\"ticks\":%d,\"mean\":%.3f,\"p50\":%.3f,\"p95\":%.3f,\"p99\":%.3f,\"max\":%.3f,"
                + "\"over50\":%d,\"over100\":%d,\"heapStartMB\":%d,\"heapEndMB\":%d,\"heapPeakMB\":%d,\"worst\":[%s]}",
            label, run.length, (run.length == 0) ? 0.0 : sum / run.length, pct(sorted, 0.50), pct(sorted, 0.95),
            pct(sorted, 0.99), (sorted.length == 0) ? 0.0 : sorted[sorted.length - 1], over50, over100, mb(heapStart),
            mb(heapEnd), mb(heapPeak), worst);
    }

    /** Every tick's duration, one a line, beside the plugin's folder, for a closer look at a run. */
    private void write(final double[] run)
    {
        final StringBuilder out = new StringBuilder();
        for (final double t : run)
        {
            out.append(String.format(Locale.ROOT, "%.3f%n", t));
        }
        try
        {
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(Path.of(getDataFolder().getPath(), label + ".ticks"), out, StandardCharsets.UTF_8);
        }
        catch (final IOException e)
        {
            getLogger().warning("could not write the ticks: " + e.getMessage());
        }
    }

    private static double pct(final double[] sorted, final double p)
    {
        return (sorted.length == 0) ? 0.0 : sorted[Math.min(sorted.length - 1, (int) Math.ceil(p * sorted.length) - 1)];
    }

    private static long used()
    {
        final Runtime r = Runtime.getRuntime();
        return r.totalMemory() - r.freeMemory();
    }

    private static long mb(final long bytes)
    {
        return bytes / (1024L * 1024L);
    }
}
