package org.feuyeux.jdk25.scopedvalues;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.System.out;

/// JEP 506: Scoped values
public class Scoper {
    public final static ScopedValue<String> USER = ScopedValue.newInstance();

    void main() {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> ScopedValue.where(USER, "Alice").run(() -> {
                out.println("Thread: " + Thread.currentThread());
                out.println("User: " + USER.get());
            }));

            executor.submit(() -> ScopedValue.where(USER, "Bob").run(() -> {
                out.println("Thread: " + Thread.currentThread());
                out.println("User: " + USER.get());
            }));

            // Optional delay to ensure output appears before main exits
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
