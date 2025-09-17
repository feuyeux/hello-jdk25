package org.feuyeux.jdk25.language;

import java.security.SecureRandom;

/// JEP 507 - Primitive Types in Patterns, instanceof, and switch (Third Preview)
public class PrimitiveTypes {
    record ExamResults(int score) {
    }

    // primitives are now supported pattern matching,
    // boolean, float, double and long are now also supported in switches
    public String determineGrade(ExamResults examResults) {
        return switch (examResults.score) {
            case int i when i >= 90 -> "A";
            case int i when i >= 80 -> "B";
            case int i when i >= 70 -> "C";
            case int i when i >= 60 -> "D";
            case int i when i >= 50 -> "E";
            case int _ -> "Failed with a score of " + examResults.score;
        };
    }

    // We can now pattern match using primitive types, which makes switching to type patterns easier as well
    public int patterns(Object number) {
        if (number instanceof int num) {
            return num;
        }
        return -1;
    }

    void main() {
        PrimitiveTypes pt = new PrimitiveTypes();
        int number = new SecureRandom().nextInt();
        IO.println(pt.patterns(number));
        IO.println(pt.determineGrade(new ExamResults(new SecureRandom().nextInt(50, 100))));
    }
}
