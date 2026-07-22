DROP TABLE IF EXISTS FANOUT_TARGET;
DROP TABLE IF EXISTS EFFECT_ENTITY;
DROP TABLE IF EXISTS ENTITY;
DROP TABLE IF EXISTS ZONE;

-- A spatial partition of the persistent world. Mirrors TPC-C's WAREHOUSE:
-- the top-level unit of horizontal partitioning that everything else hangs
-- off of. population/cost track weft-warp-loop's real AV1-style
-- population^2 split/merge cost model (see ZoneSplitMerge).
CREATE TABLE ZONE (
    z_id                BIGINT       NOT NULL,
    z_region            VARCHAR(20)  NOT NULL,
    z_population        INT          NOT NULL,
    z_authority_cap     INT          NOT NULL,
    z_interest_cap      INT          NOT NULL,
    z_cost              DOUBLE PRECISION       NOT NULL,
    CONSTRAINT pk_zone PRIMARY KEY (z_id)
);

-- A player or NPC. Mirrors TPC-C's CUSTOMER: the entity that actually
-- performs actions, scoped to whichever ZONE currently holds authority
-- over it. Position/velocity here represent the last *durably persisted*
-- checkpoint, not the live 60Hz stream - real-time sync stays in-memory
-- pub/sub over QUIC in the actual system, out of scope for a
-- durable-storage benchmark like this one (see README).
CREATE TABLE ENTITY (
    e_id                BIGINT       NOT NULL,
    e_zone_id           BIGINT       NOT NULL,
    e_x                 DOUBLE PRECISION       NOT NULL,
    e_y                 DOUBLE PRECISION       NOT NULL,
    e_vx                DOUBLE PRECISION       NOT NULL,
    e_vy                DOUBLE PRECISION       NOT NULL,
    e_rtt_ms            INT          NOT NULL,
    e_last_tick         BIGINT       NOT NULL,
    CONSTRAINT pk_entity PRIMARY KEY (e_id),
    FOREIGN KEY (e_zone_id) REFERENCES ZONE (z_id)
);
CREATE INDEX IDX_ENTITY_ZONE ON ENTITY (e_zone_id);
CREATE INDEX IDX_ENTITY_ZONE_POS ON ENTITY (e_zone_id, e_x, e_y);

-- A spell/ability cast resolved into a durable game-log entry. Mirrors
-- TPC-C's ORDERS: the record of an action a CastSpell transaction creates.
CREATE TABLE EFFECT_ENTITY (
    ef_id               BIGINT       NOT NULL,
    ef_caster_id        BIGINT       NOT NULL,
    ef_zone_id          BIGINT       NOT NULL,
    ef_kind             VARCHAR(20)  NOT NULL,
    ef_magnitude        DOUBLE PRECISION       NOT NULL,
    ef_duration_ticks    INT          NOT NULL,
    ef_created_tick      BIGINT       NOT NULL,
    CONSTRAINT pk_effect_entity PRIMARY KEY (ef_id),
    FOREIGN KEY (ef_caster_id) REFERENCES ENTITY (e_id),
    FOREIGN KEY (ef_zone_id) REFERENCES ZONE (z_id)
);
CREATE INDEX IDX_EFFECT_ZONE ON EFFECT_ENTITY (ef_zone_id);

-- Which nearby entities a given effect actually fanned out to. Mirrors
-- TPC-C's ORDER_LINE: a variable-count set of child rows per parent
-- action, sized by how many entities were within ghost-range at cast
-- time - the same "N child rows per parent transaction" shape that makes
-- TPC-C's NewOrder representative of real insert-fanout workloads.
CREATE TABLE FANOUT_TARGET (
    ft_effect_id        BIGINT       NOT NULL,
    ft_target_entity_id BIGINT       NOT NULL,
    ft_distance         DOUBLE PRECISION       NOT NULL,
    CONSTRAINT pk_fanout_target PRIMARY KEY (ft_effect_id, ft_target_entity_id),
    FOREIGN KEY (ft_effect_id) REFERENCES EFFECT_ENTITY (ef_id),
    FOREIGN KEY (ft_target_entity_id) REFERENCES ENTITY (e_id)
);
