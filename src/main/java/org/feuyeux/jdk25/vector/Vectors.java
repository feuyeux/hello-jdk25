package org.feuyeux.jdk25.vector;
import jdk.incubator.vector.*;

/// JEP 508 Vector API - Tenth incubation
public class Vectors {
    static void main() {
        float[] left = {1f, 2f, 3f, 4f};
        float[] right = {5f, 6f, 7f, 8f};

        FloatVector a = FloatVector.fromArray(FloatVector.SPECIES_128, left, 0);
        FloatVector b = FloatVector.fromArray(FloatVector.SPECIES_128, right, 0);
        FloatVector c = a.add(b);

        float[] result = new float[FloatVector.SPECIES_128.length()];
        c.intoArray(result, 0);

        System.out.println("Vector result: " + java.util.Arrays.toString(result));
    }
}
