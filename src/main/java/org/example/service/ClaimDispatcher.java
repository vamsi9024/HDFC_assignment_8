package org.example.service;

import org.example.config.AppConfig;
import org.example.model.Claim;
import org.example.model.ClaimStatus; // <-- IMPORTANT: Added correct import
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ClaimDispatcher implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ClaimDispatcher.class);

    private final BlockingQueue<Claim> incomingClaimsQueue;
    private final ExecutorService workerPool;
    private final AppConfig config;
    private final FraudDetector fraudDetector;
    private final List<Claim> finalResults;

    private final Map<String, Lock> policyLocks = new ConcurrentHashMap<>();
    private final Map<String, Queue<Claim>> policyQueues = new ConcurrentHashMap<>();

    private volatile boolean isRunning = true;

    public ClaimDispatcher(BlockingQueue<Claim> incomingClaimsQueue, ExecutorService workerPool, AppConfig config, FraudDetector fraudDetector, List<Claim> finalResults) {
        this.incomingClaimsQueue = incomingClaimsQueue;
        this.workerPool = workerPool;
        this.config = config;
        this.fraudDetector = fraudDetector;
        this.finalResults = finalResults;
    }

    @Override
    public void run() {
        log.info("Claim dispatcher started.");
        while (isRunning || !incomingClaimsQueue.isEmpty() || hasPendingWork()) {
            try {
                Claim claim = incomingClaimsQueue.poll(100, TimeUnit.MILLISECONDS);
                if (claim != null) {
                    fraudDetector.checkForSuspiciousActivity(claim);
                    policyLocks.computeIfAbsent(claim.getPolicyNumber(), k -> new ReentrantLock());
                    policyQueues.computeIfAbsent(claim.getPolicyNumber(), k -> new ConcurrentLinkedQueue<>()).add(claim);
                }
                dispatchFromPolicyQueues();
            } catch (InterruptedException e) {
                if (isRunning) log.warn("Claim dispatcher was interrupted.");
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Claim dispatcher has finished processing all known claims.");
    }

    private void dispatchFromPolicyQueues() {
        policyQueues.forEach((policyNumber, queue) -> {
            if (!queue.isEmpty()) {
                Lock lock = policyLocks.get(policyNumber);
                if (lock.tryLock()) {
                    try {
                        Claim claimToProcess = queue.peek();
                        // CORRECTED: Uses the imported ClaimStatus class
                        if (claimToProcess != null && claimToProcess.getStatus() == ClaimStatus.PENDING) {
                            workerPool.submit(new ClaimWorker(claimToProcess, this, config));
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            }
        });
    }

    public void onTaskCompleted(Claim claim) {
        // CORRECTED: Uses the imported ClaimStatus class
        if (claim.getStatus() != ClaimStatus.PENDING) {
            // A claim is only considered "done" and removed from its queue if it's no longer pending.
            // If it failed and is pending a retry, it stays at the head of the queue.
            Queue<Claim> queue = policyQueues.get(claim.getPolicyNumber());
            if (queue != null) {
                queue.poll(); // Remove the finished claim from the queue.
            }
            finalResults.add(claim); // Add to the final report list.
        }
    }

    public void requeueClaim(Claim claim) {
        // The claim is already at the head of its queue because it was never removed
        // in onTaskCompleted (since its status is PENDING).
        // The dispatcher will automatically try to process it again on the next loop.
        // No action is needed here.
    }

    private boolean hasPendingWork() {
        return policyQueues.values().stream().anyMatch(q -> !q.isEmpty());
    }

    public void shutdown() {
        this.isRunning = false;
    }
}