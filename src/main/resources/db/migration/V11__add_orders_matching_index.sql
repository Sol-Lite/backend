CREATE INDEX idx_orders_match_pending_limit
    ON orders (instrument_id, order_status, order_kind, order_side, order_price, requested_at);
