create table component (
    system_id tinyint unsigned not null,
    id binary(16) not null,
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
) partition by key ( system_id );


drop table component;

select * from component
truncate component;

explain select id, entity_id, type, data, created_at, unique_flag from component where system_id = 1 AND entity_id = unhex('019c85f0d3897c468d10a9f0e3ddb237')

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
    WHERE system_id = 1 AND entity_id = unhex('019c85f0d3897c468d10a9f0e3ddb237')
)
SELECT
    id, entity_id, type, data, created_at, unique_flag
FROM RankedComponents
WHERE rn = 1;