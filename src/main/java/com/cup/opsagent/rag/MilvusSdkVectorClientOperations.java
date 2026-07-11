package com.cup.opsagent.rag;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
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

class MilvusSdkVectorClientOperations implements MilvusVectorClientOperations {

    private final ConnectConfig connectConfig;
    private MilvusClientV2 client;

    MilvusSdkVectorClientOperations(ConnectConfig connectConfig) {
        this.connectConfig = connectConfig;
    }

    MilvusSdkVectorClientOperations(MilvusClientV2 client) {
        this.connectConfig = null;
        this.client = client;
    }

    @Override
    public boolean hasCollection(HasCollectionReq request) {
        return client().hasCollection(request);
    }

    @Override
    public void createCollection(CreateCollectionReq request) {
        client().createCollection(request);
    }

    @Override
    public void createIndex(CreateIndexReq request) {
        client().createIndex(request);
    }

    @Override
    public DescribeIndexResp describeIndex(DescribeIndexReq request) {
        return client().describeIndex(request);
    }

    @Override
    public DescribeCollectionResp describeCollection(DescribeCollectionReq request) {
        return client().describeCollection(request);
    }

    @Override
    public void loadCollection(LoadCollectionReq request) {
        client().loadCollection(request);
    }

    @Override
    public void upsert(UpsertReq request) {
        client().upsert(request);
    }

    @Override
    public SearchResp search(SearchReq request) {
        return client().search(request);
    }

    private synchronized MilvusClientV2 client() {
        if (client == null) {
            client = new MilvusClientV2(connectConfig);
        }
        return client;
    }
}
