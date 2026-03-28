-- 종목-테마 매핑 테이블
CREATE TABLE instrument_theme_mappings (
    mapping_id      NUMBER(19,0) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    instrument_id   NUMBER(19,0) NOT NULL,
    theme_code      VARCHAR2(20) NOT NULL,
    CONSTRAINT fk_theme_mapping_instrument
        FOREIGN KEY (instrument_id) REFERENCES instruments (instrument_id),
    CONSTRAINT uq_instrument_theme
        UNIQUE (instrument_id, theme_code),
    CONSTRAINT chk_theme_code
        CHECK (theme_code IN (
            'SEMICONDUCTOR', 'BATTERY', 'AUTOMOTIVE', 'IT_PLATFORM',
            'ENERGY_CHEMICAL', 'FINANCE', 'SHIPBUILDING', 'DEFENSE',
            'MANUFACTURING', 'ROBOT'
        ))
);

CREATE INDEX idx_theme_mappings_instrument ON instrument_theme_mappings (instrument_id);
CREATE INDEX idx_theme_mappings_theme ON instrument_theme_mappings (theme_code);
