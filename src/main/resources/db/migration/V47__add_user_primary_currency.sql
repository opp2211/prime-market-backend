alter table users
    add column primary_currency_code varchar(5) not null default 'RUB';

alter table users
    add constraint fk_users_primary_currency
        foreign key (primary_currency_code) references currencies (code);
