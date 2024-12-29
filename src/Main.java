import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

public class Main {

    private static final int MAX_CLIENT_DELAY = 5; // N: Maximum delay between client orders
    private static final int MAX_PROCESSING_TIME = 10; // M: Maximum processing time for an order
    private static final int INITIAL_WORKERS = 3; // Initial number of workers

    private static final BlockingQueue<String> orderQueue = new LinkedBlockingQueue<>();
    private static final AtomicInteger activeWorkers = new AtomicInteger(0);

    private static volatile boolean running = true;

    public static void main(String[] args) {
        ScheduledExecutorService clientService = Executors.newSingleThreadScheduledExecutor();
        ExecutorService workerService = Executors.newCachedThreadPool();

        // Start clients
        clientService.scheduleAtFixedRate(() -> {
            String order = "Order-" + System.currentTimeMillis();
            System.out.println("New order placed: " + order);
            orderQueue.offer(order);
        }, 0, MAX_CLIENT_DELAY, TimeUnit.SECONDS);

        // Start workers
        for (int i = 0; i < INITIAL_WORKERS; i++) {
            workerService.execute(new Worker(i % 2 == 0 ? "Elizabeth" : "Daniil"));
            activeWorkers.incrementAndGet();
        }

        // Adjust workers dynamically
        ScheduledExecutorService adjusterService = Executors.newSingleThreadScheduledExecutor();
        adjusterService.scheduleAtFixedRate(() -> {
            int queueSize = orderQueue.size();
            int currentWorkers = activeWorkers.get();

            if (queueSize > currentWorkers) {
                workerService.execute(new Worker(activeWorkers.get() % 2 == 0 ? "Elizabeth" : "Daniil"));
                activeWorkers.incrementAndGet();
                System.out.println("Added a worker. Total workers: " + activeWorkers.get());
            } else if (queueSize < currentWorkers - 1 && currentWorkers > 1) {
                activeWorkers.decrementAndGet();
                System.out.println("Reduced a worker. Total workers: " + activeWorkers.get());
            }
        }, 10, 10, TimeUnit.SECONDS);

        // Run for a specified duration and then shut down
        ScheduledExecutorService shutdownService = Executors.newSingleThreadScheduledExecutor();
        shutdownService.schedule(() -> {
            running = false;
            clientService.shutdown();
            adjusterService.shutdown();
            workerService.shutdown();

            try {
                clientService.awaitTermination(5, TimeUnit.SECONDS);
                adjusterService.awaitTermination(5, TimeUnit.SECONDS);
                workerService.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Shutdown interrupted: " + e.getMessage());
            }

            System.out.println("Order processing system terminated.");
        }, 1, TimeUnit.MINUTES);
    }

    static class Worker implements Runnable {
        private final String name;

        public Worker(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            Random random = new Random();

            while (running && activeWorkers.get() > 0) {
                try {
                    String order = orderQueue.poll(5, TimeUnit.SECONDS);

                    if (order != null) {
                        System.out.println(name + " processing " + order);
                        Thread.sleep(random.nextInt(MAX_PROCESSING_TIME) * 1000L);
                        System.out.println(name + " completed " + order);
                    } else if (activeWorkers.get() > 1) {
                        // If there's no work and excess workers, terminate this thread
                        activeWorkers.decrementAndGet();
                        System.out.println(name + " exiting due to inactivity.");
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println(name + " interrupted: " + e.getMessage());
                }
            }
        }
    }
}
