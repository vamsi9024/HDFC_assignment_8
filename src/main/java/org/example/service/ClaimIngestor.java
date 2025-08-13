package org.example.service;

import org.example.model.Claim;
import org.example.state.ThrottlingState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ClaimIngestor implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ClaimIngestor.class);

    private final String csvFilePath;
    private final BlockingQueue<Claim> claimQueue;
    private final ThrottlingState throttlingState;
    private final Set<String> processedClaimIds;
    private final AtomicInteger totalIngested;

    public ClaimIngestor(String csvFilePath, BlockingQueue<Claim> claimQueue, ThrottlingState throttlingState, Set<String> processedClaimIds, AtomicInteger totalIngested) {
        this.csvFilePath = csvFilePath;
        this.claimQueue = claimQueue;
        this.throttlingState = throttlingState;
        this.processedClaimIds = processedClaimIds;
        this.totalIngested = totalIngested;
    }

    @Override
    public void run() {
        log.info("Starting claim ingestion from '{}'...", csvFilePath);
        int lineCount = 1;
        try (BufferedReader br = Files.newBufferedReader(Paths.get(csvFilePath))) {
            br.readLine();

            String line;
            while ((line = br.readLine()) != null) {
                lineCount++;
                while (throttlingState.isThrottling()) {
                    Thread.sleep(200);
                }

                String[] values = line.split(",", -1);
                if (values.length < 6) {
                    log.warn("Skipping malformed CSV row at line {}: {}", lineCount, line);
                    continue;
                }

                Claim claim = Claim.fromCsvRow(values);

                if (processedClaimIds.add(claim.getClaimID())) {
                    claimQueue.put(claim);
                    totalIngested.incrementAndGet();
                } else {
                    log.warn("Skipping duplicate claim ID found during ingestion: {}", claim.getClaimID());
                }
            }
        } catch (InterruptedException e) {
            log.warn("Claim ingestion was interrupted.");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Failed to ingest claims from CSV file at line " + lineCount, e);
        } finally {
            log.info("Claim ingestion finished. No more claims will be added.");
        }
    }
}