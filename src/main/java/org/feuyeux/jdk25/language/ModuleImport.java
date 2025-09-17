package org.feuyeux.jdk25.language;

import module java.base;

/// JEP 511: Module Import Declarations
public class ModuleImport {
    void main() {
        List<String> elements = List.of("One", "two", "THREE");
        elements.stream().map(String::toLowerCase).forEach(System.out::println);
    }
}
