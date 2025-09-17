package org.feuyeux.jdk25.structured;

import java.util.concurrent.StructuredTaskScope;

public class Structurer {
    static String fetchUser() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Alice";
    }

    static String fetchOrder() {
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Order#42";
    }

    void main() throws Exception {
        try (var scope = StructuredTaskScope.<String>open()) {
            var userTask = scope.fork(() -> fetchUser());
            var orderTask = scope.fork(() -> fetchOrder());
            scope.join();
            System.out.println(userTask.get() + " - " + orderTask.get());
        }
    }
}
