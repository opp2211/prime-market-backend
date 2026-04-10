alter table orders
    add column taker_username_snapshot varchar(24);

update orders o
set taker_username_snapshot = u.username
from users u
where u.id = o.taker_user_id
  and o.taker_username_snapshot is null;

alter table orders
    alter column taker_username_snapshot set not null;
