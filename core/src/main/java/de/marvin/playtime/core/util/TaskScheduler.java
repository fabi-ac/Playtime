package de.marvin.playtime.core.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Utility class for scheduling tasks.
 */
public class TaskScheduler {

    /**
     * {@link Logger} instance for logging.
     */
    private static final Logger LOGGER = Logger.getLogger(TaskScheduler.class.getName());

    /**
     * {@link ScheduledExecutorService} for scheduling periodic tasks.
     */
    private static final ScheduledExecutorService SCHEDULED_TASK_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor();

    /**
     * {@link ExecutorService} for independent asynchronous tasks that may block.
     */
    private static final ExecutorService ASYNC_TASK_EXECUTOR =
            Executors.newVirtualThreadPerTaskExecutor();

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
        try {
            return SCHEDULED_TASK_EXECUTOR.scheduleAtFixedRate(task, delay, period, timeUnit);
        } catch (RejectedExecutionException exception) {
            return null;
        }
    }

    /**
     * Executes a task asynchronously without blocking the periodic task executor.
     *
     * @param task Task to execute
     * @return {@link Future} representing the task, or {@code null} if the executor is shut down
     */
    public static @Nullable Future<?> executeTask(
            @NotNull Runnable task
    ) {
        try {
            return ASYNC_TASK_EXECUTOR.submit(task);
        } catch (RejectedExecutionException exception) {
            return null;
        }
    }

    /**
     * Shuts down the periodic and asynchronous task executors and interrupts tasks that are still running.
     */
    public static void shutdown() {
        SCHEDULED_TASK_EXECUTOR.shutdownNow();
        ASYNC_TASK_EXECUTOR.shutdownNow();
        LOGGER.info("TaskScheduler has been shut down.");
    }

}
