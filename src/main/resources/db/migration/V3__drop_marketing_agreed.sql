-- users 테이블에서 불필요한 컬럼 제거
-- 동의 이력을 별도 테이블로 관리

CREATE TABLE user_consents (
    user_id                  NUMBER(19,0)  PRIMARY KEY,
    service_terms_agreed_yn  CHAR(1)       DEFAULT 'N' NOT NULL,
    privacy_terms_agreed_yn  CHAR(1)       DEFAULT 'N' NOT NULL,
    terms_agreed_at          TIMESTAMP     NOT NULL,
    CONSTRAINT fk_uconsents_user FOREIGN KEY (user_id) REFERENCES users (user_id)
);
