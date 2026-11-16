-- 영수증 인식(OCR) 결과 보관.
--
-- 지금까지는 인식 결과를 응답으로만 돌려주고 버렸다. 그래서 내역을 등록한 뒤에는 영수증에서
-- 무엇을 읽었는지 다시 볼 방법이 없었고, 보려면 건당 과금되는 OCR 을 다시 불러야 했다.
--
-- 결과는 내역이 아니라 **파일**에 붙인다. 인식은 내역을 만들기 전에 일어나므로 그때 붙일 수 있는 것이
-- 파일뿐이고, 파일이 내역의 증빙으로 연결되면(file.entry_id) 내역에서도 따라 읽힌다.
-- 파일당 한 건이며 다시 인식하면 덮어쓴다. 파일을 지우면 결과도 함께 사라진다(ON DELETE CASCADE).
CREATE TABLE receipt_ocr (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    file_id       BIGINT       NOT NULL,
    merchant_name VARCHAR(255) NULL,
    purchased_on  DATE         NULL,
    total_amount  BIGINT       NOT NULL,
    recognized_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_receipt_ocr_file UNIQUE (file_id),
    CONSTRAINT fk_receipt_ocr_file FOREIGN KEY (file_id) REFERENCES file (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 품목. 범용 모델은 품목을 읽지 못해 지금은 늘 비어 있지만, 모델을 바꿔도 스키마를 다시 손대지 않게 둔다.
-- 수량·단가·금액은 영수증에 안 찍힌 경우가 흔해 전부 NULL 을 허용한다.
CREATE TABLE receipt_ocr_item (
    receipt_ocr_id BIGINT       NOT NULL,
    line_no        INT          NOT NULL,
    name           VARCHAR(255) NULL,
    quantity       INT          NULL,
    unit_price     BIGINT       NULL,
    amount         BIGINT       NULL,
    confidence     DOUBLE       NULL,
    PRIMARY KEY (receipt_ocr_id, line_no),
    CONSTRAINT fk_receipt_ocr_item_ocr FOREIGN KEY (receipt_ocr_id) REFERENCES receipt_ocr (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
