package com.cup.opsagent.rag;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class DeterministicEmbeddingClient implements EmbeddingClient {

    public static final int DEFAULT_DIMENSIONS = 32;

    private final int dimensions;

    public DeterministicEmbeddingClient() {
        this(DEFAULT_DIMENSIONS);
    }

    public DeterministicEmbeddingClient(int dimensions) {
        this.dimensions = dimensions <= 0 ? DEFAULT_DIMENSIONS : dimensions;
    }

    @Override
    public EmbeddingVector embed(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return new EmbeddingVector(new float[dimensions]);
        }
        float[] values = new float[dimensions];
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
        for (int index = 0; index < bytes.length; index++) {
            int bucket = Math.floorMod(index + Byte.toUnsignedInt(bytes[index]), dimensions);
            values[bucket] += 1.0f;
        }
        normalize(values);
        return new EmbeddingVector(values);
    }

    public int dimensions() {
        return dimensions;
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private void normalize(float[] values) {
        double norm = 0;
        for (float value : values) {
            norm += value * value;
        }
        if (norm == 0) {
            return;
        }
        double scale = Math.sqrt(norm);
        for (int index = 0; index < values.length; index++) {
            values[index] = (float) (values[index] / scale);
        }
    }
}
