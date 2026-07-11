package com.cup.opsagent.rag;

import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.SearchResp;

interface MilvusVectorClientOperations {

    boolean hasCollection(HasCollectionReq request);

    void createCollection(CreateCollectionReq request);

    void createIndex(CreateIndexReq request);

    DescribeIndexResp describeIndex(DescribeIndexReq request);

    DescribeCollectionResp describeCollection(DescribeCollectionReq request);

    void loadCollection(LoadCollectionReq request);

    void upsert(UpsertReq request);

    SearchResp search(SearchReq request);
}
