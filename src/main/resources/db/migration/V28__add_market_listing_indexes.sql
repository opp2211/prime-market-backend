create index if not exists ix_offers_market_listing
    on offers (status, game_id, category_id, side, published_at desc);

create index if not exists ix_offers_price_currency_code
    on offers (price_currency_code);
