package org.example.model;

public enum ClaimStatus {
    PENDING,
    PROCESSING,
    APPROVED,
    REJECTED,
    ESCALATED // Used for claims that exhaust their retry limit
}