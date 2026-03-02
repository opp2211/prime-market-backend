create unique index if not exists ux_user_account_txs_ref
    on user_account_txs (ref_type, ref_id);
