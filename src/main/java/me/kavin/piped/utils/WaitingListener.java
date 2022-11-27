package me.kavin.piped.utils;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class WaitingListener {

    private final long maxWaitTime;

    public void waitFor() throws InterruptedException {
        synchronized (this) {
            this.wait(maxWaitTime);
        }
    }

    public void done() {
        synchronized (this) {
            this.notifyAll();
        }
    }
}
