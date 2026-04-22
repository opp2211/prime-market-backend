create table offer_delivery_methods
(
    offer_id            bigint      not null references offers (id) on delete cascade,
    delivery_method_id  bigint      not null references delivery_methods (id),
    created_at          timestamptz not null default now(),

    primary key (offer_id, delivery_method_id)
);

create index if not exists ix_offer_delivery_methods_delivery_method_id
    on offer_delivery_methods (delivery_method_id);