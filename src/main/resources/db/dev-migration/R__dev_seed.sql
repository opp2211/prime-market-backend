truncate users cascade;
insert into users(username, email, password_hash)
values ('user1',
        'user1@123.123',
        '$2a$10$.Kk5wPXj7pUvsGonRUyKwuh4u3QdvWcABsTPaHFdd48HQe/J.Us/e'),
       ('user2',
        'user2@123.123',
        '$2a$10$.Kk5wPXj7pUvsGonRUyKwuh4u3QdvWcABsTPaHFdd48HQe/J.Us/e'),
       ('sup1',
        'sup1@123.123',
        '$2a$10$.Kk5wPXj7pUvsGonRUyKwuh4u3QdvWcABsTPaHFdd48HQe/J.Us/e');


insert into games (slug, title, sort_order)
select v.slug, v.title, v.sort_order
from (values ('path-of-exile', 'Path of Exile', 10),
             ('albion-online', 'Albion Online', 20)) as v(slug, title, sort_order)
where not exists(select 1
                 from games g
                 where lower(g.slug) = lower(v.slug));


insert into categories (game_id, slug, title, sort_order)
select g.id, v.slug, v.title, v.sort_order
from games g
         join (values
                   -- Path of Exile
                   ('path-of-exile', 'currency', 'Currency', 10),
                   ('path-of-exile', 'items', 'Items', 20),
                   ('path-of-exile', 'services', 'Services', 30),
                   ('path-of-exile', 'packs', 'Packs', 40),

                   -- Albion Online
                   ('albion-online', 'currency', 'Currency', 10),
                   ('albion-online', 'items', 'Items', 20),
                   ('albion-online', 'services', 'Services', 30)) as v(game_slug, slug, title, sort_order)
              on lower(g.slug) = lower(v.game_slug)
where not exists(select 1
                 from categories c
                 where c.game_id = g.id
                   and lower(c.slug) = lower(v.slug));

insert into context_dimensions (game_id, slug, title, sort_order)
select g.id, v.slug, v.title, v.sort_order
from games g
         join (values
                   -- Path of Exile
                   ('path-of-exile', 'platform', 'Platform', 10),
                   ('path-of-exile', 'league', 'League', 20),
                   ('path-of-exile', 'mode', 'Mode', 30),
                   ('path-of-exile', 'ruthless', 'Ruthless', 40),

                   -- Albion Online
                   ('albion-online', 'server', 'Server', 10)) as v(game_slug, slug, title, sort_order)
              on lower(g.slug) = lower(v.game_slug)
where not exists(select 1
                 from context_dimensions cd
                 where cd.game_id = g.id
                   and lower(cd.slug) = lower(v.slug));

insert into context_dimension_values (context_dimension_id, slug, title, sort_order)
select cd.id, v.value_slug, v.value_title, v.sort_order
from context_dimensions cd
         join games g on g.id = cd.game_id
         join (values
                   -- Path of Exile / platform
                   ('path-of-exile', 'platform', 'pc', 'PC', 10),
                   ('path-of-exile', 'platform', 'xbox', 'Xbox', 20),
                   ('path-of-exile', 'platform', 'playstation', 'PlayStation', 30),

                   -- Path of Exile / league
                   ('path-of-exile', 'league', 'standard', 'Standard', 10),
                   ('path-of-exile', 'league', 'mirage', 'Mirage', 20),

                   -- Path of Exile / mode
                   ('path-of-exile', 'mode', 'softcore', 'Softcore', 10),
                   ('path-of-exile', 'mode', 'hardcore', 'Hardcore', 20),

                   -- Path of Exile / ruthless
                   ('path-of-exile', 'ruthless', 'enabled', 'Ruthless', 10),
                   ('path-of-exile', 'ruthless', 'disabled', 'Non-Ruthless', 20),

                   -- Albion Online / server
                   ('albion-online', 'server', 'europe', 'Europe', 10),
                   ('albion-online', 'server', 'america', 'America', 20),
                   ('albion-online', 'server', 'asia', 'Asia',
                    30)) as v(game_slug, dimension_slug, value_slug, value_title, sort_order)
              on lower(g.slug) = lower(v.game_slug)
                  and lower(cd.slug) = lower(v.dimension_slug)
where not exists(select 1
                 from context_dimension_values cdv
                 where cdv.context_dimension_id = cd.id
                   and lower(cdv.slug) = lower(v.value_slug));


insert into category_context_dimensions (category_id,
                                         context_dimension_id,
                                         is_required,
                                         sort_order,
                                         default_value_id)
select c.id,
       cd.id,
       true,
       v.sort_order,
       cdv.id
