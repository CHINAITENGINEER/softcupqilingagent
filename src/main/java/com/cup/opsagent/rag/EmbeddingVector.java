package com.cup.opsagent.rag;

import java.util.Arrays;

public record EmbeddingVector(float[] values) {

    public EmbeddingVector {
        values = values == null ? new float[0] : Arrays.copyOf(values, values.length);
    }

    @Override
    public float[] values() {
        return Arrays.copyOf(values, values.length);
    }

    public int dimensions() {
        return values.length;
    }

    public boolean isEmpty() {
        return values.length == 0;
    }
}
