package de.marvin.playtime.core.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Utility class for scheduling tasks.
 */
public class TaskScheduler {

    /**
     * Logger instance for logging.
     */
    private static final Logger LOGGER = Logger.getLogger(TaskScheduler.class.getName());

    /**
     * {@link ScheduledExecutorService} for scheduling tasks.
     */
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1);

    /**
     * Schedules a repeating task with a fixed delay and period.
     *
     * @param task     task to be executed
     * @param delay    initial delay before the task is executed
     * @param period   period between successive executions of the task
     * @param timeUnit {@link TimeUnit} for the delay and period
     * @return {@link ScheduledFuture} representing pending completion of the task or
     *         {@code null} if the {@link ScheduledExecutorService} is shut down.
     */
    public static @Nullable ScheduledFuture<?> scheduleTask(
            @NotNull Runnable task,
            long delay,
            long period,
            @NotNull TimeUnit timeUnit
    ) {
        if (SCHEDULER.isShutdown()) return null;
        return SCHEDULER.scheduleAtFixedRate(task, delay, period, timeUnit);
    }

    /**
     * Shuts down the {@link ScheduledExecutorService}.
     */
    public static void shutdown() {
        SCHEDULER.shutdown();
        LOGGER.info("TaskScheduler has been shut down.");
    }

}
