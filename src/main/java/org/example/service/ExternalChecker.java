package org.example.service;

import java.util.Random;

public class ExternalChecker {
    private static final Random random = new Random();

    public enum CheckResult {
        APPROVED,
        TRANSIENT_FAILURE,
        PERMANENT_FAILURE
    }

    public static CheckResult performCheck() {
        try {
            Thread.sleep(50 + random.nextInt(200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CheckResult.TRANSIENT_FAILURE;
        }

        double result = random.nextDouble();
        if (result < 0.80) {
            return CheckResult.APPROVED;
        } else if (result < 0.95) {
            return CheckResult.TRANSIENT_FAILURE;
        } else {
            return CheckResult.PERMANENT_FAILURE;
        }
    }
}