create table component (
    id binary(16) not null,
    entity_id binary(16) not null,
    type smallint unsigned not null,
    data json not null,
    sequence bigint default null,
    created_at timestamp not null,
    type_and_data_hash binary(32) not null,
    unique_flag tinyint null,
    primary key (id),
    unique key unique_entity_id_and_type (entity_id, type),
    unique key unique_type_and_data (type_and_data_hash, unique_flag),
    index (created_at),
    index (sequence, type)
);

create table type_mapping (
    id smallint unsigned not null,
    name varchar(255) not null,
    created_at timestamp not null,
    primary key (id),
    unique key (name)
);