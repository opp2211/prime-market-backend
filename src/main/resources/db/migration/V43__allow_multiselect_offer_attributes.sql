alter table offer_attribute_values
    drop constraint if exists ux_offer_attribute_values_offer_attribute;

create unique index if not exists ux_offer_attribute_values_offer_attribute_option
    on offer_attribute_values (offer_id, category_attribute_id, category_attribute_option_id)
    where category_attribute_option_id is not null;

create unique index if not exists ux_offer_attribute_values_offer_attribute_scalar
    on offer_attribute_values (offer_id, category_attribute_id)
    where category_attribute_option_id is null;
