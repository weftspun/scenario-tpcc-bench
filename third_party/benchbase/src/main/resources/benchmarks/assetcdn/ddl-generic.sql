DROP TABLE IF EXISTS EDGE_CACHE_ENTRY;
DROP TABLE IF EXISTS USER_ENTITLEMENT;
DROP TABLE IF EXISTS ASSET_VERSION;
DROP TABLE IF EXISTS ASSET;

-- A distinct 3D asset in the catalog. Mirrors TPC-C's ITEM/WAREHOUSE-owned
-- root: the top-level unit assets/CDN's scaling problem partitions on.
CREATE TABLE ASSET (
    asset_id            BIGINT       NOT NULL,
    owner_id            VARCHAR(20)  NOT NULL,
    current_version     INT          NOT NULL,
    content_hash        VARCHAR(64)  NOT NULL,
    size_bytes          BIGINT       NOT NULL,
    created_tick        BIGINT       NOT NULL,
    updated_tick        BIGINT       NOT NULL,
    CONSTRAINT pk_asset PRIMARY KEY (asset_id)
);

-- Every published revision of an asset. Mirrors TPC-C's ORDERS: each
-- AssetUpload transaction appends one row here, never mutates an existing
-- one - versions are immutable once published.
CREATE TABLE ASSET_VERSION (
    asset_id            BIGINT       NOT NULL,
    version             INT          NOT NULL,
    storage_uri         VARCHAR(200) NOT NULL,
    size_bytes          BIGINT       NOT NULL,
    created_tick        BIGINT       NOT NULL,
    CONSTRAINT pk_asset_version PRIMARY KEY (asset_id, version),
    FOREIGN KEY (asset_id) REFERENCES ASSET (asset_id)
);

-- Which edge nodes currently cache which asset version, and how hot that
-- cache entry is. Mirrors TPC-C's STOCK: a per-partition materialized
-- view of availability that AssetFetch reads/writes on every hit or miss.
CREATE TABLE EDGE_CACHE_ENTRY (
    edge_node_id        INT          NOT NULL,
    asset_id            BIGINT       NOT NULL,
    version             INT          NOT NULL,
    last_access_tick    BIGINT       NOT NULL,
    hit_count           BIGINT       NOT NULL,
    CONSTRAINT pk_edge_cache_entry PRIMARY KEY (edge_node_id, asset_id),
    FOREIGN KEY (asset_id) REFERENCES ASSET (asset_id)
);
CREATE INDEX IDX_EDGE_CACHE_ASSET ON EDGE_CACHE_ENTRY (asset_id);
CREATE INDEX IDX_EDGE_CACHE_STALE ON EDGE_CACHE_ENTRY (edge_node_id, last_access_tick);

-- Which users are entitled to fetch a given asset. Mirrors TPC-C's
-- CUSTOMER-scoped access pattern: a read-mostly point-lookup table
-- EntitlementCheck queries on every access-gated fetch.
CREATE TABLE USER_ENTITLEMENT (
    user_id             BIGINT       NOT NULL,
    asset_id            BIGINT       NOT NULL,
    granted_tick        BIGINT       NOT NULL,
    CONSTRAINT pk_user_entitlement PRIMARY KEY (user_id, asset_id),
    FOREIGN KEY (asset_id) REFERENCES ASSET (asset_id)
);
