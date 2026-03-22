ALTER TABLE holdings
ADD available_quantity NUMBER(12,0) DEFAULT 0 NOT NULL;

COMMENT ON COLUMN holdings.available_quantity IS '매도 주문 가능 수량 (holding_quantity - PENDING/SUBMITTED 매도 주문 수량)';
