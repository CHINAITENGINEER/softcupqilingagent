package com.cup.opsagent.rag;

public interface EmbeddingClient {

    EmbeddingVector embed(String text);
}
