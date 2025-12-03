create extension citext;
create extension hstore;
create extension vector;

create type complex as (
    r float8,
    i float8
);

create type myenum as enum ('a', 'b', 'c');
create domain mydomain as text;

-- types which are not working yet!
--     booles        bool[]     doobie has confused `bit` and `bool`
--     mydomaines   mydomain[] we get PGObjects for these. not sure how that works with types other than strings

-- pg types with no test so far
--     aclitem       aclitem,
--     anyarray      anyarray,
--     cid           cid,
--     oid           oid,
--     oidvector     oidvector,
--     pg_lsn        pg_lsn,
--     pg_node_tree  pg_node_tree,
--     record        record,
--     regclass      regclass,
--     regconfig     regconfig,
--     regdictionary regdictionary,
--     regnamespace  regnamespace,
--     regoper       regoper,
--     regoperator   regoperator,
--     regproc       regproc,
--     regprocedure  regprocedure,
--     regrole       regrole,
--     regtype       regtype,
--     xid           xid,

-- drop table pgtest, pgtestnull;

create table pgtest
(
    bool         bool                       not null,
    box          box                        not null,
    bpchar       bpchar(3)                  not null,
    bytea        bytea                      not null,
    char         char                       not null,
    circle       circle                     not null,
    date         date                       not null,
    float4       float4                     not null,
    float8       float8                     not null,
    hstore       hstore                     not null,
    inet         inet                       not null,
    int2         int2                       not null,
    int2vector   int2vector                 not null,
    int4         int4                       not null,
    int8         int8                       not null,
    interval     interval                   not null,
    json         json                       not null,
    jsonb        jsonb                      not null,
    line         line                       not null,
    lseg         lseg                       not null,
    money        money                      not null,
    mydomain     mydomain                   not null,
    myenum       myenum                     not null,
    name         name                       not null,
    numeric      numeric                    not null,
    path         path                       not null,
    point        point                      not null,
    polygon      polygon                    not null,
    text         text                       not null,
    time         time                       not null,
    timestamp    timestamp                  not null,
    timestampz   timestamp with time zone   not null,
    timez        time with time zone        not null,
    uuid         uuid                       not null,
    varchar      varchar                    not null,
    vector       vector                     not null,
    xml          xml                        not null,
    boxes        box[]                      not null,
    bpchares     bpchar(3)[]                not null,
    chares       char[]                     not null,
    circlees     circle[]                   not null,
    datees       date[]                     not null,
    float4es     float4[]                   not null,
    float8es     float8[]                   not null,
    inetes       inet[]                     not null,
    int2es       int2[]                     not null,
    int2vectores int2vector[]               not null,
    int4es       int4[]                     not null,
    int8es       int8[]                     not null,
    intervales   interval[]                 not null,
    jsones       json[]                     not null,
    jsonbes      jsonb[]                    not null,
    linees       line[]                     not null,
    lseges       lseg[]                     not null,
    moneyes      money[]                    not null,
    mydomaines   mydomain[]                 not null,
    myenumes     myenum[]                   not null,
    namees       name[]                     not null,
    numerices    numeric[]                  not null,
    pathes       path[]                     not null,
    pointes      point[]                    not null,
    polygones    polygon[]                  not null,
    textes       text[]                     not null,
    timees       time[]                     not null,
    timestampes  timestamp[]                not null,
    timestampzes timestamp with time zone[] not null,
    timezes      time with time zone[]      not null,
    uuides       uuid[]                     not null,
    varchares    varchar[]                  not null,
    xmles        xml[]                      not null
);



