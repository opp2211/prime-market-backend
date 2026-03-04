create table roles
(
    id   bigserial primary key,
    code varchar(50) not null unique -- ADMIN, SUPPORT_FINANCE, ...
);

create table user_roles
(
    user_id bigint not null references users (id) on delete cascade,
    role_id bigint not null references roles (id) on delete cascade,
    primary key (user_id, role_id)
);

create table permissions
(
    id   bigserial primary key,
    code varchar(100) not null unique -- TICKET_READ, WITHDRAW_APPROVE, ...
);

create table role_permissions
(
    role_id       bigint not null references roles (id) on delete cascade,
    permission_id bigint not null references permissions (id) on delete cascade,
    primary key (role_id, permission_id)
);
