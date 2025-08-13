package org.example;

import org.example.config.AppConfig;
import org.example.model.Claim;
import org.example.service.*;
import org.example.state.ThrottlingState;
// --- CORRECT IMPORTS ---
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    // --- CORRECTED LOGGER DECLARATION (NO CAST) ----
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("System starting up...");
        long startTime = System.currentTimeMillis();

        // 1. Load Configuration
        final AppConfig config = new AppConfig("config.properties");
        int workerCount = config.getInt("worker.count");
        int backlogCapacity = config.getInt("backlog.capacity");

        // 2. Create Shared State and Core Components
        final ThrottlingState throttlingState = new ThrottlingState();
        final BlockingQueue<Claim> claimQueue = new PriorityBlockingQueue<>(backlogCapacity);
        final Set<String> processedClaimIds = ConcurrentHashMap.newKeySet();
        final ExecutorService workerPool = Executors.newFixedThreadPool(workerCount);
        final List<Claim> allProcessedClaims = new CopyOnWriteArrayList<>();
        final AtomicInteger totalIngested = new AtomicInteger(0);

        // 3. Instantiate Services (Dependency Injection)
        final FraudDetector fraudDetector = new FraudDetector(config, throttlingState);
        final ClaimIngestor ingestor = new ClaimIngestor("claims.csv", claimQueue, throttlingState, processedClaimIds, totalIngested);
        final ClaimDispatcher dispatcher = new ClaimDispatcher(claimQueue, workerPool, config, fraudDetector, allProcessedClaims);

        // 4. Start all background services
        Thread ingestorThread = new Thread(ingestor, "Claim-Ingestor");
        Thread dispatcherThread = new Thread(dispatcher, "Claim-Dispatcher");
        Thread fraudDetectorThread = new Thread(fraudDetector, "Fraud-Detector");

        ingestorThread.start();
        dispatcherThread.start();
        fraudDetectorThread.start();

        // 5. Setup Graceful Shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received. Finishing in-flight work...");
            dispatcher.shutdown();
            fraudDetectorThread.interrupt();
            shutdownAndAwaitTermination(workerPool);
            log.info("Shutdown complete.");
        }));

        try {
            // Wait for ingestion to complete to know the total number of claims
            ingestorThread.join();
            // This line will now compile correctly because 'log' is an org.slf4j.Logger
            log.info("Ingestion finished. Total unique claims to process: {}", totalIngested.get());

            // Wait until all processed claims are collected
            while (allProcessedClaims.size() < totalIngested.get()) {
                Thread.sleep(500); // Poll for completion
            }

        } catch (InterruptedException e) {
            // This line will also compile correctly now
            log.error("Main thread interrupted while waiting for completion.", e);
            Thread.currentThread().interrupt();
        } finally {
            // 6. Finalize and report
            log.info("All claims processed. Initiating final shutdown sequence.");
            dispatcher.shutdown();
            fraudDetectorThread.interrupt();
            shutdownAndAwaitTermination(workerPool);

            long duration = System.currentTimeMillis() - startTime;
            SummaryReporter.generateReports(allProcessedClaims, duration, fraudDetector.getSuspiciousClaimCount(), config);
        }

        log.info("Claim processing system has finished successfully.");
    }

    private static void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                if (!pool.awaitTermination(30, TimeUnit.SECONDS))
                    // This line will also compile correctly now
                    log.error("Worker pool did not terminate.");
            }
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}