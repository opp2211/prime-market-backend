create table currencies
(
    code      varchar(5) PRIMARY KEY,
    title     text,
    is_active boolean default true
);

insert into currencies (code, title)
values
    ('USD', 'US Dollar'),
    ('RUB', 'Russian Ruble'),
    ('KZT', 'Kazakhstani Tenge'),
    ('UAH', 'Ukrainian Hryvnia'),
    ('GEL', 'Georgian Lari'),
    ('BYN', 'Belarusian Ruble');
