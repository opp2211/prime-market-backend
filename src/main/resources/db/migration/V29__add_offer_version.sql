alter table offers
    add column version bigint not null default 1;

alter table offers
    add constraint offers_version_positive
        check (version > 0);
