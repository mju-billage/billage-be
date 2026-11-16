#!/usr/bin/env python3
"""
영수증 사진을 CLOVA 범용 OCR 에 한 번씩 보내고 응답을 저장한다. 채점은 저장된 응답으로 한다.

    CLOVA_INVOKE_URL='https://...apigw.ntruss.com/custom/v1/.../general' \
    CLOVA_SECRET='...' \
    python3 tools/ocr-eval/record.py ~/Project/billage-ocr-eval

- 이미 응답(<이름>.clova.json)이 있는 사진은 건너뛴다. 다시 부르면 호출 수만 쓴다.
- HEIC 는 맥의 sips 로 JPG 로 바꿔 보낸다(서버도 jpg·png 만 받는다).
- 서버와 같은 형식으로 요청한다(ClovaHttpClient): version V2, images[0] 에 base64.
- 결과마다 <이름>.fields.tsv 를 남긴다. 채점기(ReceiptOcrEvaluationTest)가 이것만 읽는다 —
  JSON 필드 순서·부가 필드에 흔들리지 않게 글자와 꼭짓점만 뽑아 둔다.

외부 패키지 없이 표준 라이브러리만 쓴다.
"""
import base64
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

SERVER_LIMIT_BYTES = 4 * 1024 * 1024  # billage.ocr.max-image-size
IMAGE_SUFFIXES = {".jpg": "jpg", ".jpeg": "jpg", ".png": "png"}


def to_jpg_if_heic(path: Path) -> Path:
    if path.suffix.lower() not in (".heic", ".heif"):
        return path
    target = path.with_suffix(".jpg")
    if not target.exists():
        if shutil.which("sips") is None:
            sys.exit(f"{path.name}: HEIC 를 바꿀 sips 가 없습니다. JPG 로 내보낸 뒤 다시 실행하세요.")
        subprocess.run(["sips", "-s", "format", "jpeg", str(path), "--out", str(target)],
                       check=True, stdout=subprocess.DEVNULL)
        print(f"  {path.name} → {target.name} (HEIC 변환)")
    return target


def call_clova(url: str, secret: str, image: Path) -> dict:
    body = json.dumps({
        "version": "V2",
        "requestId": str(uuid.uuid4()),
        "timestamp": int(time.time() * 1000),
        "images": [{
            "format": IMAGE_SUFFIXES[image.suffix.lower()],
            "name": "receipt",
            "data": base64.b64encode(image.read_bytes()).decode(),
        }],
    }).encode()
    request = urllib.request.Request(url, data=body, method="POST", headers={
        "Content-Type": "application/json",
        "X-OCR-SECRET": secret,
    })
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read())


def write_fields(response: dict, target: Path) -> int:
    """글자 \\t x1 y1 x2 y2 x3 y3 x4 y4 (꼭짓점 순서: 좌상·우상·우하·좌하)."""
    images = response.get("images") or [{}]
    fields = images[0].get("fields") or []
    rows = []
    for field in fields:
        text = (field.get("inferText") or "").replace("\t", " ").strip()
        vertices = ((field.get("boundingPoly") or {}).get("vertices")) or []
        if not text or not vertices:
            continue
        coords = [f"{v.get('x', 0)}\t{v.get('y', 0)}" for v in vertices]
        rows.append(text + "\t" + "\t".join(coords))
    target.write_text("\n".join(rows) + "\n", encoding="utf-8")
    return len(rows)


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit("사용법: python3 record.py <영수증 폴더>")
    folder = Path(sys.argv[1]).expanduser()
    url = os.environ.get("CLOVA_INVOKE_URL", "").strip()
    secret = os.environ.get("CLOVA_SECRET", "").strip()
    if not url.endswith("/general") or not secret:
        sys.exit("CLOVA_INVOKE_URL(…/general 로 끝남)과 CLOVA_SECRET 환경변수가 필요합니다.")

    photos = sorted(p for p in folder.iterdir()
                    if p.suffix.lower() in (*IMAGE_SUFFIXES, ".heic", ".heif"))
    called = 0
    for photo in photos:
        if photo.suffix.lower() in (".jpg", ".jpeg") and photo.with_suffix(".heic").exists():
            continue  # HEIC 에서 바꾼 사본. 원본 쪽에서 처리한다.
        image = to_jpg_if_heic(photo)
        raw = image.with_suffix(".clova.json")
        size_note = " ⚠ 서버 상한 4MB 초과" if image.stat().st_size > SERVER_LIMIT_BYTES else ""
        if raw.exists():
            print(f"  {image.name}: 저장된 응답 사용{size_note}")
        else:
            try:
                response = call_clova(url, secret, image)
            except urllib.error.HTTPError as e:
                print(f"  {image.name}: HTTP {e.code} {e.read()[:200]!r}")
                continue
            raw.write_text(json.dumps(response, ensure_ascii=False), encoding="utf-8")
            called += 1
            time.sleep(1)
            result = (response.get("images") or [{}])[0].get("inferResult")
            print(f"  {image.name}: {result}{size_note}")
        count = write_fields(json.loads(raw.read_text(encoding="utf-8")), image.with_suffix(".fields.tsv"))
        if count == 0:
            print(f"    → 읽은 글자가 없습니다")

    answers = folder / "answers.csv"
    if not answers.exists():
        answers.write_text("file,total,item_amounts,memo\n" + "".join(
            f"{to_jpg_if_heic(p).name},,,\n" for p in photos
            if not (p.suffix.lower() in (".jpg", ".jpeg") and p.with_suffix(".heic").exists())),
            encoding="utf-8")
        print(f"\nanswers.csv 를 만들었습니다 — 정답을 채워 주세요.")
    print(f"\n완료: 사진 {len(photos)}장, 이번에 CLOVA 호출 {called}회")


if __name__ == "__main__":
    main()
