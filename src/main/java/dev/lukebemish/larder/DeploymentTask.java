package dev.lukebemish.larder;

import dev.lukebemish.larder.orm.ModelConnection;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

abstract class DeploymentTask implements Runnable {
    protected DeploymentTask(Scheduler owner) {
        this.owner = owner;
    }

    static final class Scheduler {
        private static final int TASKS_TO_ENQUEUE_AT_ONCE = Integer.parseInt(System.getenv().getOrDefault("LARDER_DEPLOYMENT_TASKS_BATCH_SIZE", "10"));
        private static final Duration TASK_EXPIRY_DELAY = Duration.parse(System.getenv().getOrDefault("LARDER_DEPLOYMENT_TASK_RETRY_TIME", "30S"));
        private static final Duration DROP_DEPLOYMENTS_AFTER = Duration.parse(System.getenv().getOrDefault("LARDER_DEPLOYMENT_DROP_FAILED_AFTER", "30D"));

        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final AtomicInteger activeTasks = new AtomicInteger(0);
        private final ModelConnection connection;

        Scheduler(ModelConnection connection) {
            this.connection = connection;
        }

        void enqueueMoreWork() {
            // Workflow should be:
            // - establish <SOME_NUMBER> of different tasks, and generate unique IDs
            // - create only tasks which do not have an active run attempt
            // - push a task expiry at <SOME_TIME> in the future (which will free up those tasks to be tried again)
            // - increment ACTIVE_TASKS the right number of times
            // - submit the tasks to the executor on this end
            // - prioritize oldest deployment tasks first
            // Deployment tasks are established for any deployment state transition that requires work on this end
        }
    }

    private final Scheduler owner;

    @Override
    public final void run() {
        try {
            execute();
        } finally {
            if (owner.activeTasks.decrementAndGet() <= 0) {
                // queue is empty, ask for more deployment tasks to run
                owner.enqueueMoreWork();
            }
        }
    }

    abstract void execute();
}
