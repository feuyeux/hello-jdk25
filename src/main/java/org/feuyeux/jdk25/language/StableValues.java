package org.feuyeux.jdk25.language;

import java.util.function.Supplier;

/// JEP-502: Stable Values (Preview)
public class StableValues {
    void main() {
        // Create a new unset StableValue
        var greeting = StableValue.<String>of();
        String message = greeting.orElseSet(() -> "hi");
        System.out.println(message);
        final Supplier<String> hello = StableValue.supplier(() -> "hello");
        System.out.println(hello.get());
    }
}
