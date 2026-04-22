insert into permissions (code)
select v.code
from (values
          ('DEPOSIT_APPROVE'),
          ('BACKOFFICE_ACCESS')) as v(code)
where not exists(select 1
                 from permissions p
                 where lower(p.code) = lower(v.code));