from categories c
         join games g on g.id = c.game_id
         join context_dimensions cd
              on cd.game_id = g.id
         join context_dimension_values cdv
              on cdv.context_dimension_id = cd.id
         join (values
                   -- Path of Exile / currency
                   ('path-of-exile', 'currency', 'platform', 10, 'pc'),
                   ('path-of-exile', 'currency', 'league', 20, 'standard'),
                   ('path-of-exile', 'currency', 'mode', 30, 'softcore'),
                   ('path-of-exile', 'currency', 'ruthless', 40, 'disabled'),

                   -- Path of Exile / items
                   ('path-of-exile', 'items', 'platform', 10, 'pc'),
                   ('path-of-exile', 'items', 'league', 20, 'standard'),
                   ('path-of-exile', 'items', 'mode', 30, 'softcore'),
                   ('path-of-exile', 'items', 'ruthless', 40, 'disabled'),

                   -- Path of Exile / services
                   ('path-of-exile', 'services', 'platform', 10, 'pc'),
                   ('path-of-exile', 'services', 'league', 20, 'standard'),
                   ('path-of-exile', 'services', 'mode', 30, 'softcore'),
                   ('path-of-exile', 'services', 'ruthless', 40, 'disabled'),

                   -- Albion Online / currency
                   ('albion-online', 'currency', 'server', 10, 'europe'),

                   -- Albion Online / items
                   ('albion-online', 'items', 'server', 10, 'europe'),

                   -- Albion Online / services
                   ('albion-online', 'services', 'server', 10, 'europe')) as v(game_slug, category_slug, dimension_slug,
                                                                               sort_order, default_value_slug)
              on lower(g.slug) = lower(v.game_slug)
                  and lower(c.slug) = lower(v.category_slug)
                  and lower(cd.slug) = lower(v.dimension_slug)
                  and lower(cdv.slug) = lower(v.default_value_slug)
where not exists (select 1
                  from category_context_dimensions ccd
                  where ccd.category_id = c.id
                    and ccd.context_dimension_id = cd.id);

insert into category_attributes (category_id,
                                 slug,
                                 title,
                                 data_type,
                                 is_required,
                                 sort_order,
                                 is_active)
select c.id,
       v.slug,
       v.title,
       v.data_type,
       v.is_required,
       v.sort_order,
       true
from categories c
         join games g on g.id = c.game_id
         join (values
                   -- Path of Exile / currency
                   ('path-of-exile', 'currency', 'currency-type', 'Currency Type', 'select', true, 10),

                   -- Path of Exile / items
                   ('path-of-exile', 'items', 'item-name', 'Item Name', 'text', true, 10),

                   -- Path of Exile / services
                   ('path-of-exile', 'services', 'service-type', 'Service Type', 'select', true, 10),

                   -- Albion Online / currency
                   ('albion-online', 'currency', 'currency-type', 'Currency Type', 'select', true, 10),

                   -- Albion Online / items
                   ('albion-online', 'items', 'item-name', 'Item Name', 'text', true, 10),

                   -- Albion Online / services
                   ('albion-online', 'services', 'service-type', 'Service Type', 'select', true, 10)) as v(game_slug,
                                                                                                           category_slug,
                                                                                                           slug, title,
                                                                                                           data_type,
                                                                                                           is_required,
                                                                                                           sort_order)
              on lower(g.slug) = lower(v.game_slug)
                  and lower(c.slug) = lower(v.category_slug)
where not exists (select 1
                  from category_attributes ca
                  where ca.category_id = c.id
                    and lower(ca.slug) = lower(v.slug));

insert into category_attribute_options (category_attribute_id,
                                        slug,
                                        title,
                                        sort_order,
                                        is_active)
select ca.id,
       v.option_slug,
       v.option_title,
       v.sort_order,
       true
from category_attributes ca
         join categories c on c.id = ca.category_id
         join games g on g.id = c.game_id
         join (values
                   -- Path of Exile / currency / currency-type
                   ('path-of-exile', 'currency', 'currency-type', 'chaos-orb', 'Chaos Orb', 10),
                   ('path-of-exile', 'currency', 'currency-type', 'divine-orb', 'Divine Orb', 20),
                   ('path-of-exile', 'currency', 'currency-type', 'exalted-orb', 'Exalted Orb', 30),

                   -- Path of Exile / services / service-type
                   ('path-of-exile', 'services', 'service-type', 'boss-carry', 'Boss Carry', 10),
                   ('path-of-exile', 'services', 'service-type', 'leveling', 'Leveling', 20),

                   -- Albion Online / currency / currency-type
                   ('albion-online', 'currency', 'currency-type', 'silver', 'Silver', 10),

                   -- Albion Online / services / service-type
                   ('albion-online', 'services', 'service-type', 'transport', 'Transport', 10),
                   ('albion-online', 'services', 'service-type', 'leveling', 'Leveling', 20)) as v(game_slug,
                                                                                                   category_slug,
                                                                                                   attribute_slug,
                                                                                                   option_slug,
                                                                                                   option_title,
                                                                                                   sort_order)
              on lower(g.slug) = lower(v.game_slug)
                  and lower(c.slug) = lower(v.category_slug)
                  and lower(ca.slug) = lower(v.attribute_slug)
