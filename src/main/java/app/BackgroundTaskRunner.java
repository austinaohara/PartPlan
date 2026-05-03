package app;

import javafx.concurrent.Task;

import java.util.Objects;
import java.util.function.Consumer;

public final class BackgroundTaskRunner {
    private BackgroundTaskRunner() {
    }

    public static <T> void run(
            String threadName,
            BackgroundAction<T> action,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure
    ) {
        Objects.requireNonNull(threadName, "threadName must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(onSuccess, "onSuccess must not be null");
        Objects.requireNonNull(onFailure, "onFailure must not be null");

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return action.run();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onFailure.accept(task.getException() == null
                ? new IllegalStateException("Background task failed.")
                : task.getException()));

        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    public static void run(
            String threadName,
            BackgroundRunnable action,
            Runnable onSuccess,
            Consumer<Throwable> onFailure
    ) {
        Objects.requireNonNull(onSuccess, "onSuccess must not be null");
        run(threadName, () -> {
            action.run();
            return null;
        }, ignored -> onSuccess.run(), onFailure);
    }

    @FunctionalInterface
    public interface BackgroundAction<T> {
        T run() throws Exception;
    }

    @FunctionalInterface
    public interface BackgroundRunnable {
        void run() throws Exception;
    }
}
