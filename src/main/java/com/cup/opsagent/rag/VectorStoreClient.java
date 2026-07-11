package com.cup.opsagent.rag;

import java.util.List;

public interface VectorStoreClient {

    void upsert(List<VectorStoreRecord> records);

    List<VectorSearchResult> search(VectorStoreQuery query);
}