where not exists (select 1
                  from category_attribute_options cao
                  where cao.category_attribute_id = ca.id
                    and lower(cao.slug) = lower(v.option_slug));


insert into delivery_methods (category_id, slug, title, sort_order)
select c.id,
       v.slug,
       v.title,
       v.sort_order
from categories c
         join games g on g.id = c.game_id
         join (values
                   -- Path of Exile / currency
                   ('path-of-exile', 'currency', 'f2f', 'Face to Face', 10),
                   ('path-of-exile', 'currency', 'poe-trade-link', 'PoE Trade Link', 20),
                   ('path-of-exile', 'currency', 'merchant-tab', 'Merchant Tab', 30),

                   -- Path of Exile / items
                   ('path-of-exile', 'items', 'f2f', 'Face to Face', 10),
                   ('path-of-exile', 'items', 'poe-trade-link', 'PoE Trade Link', 20),

                   -- Path of Exile / services
                   ('path-of-exile', 'services', 'self-play', 'Self Play', 10),
                   ('path-of-exile', 'services', 'piloted', 'Piloted', 20),
                   ('path-of-exile', 'services', 'account-share', 'Account Share', 30),

                   -- Albion Online / currency
                   ('albion-online', 'currency', 'f2f', 'Face to Face', 20),
                   ('albion-online', 'currency', 'island', 'Island', 10),
                   ('albion-online', 'currency', 'market', 'Market', 30),

                   -- Albion Online / items
                   ('albion-online', 'items', 'f2f', 'Face to Face', 10),
                   ('albion-online', 'items', 'island', 'Island', 20),

                   -- Albion Online / services
                   ('albion-online', 'services', 'self-play', 'Self Play', 10),
                   ('albion-online', 'services', 'account-share', 'Account Share',
                    20)) as v(game_slug, category_slug, slug, title, sort_order)
              on lower(g.slug) = lower(v.game_slug)
                  and lower(c.slug) = lower(v.category_slug)
where not exists (select 1
                  from delivery_methods dm
                  where dm.category_id = c.id
                    and lower(dm.slug) = lower(v.slug));

insert into category_trade_field_configs (category_id,
                                          field_slug,
                                          is_visible,
                                          is_required,
                                          is_multiselect,
                                          default_value_text,
                                          default_value_number,
                                          sort_order)
select c.id,
       v.field_slug,
       v.is_visible,
       v.is_required,
       v.is_multiselect,
       v.default_value_text,
       v.default_value_number,
       v.sort_order
