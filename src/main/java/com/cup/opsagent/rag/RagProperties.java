package com.cup.opsagent.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Component
@ConfigurationProperties(prefix = "ops-agent.rag")
public class RagProperties {

    public static final int DEFAULT_MAX_RESULTS = 5;
    public static final int DEFAULT_CONTEXT_MAX_LENGTH = 2_000;
    public static final int DEFAULT_SNIPPET_MAX_LENGTH = 500;

    private boolean enabled = false;
    private String retrieverMode = RagRetrieverMode.KEYWORD.name().toLowerCase();
    private String embeddingProvider = RagEmbeddingProvider.DETERMINISTIC.name().toLowerCase();
    private String embeddingBaseUrl = "";
    private String embeddingApiKey = "";
    private String embeddingModel = "";
    private String vectorStoreProvider = "in-memory";
    private String milvusUri = "";
    private String milvusToken = "";
    private String milvusCollection = "";
    private int milvusDimension = 0;
    private String milvusMetricType = RagMilvusMetricType.COSINE.name().toLowerCase();
    private String milvusIndexType = RagMilvusIndexType.AUTOINDEX.name().toLowerCase();
    private int milvusHnswM = 16;
    private int milvusHnswEfConstruction = 200;
    private int milvusHnswEf = 64;
    private int milvusIvfFlatNlist = 128;
    private int milvusIvfFlatNprobe = 16;
    private boolean milvusValidateIndex = false;
    private boolean milvusAutoCreateCollection = false;
    private boolean milvusAutoLoadCollection = true;
    private int maxResults = DEFAULT_MAX_RESULTS;
    private int contextMaxLength = DEFAULT_CONTEXT_MAX_LENGTH;
    private int snippetMaxLength = DEFAULT_SNIPPET_MAX_LENGTH;

    public void validate() {
        retrieverMode();
        embeddingProvider();
        vectorStoreProvider();
        validatePositive(maxResults, "maxResults");
        validatePositive(contextMaxLength, "contextMaxLength");
        validatePositive(snippetMaxLength, "snippetMaxLength");
    }

    public void validateEmbeddingProviderConfig() {
        validate();
        if (embeddingProvider() != RagEmbeddingProvider.OPENAI) {
            return;
        }
        requireText(embeddingBaseUrl, "embeddingBaseUrl", "RAG embedding provider");
        requireText(embeddingApiKey, "embeddingApiKey", "RAG embedding provider");
        requireText(embeddingModel, "embeddingModel", "RAG embedding provider");
        validateEmbeddingBaseUrl();
    }

    public void validateVectorStoreProviderConfig() {
        validate();
        if (vectorStoreProvider() != RagVectorStoreProvider.MILVUS) {
            return;
        }
        requireText(milvusUri, "milvusUri", "RAG vector store provider");
        requireText(milvusCollection, "milvusCollection", "RAG vector store provider");
        validatePositiveProviderConfig(milvusDimension, "milvusDimension", "RAG vector store provider");
        milvusMetricType();
        validateMilvusIndexType();
        validateMilvusUri();
    }

