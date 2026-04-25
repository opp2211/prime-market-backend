alter table currencies
    add column sort_order int not null default 0;

insert into currencies (code, title, sort_order, is_active)
values ('RUB', 'Russian Ruble', 10, true),
       ('USD', 'US Dollar', 20, true),
       ('EUR', 'Euro', 30, true),
       ('CNY', 'Chinese Yuan', 40, true),
       ('KZT', 'Kazakhstani Tenge', 50, true),
       ('UAH', 'Ukrainian Hryvnia', 60, true),
       ('BYN', 'Belarusian Ruble', 70, true),
       ('GEL', 'Georgian Lari', 80, true)
on conflict (code) do update
set title = excluded.title,
    sort_order = excluded.sort_order,
    is_active = excluded.is_active;
