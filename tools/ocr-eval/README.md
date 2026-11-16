# 영수증 인식 정확도 평가

실제 영수증으로 총액·품목 인식 정확도를 잰다. **CLOVA 는 영수증당 한 번만 부르고**, 채점은 저장된 응답으로
몇 번이든 다시 한다 — 파서 규칙을 고칠 때마다 같은 영수증으로 전후를 비교할 수 있다.

> 영수증 사진·응답에는 카드번호 일부와 가게 정보가 있다. **저장소 밖 폴더에 둔다**(공개 저장소다).

## 1. 사진 모으기

`~/Project/billage-ocr-eval/` 같은 저장소 밖 폴더에 영수증 사진을 넣는다(jpg·png·heic).
일부러 섞을 것: 마트(품목 많음)·카페·식당·편의점, 카드전표만 있는 것, 비스듬히 찍은 것, 구겨진 것.

## 2. CLOVA 응답 저장 (맥에서, 1회)

서버의 `/etc/billage/billage.env` 에 있는 `BILLAGE_OCR_INVOKE_URL` / `BILLAGE_OCR_SECRET_KEY` 값을 쓴다.

```bash
export CLOVA_INVOKE_URL='https://…/general'
export CLOVA_SECRET='…'          # 작은따옴표로 감싼다(특수문자)
python3 tools/ocr-eval/record.py ~/Project/billage-ocr-eval
```

사진마다 `<이름>.clova.json`(원본 응답)과 `<이름>.fields.tsv`(채점용)가 생긴다. 응답이 이미 있으면 다시 부르지 않는다.
4MB 를 넘는 사진은 ⚠ 로 표시한다 — 지금 서버는 이런 사진을 거절한다.

## 3. 정답 채우기 — `answers.csv`

```csv
file,total,item_amounts,memo
r01.jpg,6500,2000|1500|3000,편의점
r02.jpg,12000,,카드전표만
r03.jpg,,-,얼룩으로 금액이 가려짐
```

- `total`: 최종 결제 금액. 숫자만(쉼표 없이).
- `item_amounts`: 품목 **금액**을 영수증 위에서부터 `|` 로 잇는다(수량×단가가 아니라 그 줄의 금액).
  **품목이 없는 영수증은 비운다** — 이때 품목을 채우면 "틀림"이다(카드전표의 결제 줄을 품목으로 집는 것 등).
  품목을 채점하지 않을 영수증은 `-` 로 적는다.

## 4. 채점

```bash
OCR_EVAL_DIR=~/Project/billage-ocr-eval ./gradlew test --tests '*ReceiptOcrEvaluationTest' --rerun
```

결과는 폴더의 `report.md`. 볼 숫자:

| 항목 | 뜻 |
|---|---|
| 총액 정확도 | 읽은 총액이 정답과 같은 비율 |
| 품목 채움 | 품목을 채운 비율. 합계가 안 맞으면 비우므로 낮을 수 있다 |
| **품목을 채웠는데 틀림** | **0 이어야 한다.** 0 이 아니면 합계 검증이 뚫린 것 |

틀린 영수증은 아래에 줄 복원 결과가 붙는다 — 규칙을 어디서 고칠지는 이것으로 본다.
