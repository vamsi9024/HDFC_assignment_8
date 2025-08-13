package org.example.state;

import java.util.concurrent.atomic.AtomicBoolean;

public class ThrottlingState {
    private final AtomicBoolean isThrottling = new AtomicBoolean(false);

    public boolean isThrottling() {
        return isThrottling.get();
    }

    public void set(boolean state) {
        isThrottling.set(state);
    }
}