from categories c
         join games g on g.id = c.game_id
         join (values
                   -- Path of Exile / currency
                   ('path-of-exile', 'currency', 'quantity', true, true, false, null, null, 10),
                   ('path-of-exile', 'currency', 'min-trade-quantity', true, false, false, null, null, 20),
                   ('path-of-exile', 'currency', 'max-trade-quantity', true, false, false, null, null, 30),
                   ('path-of-exile', 'currency', 'quantity-step', true, false, false, null, 1, 40),
                   ('path-of-exile', 'currency', 'trade-terms', true, false, false, null, null, 50),
                   ('path-of-exile', 'currency', 'delivery-methods', true, true, true, null, null, 60),

                   -- Path of Exile / items
                   ('path-of-exile', 'items', 'quantity', false, false, false, null, 1, 10),
                   ('path-of-exile', 'items', 'min-trade-quantity', false, false, false, null, null, 20),
                   ('path-of-exile', 'items', 'max-trade-quantity', false, false, false, null, null, 30),
                   ('path-of-exile', 'items', 'quantity-step', false, false, false, null, 1, 40),
                   ('path-of-exile', 'items', 'trade-terms', true, false, false, null, null, 50),
                   ('path-of-exile', 'items', 'delivery-methods', true, true, true, null, null, 60),

                   -- Path of Exile / services
                   ('path-of-exile', 'services', 'quantity', false, false, false, null, 1, 10),
                   ('path-of-exile', 'services', 'min-trade-quantity', false, false, false, null, null, 20),
                   ('path-of-exile', 'services', 'max-trade-quantity', false, false, false, null, null, 30),
                   ('path-of-exile', 'services', 'quantity-step', false, false, false, null, 1, 40),
                   ('path-of-exile', 'services', 'trade-terms', true, false, false, null, null, 50),
                   ('path-of-exile', 'services', 'delivery-methods', true, false, true, null, null, 60),

                   -- Path of Exile / donate-packs-coins
                   ('path-of-exile', 'donate-packs-coins', 'quantity', false, false, false, null, 1, 10),
                   ('path-of-exile', 'donate-packs-coins', 'min-trade-quantity', false, false, false, null, null, 20),
                   ('path-of-exile', 'donate-packs-coins', 'max-trade-quantity', false, false, false, null, null, 30),
                   ('path-of-exile', 'donate-packs-coins', 'quantity-step', false, false, false, null, 1, 40),
                   ('path-of-exile', 'donate-packs-coins', 'trade-terms', true, false, false, null, null, 50),
                   ('path-of-exile', 'donate-packs-coins', 'delivery-methods', false, false, true, null, null, 60),

                   -- Albion Online / currency
                   ('albion-online', 'currency', 'quantity', true, true, false, null, null, 10),
                   ('albion-online', 'currency', 'min-trade-quantity', true, false, false, null, null, 20),
                   ('albion-online', 'currency', 'max-trade-quantity', true, false, false, null, null, 30),
                   ('albion-online', 'currency', 'quantity-step', true, false, false, null, 1, 40),
                   ('albion-online', 'currency', 'trade-terms', true, false, false, null, null, 50),
                   ('albion-online', 'currency', 'delivery-methods', true, true, true, null, null, 60),

                   -- Albion Online / items
                   ('albion-online', 'items', 'quantity', false, false, false, null, 1, 10),
                   ('albion-online', 'items', 'min-trade-quantity', false, false, false, null, null, 20),
                   ('albion-online', 'items', 'max-trade-quantity', false, false, false, null, null, 30),
                   ('albion-online', 'items', 'quantity-step', false, false, false, null, 1, 40),
                   ('albion-online', 'items', 'trade-terms', true, false, false, null, null, 50),
                   ('albion-online', 'items', 'delivery-methods', true, true, true, null, null, 60),

                   -- Albion Online / services
                   ('albion-online', 'services', 'quantity', false, false, false, null, 1, 10),
                   ('albion-online', 'services', 'min-trade-quantity', false, false, false, null, null, 20),
                   ('albion-online', 'services', 'max-trade-quantity', false, false, false, null, null, 30),
                   ('albion-online', 'services', 'quantity-step', false, false, false, null, 1, 40),
                   ('albion-online', 'services', 'trade-terms', true, false, false, null, null, 50),
                   ('albion-online', 'services', 'delivery-methods', true, false, true, null, null, 60)) as v(
                                                                                                              game_slug,
                                                                                                              category_slug,
                                                                                                              field_slug,
                                                                                                              is_visible,
                                                                                                              is_required,
                                                                                                              is_multiselect,
                                                                                                              default_value_text,
                                                                                                              default_value_number,
                                                                                                              sort_order
    )
              on lower(g.slug) = lower(v.game_slug)
                  and lower(c.slug) = lower(v.category_slug)
where not exists (select 1
                  from category_trade_field_configs ctfc
                  where ctfc.category_id = c.id
                    and lower(ctfc.field_slug) = lower(v.field_slug));

insert into currency_rates (from_currency_code, to_currency_code, rate, source, note)
select v.from_currency_code, v.to_currency_code, v.rate, v.source, v.note
from (values ('USD', 'RUB', 92.50000000, 'system', 'dev seed'),
             ('RUB', 'USD', 0.01050000, 'system', 'dev seed'),

             ('USD', 'KZT', 475.00000000, 'system', 'dev seed'),
             ('KZT', 'USD', 0.00205000, 'system', 'dev seed'),

             ('USD', 'UAH', 39.50000000, 'system', 'dev seed'),
             ('UAH', 'USD', 0.02480000, 'system', 'dev seed'),

             ('USD', 'GEL', 2.70000000, 'system', 'dev seed'),
             ('GEL', 'USD', 0.36000000, 'system', 'dev seed'),

             ('USD', 'BYN', 3.25000000, 'system', 'dev seed'),
             ('BYN', 'USD', 0.30500000, 'system',
              'dev seed')) as v(from_currency_code, to_currency_code, rate, source, note)
where not exists (select 1
                  from currency_rates cr
                  where cr.from_currency_code = v.from_currency_code
                    and cr.to_currency_code = v.to_currency_code);