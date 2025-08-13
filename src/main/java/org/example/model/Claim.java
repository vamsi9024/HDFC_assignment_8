package org.example.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class Claim implements Comparable<Claim> {
    // --- CORRECTED FORMATTER ---
    // The pattern now uses a space instead of 'T' to match the CSV data.
    private static final DateTimeFormatter CSV_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String claimID;
    private final String policyNumber;
    private final int claimAmount;
    private final String claimType;
    private final LocalDateTime timestamp;
    private final String priorityFlag;

    private final AtomicReference<ClaimStatus> status;
    private final AtomicInteger attempts;

    public Claim(String claimID, String policyNumber, int claimAmount, String claimType, LocalDateTime timestamp, String priorityFlag) {
        this.claimID = claimID;
        this.policyNumber = policyNumber;
        this.claimAmount = claimAmount;
        this.claimType = claimType;
        this.timestamp = timestamp;
        this.priorityFlag = priorityFlag;
        this.status = new AtomicReference<>(ClaimStatus.PENDING);
        this.attempts = new AtomicInteger(0);
    }

    public static Claim fromCsvRow(String[] csvRow) {
        return new Claim(
                csvRow[0],
                csvRow[1],
                Integer.parseInt(csvRow[2]),
                csvRow[3],
                // Use the corrected formatter to parse the timestamp
                LocalDateTime.parse(csvRow[4], CSV_TIMESTAMP_FORMATTER),
                csvRow[5]
        );
    }

    @Override
    public int compareTo(Claim other) {
        boolean thisIsUrgent = isUrgent();
        boolean otherIsUrgent = other.isUrgent();

        if (thisIsUrgent && !otherIsUrgent) return -1;
        if (!thisIsUrgent && otherIsUrgent) return 1;

        return this.timestamp.compareTo(other.timestamp);
    }

    public boolean isUrgent() {
        return "URGENT".equalsIgnoreCase(this.priorityFlag);
    }

    // --- Getters and other methods remain the same ---

    public String getClaimID() { return claimID; }
    public String getPolicyNumber() { return policyNumber; }
    public int getClaimAmount() { return claimAmount; }
    public String getClaimType() { return claimType; }
    public ClaimStatus getStatus() { return status.get(); }
    public int getAttempts() { return attempts.get(); }

    public int incrementAndGetAttempts() {
        return this.attempts.incrementAndGet();
    }

    public ClaimStatus getAndSetStatus(ClaimStatus newStatus) {
        return this.status.getAndSet(newStatus);
    }

    @Override
    public String toString() {
        return String.format("Claim[ID=%s, Policy=%s, Status=%s, Priority=%s]",
                claimID, policyNumber, status.get(), priorityFlag);
    }
}