package org.feuyeux.jdk25;

/// JEP 512: Compact Source Files and Instance Main methods
public class App {

    void main() {
        String jdkVersion = System.getProperty("java.version");
        System.out.println("Hello from Java " + jdkVersion + "!");
    }
}