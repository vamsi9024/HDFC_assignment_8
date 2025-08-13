package org.example.service;

import org.example.config.AppConfig;
import org.example.model.Claim;
import org.example.model.ClaimStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.Callable;

public class ClaimWorker implements Callable<Claim> {
    private static final Logger log = LoggerFactory.getLogger(ClaimWorker.class);
    private static final Logger auditLog = LoggerFactory.getLogger("AuditLogger");

    private final Claim claim;
    private final ClaimDispatcher dispatcher;
    private final int retryLimit;

    public ClaimWorker(Claim claim, ClaimDispatcher dispatcher, AppConfig config) {
        this.claim = claim;
        this.dispatcher = dispatcher;
        this.retryLimit = config.getInt("retry.limit");
    }

    @Override
    public Claim call() {
        String threadName = Thread.currentThread().getName();
        try {
            int attempt = claim.incrementAndGetAttempts();
            ClaimStatus oldStatus = claim.getAndSetStatus(ClaimStatus.PROCESSING);
            auditLog.info("{},{},{},{},{}", claim.getClaimID(), threadName, oldStatus, ClaimStatus.PROCESSING, attempt);
            log.debug("Processing claim: {}", claim.getClaimID());

            ExternalChecker.CheckResult result = ExternalChecker.performCheck();

            switch (result) {
                case APPROVED:
                    claim.getAndSetStatus(ClaimStatus.APPROVED);
                    auditLog.info("{},{},{},{},{}", claim.getClaimID(), threadName, ClaimStatus.PROCESSING, ClaimStatus.APPROVED, attempt);
                    break;
                case PERMANENT_FAILURE:
                    claim.getAndSetStatus(ClaimStatus.REJECTED);
                    auditLog.info("{},{},{},{},{}", claim.getClaimID(), threadName, ClaimStatus.PROCESSING, ClaimStatus.REJECTED, attempt);
                    break;
                case TRANSIENT_FAILURE:
                    handleFailure(threadName, attempt);
                    break;
            }
        } catch (Exception e) {
            log.error("Unhandled exception processing claim {}", claim.getClaimID(), e);
            handleFailure(Thread.currentThread().getName(), claim.getAttempts());
        } finally {
            dispatcher.onTaskCompleted(claim);
        }
        return claim;
    }

    private void handleFailure(String threadName, int attempt) {
        if (attempt >= retryLimit) {
            claim.getAndSetStatus(ClaimStatus.ESCALATED);
            auditLog.info("{},{},{},{},{}", claim.getClaimID(), threadName, ClaimStatus.PROCESSING, ClaimStatus.ESCALATED, attempt);
            log.warn("Claim {} exceeded retry limit. Escalating.", claim.getClaimID());
        } else {
            claim.getAndSetStatus(ClaimStatus.PENDING);
            auditLog.info("{},{},{},{},{}", claim.getClaimID(), threadName, ClaimStatus.PROCESSING, ClaimStatus.PENDING, attempt);
            log.info("Claim {} failed transiently. Re-queueing for another attempt.", claim.getClaimID());
            dispatcher.requeueClaim(claim);
        }
    }
}