DROP TABLE IF EXISTS BEAUTIFIED_STROKE;
DROP TABLE IF EXISTS STROKE_POINT;
DROP TABLE IF EXISTS STROKE;
DROP TABLE IF EXISTS CANVAS_SUBSCRIBER;
DROP TABLE IF EXISTS CANVAS;

-- A shared in-world sketch/annotation surface. Mirrors TPC-C's WAREHOUSE:
-- the top-level unit of partitioning everything else hangs off of.
CREATE TABLE CANVAS (
    c_id                BIGINT       NOT NULL,
    c_owner_id          VARCHAR(20)  NOT NULL,
    c_created_tick      BIGINT       NOT NULL,
    c_active_sessions   INT          NOT NULL,
    CONSTRAINT pk_canvas PRIMARY KEY (c_id)
);

-- A completed freehand stroke. Mirrors TPC-C's ORDERS: the durably-logged
-- record a AppendStrokePoints transaction creates.
CREATE TABLE STROKE (
    s_id                BIGINT       NOT NULL,
    s_canvas_id         BIGINT       NOT NULL,
    s_author_user_id    BIGINT       NOT NULL,
    s_point_count       INT          NOT NULL,
    s_created_tick      BIGINT       NOT NULL,
    s_deleted           BOOLEAN      NOT NULL,
    CONSTRAINT pk_stroke PRIMARY KEY (s_id),
    FOREIGN KEY (s_canvas_id) REFERENCES CANVAS (c_id)
);
CREATE INDEX IDX_STROKE_CANVAS ON STROKE (s_canvas_id);

-- Raw input points making up a stroke. Mirrors TPC-C's ORDER_LINE: a
-- variable-count set of child rows per parent stroke, sized by how many
-- points the input batch contained - the same "N child rows per parent
-- transaction" shape that makes NewOrder representative of real
-- insert-fanout workloads.
CREATE TABLE STROKE_POINT (
    sp_stroke_id        BIGINT           NOT NULL,
    sp_seq              INT              NOT NULL,
    sp_x                DOUBLE PRECISION NOT NULL,
    sp_y                DOUBLE PRECISION NOT NULL,
    sp_pressure         DOUBLE PRECISION NOT NULL,
    sp_tick             BIGINT           NOT NULL,
    CONSTRAINT pk_stroke_point PRIMARY KEY (sp_stroke_id, sp_seq),
    FOREIGN KEY (sp_stroke_id) REFERENCES STROKE (s_id)
);

-- Beautification result for a completed stroke. One row per stroke once
-- BeautifyStroke has run on it - absence of a row means "not yet
-- beautified", queried by CanvasSnapshot.
CREATE TABLE BEAUTIFIED_STROKE (
    bs_stroke_id            BIGINT NOT NULL,
    bs_algorithm_version    INT    NOT NULL,
    bs_point_count          INT    NOT NULL,
    bs_compute_ms           BIGINT NOT NULL,
    CONSTRAINT pk_beautified_stroke PRIMARY KEY (bs_stroke_id),
    FOREIGN KEY (bs_stroke_id) REFERENCES STROKE (s_id)
);

-- Which users are subscribed to a canvas and how far behind their last
-- sync is. Mirrors TPC-C's CUSTOMER-scoped read pattern: a point-lookup
-- table CanvasSyncQuery reads on every reconnect/catch-up check.
CREATE TABLE CANVAS_SUBSCRIBER (
    cs_canvas_id         BIGINT NOT NULL,
    cs_user_id           BIGINT NOT NULL,
    cs_last_synced_tick  BIGINT NOT NULL,
    CONSTRAINT pk_canvas_subscriber PRIMARY KEY (cs_canvas_id, cs_user_id),
    FOREIGN KEY (cs_canvas_id) REFERENCES CANVAS (c_id)
);