    private void validatePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException("RAG config " + fieldName + " must be positive");
        }
    }

    private void validatePositiveProviderConfig(int value, String fieldName, String providerName) {
        if (value <= 0) {
            throw new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED, providerName + " config " + fieldName + " must be positive");
        }
    }

    private void requireText(String value, String fieldName, String providerName) {
        if (value == null || value.isBlank()) {
            throw new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED, "missing required " + providerName + " config: " + fieldName);
        }
    }

    private void validateEmbeddingBaseUrl() {
        URI uri;
        try {
            uri = new URI(embeddingBaseUrl.trim());
        } catch (URISyntaxException exception) {
            throw new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED, "invalid RAG embedding provider baseUrl");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(scheme.equals("http") && isLocalhost(host))) {
            throw new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED, "RAG embedding provider baseUrl must use https, except localhost test endpoints");
        }
        if (uri.getRawQuery() != null || uri.getRawUserInfo() != null) {
            throw new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED, "RAG embedding provider baseUrl must not contain userinfo or query");
        }
    }

    private void validateMilvusUri() {
        URI uri;
        try {
            uri = new URI(milvusUri.trim());
        } catch (URISyntaxException exception) {
            throw new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED, "invalid RAG vector store provider milvusUri");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED, "RAG vector store provider milvusUri must use http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED, "RAG vector store provider milvusUri must include host");
        }
        if (uri.getRawQuery() != null || uri.getRawUserInfo() != null) {
            throw new RagProviderException(RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED, "RAG vector store provider milvusUri must not contain userinfo or query");
        }
    }

    private boolean isLocalhost(String host) {
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRetrieverMode() {
        return retrieverMode;
    }

    public void setRetrieverMode(String retrieverMode) {
        this.retrieverMode = retrieverMode;
    }

    public RagRetrieverMode retrieverMode() {
        try {
            return RagRetrieverMode.from(retrieverMode);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("RAG config retrieverMode must be one of: keyword, vector", exception);
        }
    }

    public String getEmbeddingProvider() {
        return embeddingProvider;
    }

    public void setEmbeddingProvider(String embeddingProvider) {
        this.embeddingProvider = embeddingProvider;
    }

    public RagEmbeddingProvider embeddingProvider() {
        try {
            return RagEmbeddingProvider.from(embeddingProvider);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("RAG config embeddingProvider must be one of: deterministic, openai", exception);
        }
    }

    public String getEmbeddingBaseUrl() {
        return embeddingBaseUrl;
    }

    public void setEmbeddingBaseUrl(String embeddingBaseUrl) {
        this.embeddingBaseUrl = embeddingBaseUrl;
    }

    public String getEmbeddingApiKey() {
        return embeddingApiKey;
    }

    public void setEmbeddingApiKey(String embeddingApiKey) {
        this.embeddingApiKey = embeddingApiKey;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getVectorStoreProvider() {
        return vectorStoreProvider;
    }

    public void setVectorStoreProvider(String vectorStoreProvider) {
        this.vectorStoreProvider = vectorStoreProvider;
    }

    public RagVectorStoreProvider vectorStoreProvider() {
        try {
            return RagVectorStoreProvider.from(vectorStoreProvider);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("RAG config vectorStoreProvider must be one of: in-memory, milvus", exception);
        }
    }

    public String getMilvusUri() {
        return milvusUri;
    }

    public void setMilvusUri(String milvusUri) {
        this.milvusUri = milvusUri;
    }

    public String getMilvusToken() {
        return milvusToken;
    }

    public void setMilvusToken(String milvusToken) {
        this.milvusToken = milvusToken;
    }

    public String getMilvusCollection() {
        return milvusCollection;
    }

    public void setMilvusCollection(String milvusCollection) {
        this.milvusCollection = milvusCollection;
    }

    public int getMilvusDimension() {
        return milvusDimension;
    }

    public void setMilvusDimension(int milvusDimension) {
        this.milvusDimension = milvusDimension;
    }

    public String getMilvusMetricType() {
        return milvusMetricType;
    }

    public void setMilvusMetricType(String milvusMetricType) {
        this.milvusMetricType = milvusMetricType;
    }

    public RagMilvusMetricType milvusMetricType() {
        try {
            return RagMilvusMetricType.from(milvusMetricType);
        } catch (IllegalArgumentException exception) {
            throw new RagProviderException(
                    RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED,
                    "RAG vector store provider config milvusMetricType must be one of: cosine, l2, ip",
                    exception
            );
        }
    }

    public String getMilvusIndexType() {
        return milvusIndexType;
    }

    public void setMilvusIndexType(String milvusIndexType) {
        this.milvusIndexType = milvusIndexType;
    }

    public RagMilvusIndexType milvusIndexType() {
        try {
            return RagMilvusIndexType.from(milvusIndexType);
        } catch (IllegalArgumentException exception) {
            throw new RagProviderException(
                    RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED,
                    "RAG vector store provider config milvusIndexType must be one of: autoindex, hnsw, ivf-flat",
                    exception
            );
        }
    }

    public void validateMilvusIndexType() {
        RagMilvusIndexType indexType = milvusIndexType();
        if (indexType == RagMilvusIndexType.HNSW) {
            validatePositiveProviderConfig(milvusHnswM, "milvusHnswM", "RAG vector store provider");
            validatePositiveProviderConfig(milvusHnswEfConstruction, "milvusHnswEfConstruction", "RAG vector store provider");
            validatePositiveProviderConfig(milvusHnswEf, "milvusHnswEf", "RAG vector store provider");
            return;
        }
        if (indexType == RagMilvusIndexType.IVF_FLAT) {
            validatePositiveProviderConfig(milvusIvfFlatNlist, "milvusIvfFlatNlist", "RAG vector store provider");
            validatePositiveProviderConfig(milvusIvfFlatNprobe, "milvusIvfFlatNprobe", "RAG vector store provider");
            return;
        }
        if (indexType != RagMilvusIndexType.AUTOINDEX) {
            throw new RagProviderException(
                    RagProviderErrorCode.RAG_PROVIDER_MISCONFIGURED,
                    "RAG vector store provider config milvusIndexType " + getMilvusIndexType()
                            + " is not supported yet; use autoindex, hnsw, or ivf-flat"
            );
        }
    }

    public int getMilvusHnswM() {
        return milvusHnswM;
    }

    public void setMilvusHnswM(int milvusHnswM) {
        this.milvusHnswM = milvusHnswM;
    }

    public int getMilvusHnswEfConstruction() {
        return milvusHnswEfConstruction;
    }

    public void setMilvusHnswEfConstruction(int milvusHnswEfConstruction) {
        this.milvusHnswEfConstruction = milvusHnswEfConstruction;
    }

    public int getMilvusHnswEf() {
        return milvusHnswEf;
    }

    public void setMilvusHnswEf(int milvusHnswEf) {
        this.milvusHnswEf = milvusHnswEf;
    }

    public int getMilvusIvfFlatNlist() {
        return milvusIvfFlatNlist;
    }

    public void setMilvusIvfFlatNlist(int milvusIvfFlatNlist) {
        this.milvusIvfFlatNlist = milvusIvfFlatNlist;
    }

    public int getMilvusIvfFlatNprobe() {
        return milvusIvfFlatNprobe;
    }

    public void setMilvusIvfFlatNprobe(int milvusIvfFlatNprobe) {
        this.milvusIvfFlatNprobe = milvusIvfFlatNprobe;
    }

    public boolean isMilvusValidateIndex() {
        return milvusValidateIndex;
    }

    public void setMilvusValidateIndex(boolean milvusValidateIndex) {
        this.milvusValidateIndex = milvusValidateIndex;
    }

    public boolean isMilvusAutoCreateCollection() {
        return milvusAutoCreateCollection;
    }

    public void setMilvusAutoCreateCollection(boolean milvusAutoCreateCollection) {
        this.milvusAutoCreateCollection = milvusAutoCreateCollection;
    }

    public boolean isMilvusAutoLoadCollection() {
        return milvusAutoLoadCollection;
    }

    public void setMilvusAutoLoadCollection(boolean milvusAutoLoadCollection) {
        this.milvusAutoLoadCollection = milvusAutoLoadCollection;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public int getContextMaxLength() {
        return contextMaxLength;
    }

    public void setContextMaxLength(int contextMaxLength) {
        this.contextMaxLength = contextMaxLength;
    }

    public int getSnippetMaxLength() {
        return snippetMaxLength;
    }

    public void setSnippetMaxLength(int snippetMaxLength) {
        this.snippetMaxLength = snippetMaxLength;
    }
}
