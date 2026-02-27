alter table users
    add column role varchar(16) not null default 'USER';

alter table users
    add constraint users_role_check check (role in ('USER', 'OPERATOR'));