create table pgtestnull
(
    bool         bool,
    box          box,
    bpchar       bpchar(3),
    bytea        bytea,
    char         char,
    circle       circle,
    date         date,
    float4       float4,
    float8       float8,
    hstore       hstore,
    inet         inet,
    int2         int2,
    int2vector   int2vector,
    int4         int4,
    int8         int8,
    interval     interval,
    json         json,
    jsonb        jsonb,
    line         line,
    lseg         lseg,
    money        money,
    mydomain     mydomain,
    myenum       myenum,
    name         name,
    numeric      numeric,
    path         path,
    point        point,
    polygon      polygon,
    text         text,
    time         time,
    timestamp    timestamp,
    timestampz   timestamp with time zone,
    timez        time with time zone,
    uuid         uuid,
    varchar      varchar,
    vector       vector,
    xml          xml,
--     booles       bool[],
    boxes        box[],
    bpchares     bpchar(3)[],
    chares       char[],
    circlees     circle[],
    datees       date[],
    float4es     float4[],
    float8es     float8[],
    inetes       inet[],
    int2es       int2[],
    int2vectores int2vector[],
    int4es       int4[],
    int8es       int8[],
    intervales   interval[],
    jsones       json[],
    jsonbes      jsonb[],
    linees       line[],
    lseges       lseg[],
    moneyes      money[],
    mydomaines   mydomain[],
    myenumes     myenum[],
    namees       name[],
    numerices    numeric[],
    pathes       path[],
    pointes      point[],
    polygones    polygon[],
    textes       text[],
    timees       time[],
    timestampes  timestamp[],
    timestampzes timestamp with time zone[],
    timezes      time with time zone[],
    uuides       uuid[],
    varchares    varchar[],
    xmles        xml[]
);

CREATE TABLE users
(
    user_id     UUID        NOT NULL,
    name        TEXT        NOT NULL,
    last_name   TEXT        NULL,
    email       CITEXT      NOT NULL,
    password    TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    verified_on TIMESTAMPTZ NULL,
    CONSTRAINT users_user_id_pk PRIMARY KEY (user_id),
    CONSTRAINT users_email_unique UNIQUE (email)
);

CREATE TABLE "identity-test"
(
    always_generated  Integer                NOT NULL GENERATED ALWAYS AS IDENTITY (INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1),
    default_generated Integer                NOT NULL GENERATED BY DEFAULT AS IDENTITY (INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1),
    name              Character varying(250) NOT NULL primary key
);

create domain short_text as text
    constraint short_text_check check (length(VALUE) <= 55);

create table flaff
(
    code            short_text  not null,
    another_code    varchar(20) not null,
    some_number     integer     not null,
    specifier       short_text  not null,
    parentSpecifier short_text,
    constraint flaff_pk primary key (code, another_code, some_number, specifier),
    constraint flaff_parent_fk foreign key (code, another_code, some_number, parentSpecifier) references flaff
);

create table title
(
    code text primary key
);
insert into title (code)
values ('mr'),
       ('ms'),
       ('dr'),
       ('phd');

create table title_domain
(
    code short_text primary key
);
insert into title_domain (code)
values ('mr'),
       ('ms'),
       ('dr'),
       ('phd');

create table titledperson
(
    title_short short_text not null references title_domain,
    title       text       not null references title,
    name        text       not null
);

create table "table-with-generated-columns"
(
    name               text primary key,
    "name-type-always" text NOT NULL GENERATED ALWAYS AS
        (CASE
             WHEN name IS NOT NULL THEN 'no-name'
             WHEN name = 'a' THEN 'a-name'
             ELSE 'some-name'
            END) STORED
);


CREATE TABLE issue142
(
    tabellkode TEXT NOT NULL ,
    CONSTRAINT tabell_pk PRIMARY KEY (tabellkode)
);

CREATE TABLE issue142_2
(
    tabellkode TEXT NOT NULL,
    CONSTRAINT tabell2_pk PRIMARY KEY (tabellkode),
    CONSTRAINT tabell2_tabell_fk FOREIGN KEY (tabellkode) REFERENCES issue142 (tabellkode)
);

INSERT INTO issue142 (tabellkode)
VALUES  ('aa'),
        ('bb')
;

create table only_pk_columns(
    key_column_1 text not null,
    key_column_2 int not null,
    constraint only_pk_columns_pk primary key (key_column_1, key_column_2)
);
