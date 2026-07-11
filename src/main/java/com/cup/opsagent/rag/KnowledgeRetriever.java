package com.cup.opsagent.rag;

import java.util.List;

public interface KnowledgeRetriever {

    List<RetrievedKnowledge> retrieve(KnowledgeQuery query);
}
