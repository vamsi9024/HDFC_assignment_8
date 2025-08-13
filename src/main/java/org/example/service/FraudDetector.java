package org.example.service;

import org.example.config.AppConfig;
import org.example.model.Claim; // <-- IMPORTANT: Use the correct import
import org.example.state.ThrottlingState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A background service that monitors for suspicious claim activity in real-time.
 * If a configured threshold is breached within a sliding time window, it activates
 * system-wide throttling.
 */
public class FraudDetector implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(FraudDetector.class);

    private final ThrottlingState throttlingState;
    private final ConcurrentLinkedDeque<Instant> suspiciousClaimTimestamps = new ConcurrentLinkedDeque<>();
    private final AtomicInteger suspiciousClaimCount = new AtomicInteger(0);

    // Config values cached for performance
    private final String suspiciousType;
    private final int suspiciousAmount;
    private final int threshold;
    private final long windowSeconds;
    private final long pauseMs;

    public FraudDetector(AppConfig config, ThrottlingState throttlingState) {
        this.throttlingState = throttlingState;
        this.suspiciousType = config.getString("suspicious.claim.type");
        this.suspiciousAmount = config.getInt("suspicious.claim.amount");
        this.threshold = config.getInt("suspicious.threshold");
        this.windowSeconds = config.getLong("suspicious.window.seconds");
        this.pauseMs = config.getLong("throttling.pause.ms");
    }

    /**
     * Checks if a claim meets the criteria to be considered suspicious.
     * If so, it logs the event and records its timestamp for throttling analysis.
     * @param claim The claim to check.
     */
    // CORRECTED METHOD SIGNATURE: Uses the imported 'Claim' class
    public void checkForSuspiciousActivity(Claim claim) {
        if (suspiciousType.equalsIgnoreCase(claim.getClaimType()) && claim.getClaimAmount() >= suspiciousAmount) {
            log.warn("SUSPICIOUS CLAIM DETECTED: ID={}, Amount={}, Type={}",
                    claim.getClaimID(), claim.getClaimAmount(), claim.getClaimType());
            suspiciousClaimTimestamps.add(Instant.now());
            suspiciousClaimCount.incrementAndGet();
        }
    }

    @Override
    public void run() {
        log.info("Fraud detector started. Window: {}s, Threshold: {}", windowSeconds, threshold);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Instant windowStart = Instant.now().minusSeconds(windowSeconds);
                suspiciousClaimTimestamps.removeIf(timestamp -> timestamp.isBefore(windowStart));

                if (suspiciousClaimTimestamps.size() > threshold) {
                    if (!throttlingState.isThrottling()) {
                        throttlingState.set(true);
                        log.error("!!! High suspicious activity detected! Engaging throttling for {}ms. !!!", pauseMs);
                        Thread.sleep(pauseMs); // Hold the throttle
                        throttlingState.set(false);
                        log.info("--- Throttling disengaged. Resuming normal claim intake. ---");
                    }
                }
                Thread.sleep(500); // Check for breaches every 500ms
            } catch (InterruptedException e) {
                log.warn("Fraud detector was interrupted.");
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Fraud detector shut down.");
    }

    public int getSuspiciousClaimCount() {
        return suspiciousClaimCount.get();
    }
}