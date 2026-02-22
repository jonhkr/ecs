create table component (
    id binary(16) not null,
    system_id tinyint unsigned not null,
    entity_id binary(16) not null,
    type smallint unsigned not null,
    data json not null,
    version smallint unsigned not null,
    sequence bigint default null,
    created_at timestamp not null,
    type_and_data_hash binary(32) not null,
    unique_flag tinyint null,
    primary key (system_id, id),
    unique key unique_entity_id_and_type (system_id, entity_id, type, version),
    unique key unique_type_and_data (system_id, type_and_data_hash, unique_flag),
    index (system_id, created_at),
    index (system_id, sequence, type)
) partition by list ( system_id ) (
    partition system1 values in (1),
    partition system2 values in (2)
);

explain
WITH TargetEntity AS (
    SELECT entity_id
    FROM component
    WHERE system_id = ?
      AND type_and_data_hash = ?
      AND unique_flag = 1
    LIMIT 1
),
    RankedComponents AS (
    SELECT
        id,
        entity_id,
        type,
        data,
        created_at,
        unique_flag,
        ROW_NUMBER() OVER(PARTITION BY type ORDER BY version DESC) as rn
    FROM component
    WHERE system_id = ?
      AND entity_id = (SELECT entity_id from TargetEntity)
)
SELECT
    id, entity_id, type, data, created_at, unique_flag
FROM RankedComponents
WHERE rn = 1;

alter table component add partition (partition system3 values in (3));

drop table component;

select system_id, hex(type_and_data_hash) from component;
# truncate component;

explain select id, entity_id, type, data, created_at, unique_flag from component where system_id = 1 AND entity_id = unhex('019c85f0d3897c468d10a9f0e3ddb237');

explain WITH RankedComponents AS (
    SELECT
        id,
        entity_id,
        type,
        data,
        created_at,
        unique_flag,
        ROW_NUMBER() OVER(PARTITION BY type ORDER BY version DESC) as rn
    FROM component
    WHERE system_id = 1 AND entity_id = unhex('019c868bfd3075ad972c9f72e19c6014')
)
SELECT
    id, entity_id, type, data, created_at, unique_flag
FROM RankedComponents
WHERE rn = 1;

SELECT
    PARTITION_NAME AS `Partition`,
    ROUND((DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024, 2) AS `Size (MB)`,
    TABLE_ROWS AS `Estimated Rows`,
    DATA_LENGTH AS `Data Length (Bytes)`,
    INDEX_LENGTH AS `Index Length (Bytes)`
FROM
    information_schema.PARTITIONS
WHERE
    TABLE_SCHEMA = 'ecs' AND
    TABLE_NAME = 'component'
ORDER BY
    PARTITION_ORDINAL_POSITION;

SELECT
    table_name AS 'Table',
    ROUND(index_length / 1024 / 1024, 2) AS 'Index Size (MB)'
FROM
    information_schema.TABLES
WHERE
    table_schema = 'ecs'
ORDER BY
    index_length DESC;

SELECT
    table_name AS 'Table',
    index_name AS 'Index',
    ROUND(stat_value * @@innodb_page_size / 1024 / 1024, 2) AS 'Size_MB'
FROM
    mysql.innodb_index_stats
WHERE
    stat_name = 'size'
  AND index_name != 'PRIMARY'
ORDER BY
    Size_MB DESC;