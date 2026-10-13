import os
import re
import tempfile
from functools import lru_cache
from pathlib import Path
from typing import Any

from fastapi import FastAPI, File, HTTPException, UploadFile
from pydantic import BaseModel, Field

app = FastAPI(title="PCB QMS OCR", version="0.1.0")


class ExtractionResponse(BaseModel):
    fields: dict[str, str] = Field(default_factory=dict)
    raw_text: str = ""
    confidence: float = 0.0


@lru_cache(maxsize=1)
def get_ocr_engine() -> Any:
    # PaddleOCR V3 model. Keep engine creation lazy because model loading is expensive.
    from paddleocr import PaddleOCR

    return PaddleOCR(lang="ch")


def compact_text(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip()


def extract_fields(raw_text: str) -> dict[str, str]:
    """Extract only conservative candidates; QMS still requires human confirmation."""
    normalized = compact_text(raw_text)
    patterns: dict[str, list[str]] = {
        "workOrderNo": [
            r"(?:工单号|生产指令|生产单号)\s*[:：#]?\s*([A-Za-z0-9_-]{5,})",
            r"\b(WO[-_]?[A-Za-z0-9_-]{4,})\b",
        ],
        "batchNo": [r"(?:批次号|生产批次|批号)\s*[:：#]?\s*([A-Za-z0-9_-]{4,})"],
        "productCode": [r"(?:产品编号|料号|客户料号)\s*[:：#]?\s*([A-Za-z0-9_.-]{3,})"],
        "productName": [r"(?:产品名称|品名)\s*[:：#]?\s*([^\s]{2,40})"],
        "specification": [r"(?:规格|板厚|型号)\s*[:：#]?\s*([^\n]{2,80})"],
        "plannedQuantity": [r"(?:计划数量|工单数量|数量)\s*[:：#]?\s*(\d{1,9})"],
        "currentProcessName": [r"(?:当前工序|工序)\s*[:：#]?\s*([^\s]{2,30})"],
    }
    fields: dict[str, str] = {}
    for field, candidates in patterns.items():
        for pattern in candidates:
            match = re.search(pattern, normalized, flags=re.IGNORECASE)
            if match:
                fields[field] = compact_text(match.group(1))
                break
    return fields


def read_text(image_path: str) -> tuple[str, float]:
    engine = get_ocr_engine()
    text_parts: list[str] = []
    confidences: list[float] = []
    try:
        # PaddleOCR 3.x primary API.
        predictions = engine.predict(image_path)
        for prediction in predictions:
            payload = prediction.json if hasattr(prediction, "json") else prediction
            if callable(payload):
                payload = payload()
            if isinstance(payload, dict):
                result = payload.get("res", payload)
                text_parts.extend(str(item) for item in result.get("rec_texts", []) if item)
                confidences.extend(float(item) for item in result.get("rec_scores", []) if item is not None)
    except AttributeError:
        # Compatibility fallback for PaddleOCR 2.x-style deployments.
        result = engine.ocr(image_path, cls=True)
        for block in result or []:
            for line in block or []:
                if len(line) > 1 and len(line[1]) > 1:
                    text_parts.append(str(line[1][0]))
                    confidences.append(float(line[1][1]))
    return "\n".join(text_parts), sum(confidences) / len(confidences) if confidences else 0.0


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/v1/extractions/work-order", response_model=ExtractionResponse)
async def extract_work_order(image: UploadFile = File(...)) -> ExtractionResponse:
    if image.content_type not in {"image/jpeg", "image/png", "image/webp"}:
        raise HTTPException(status_code=400, detail="仅支持 JPG、PNG 或 WEBP 图像")
    suffix = Path(image.filename or "image.png").suffix or ".png"
    temporary_path = ""
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temporary_file:
            temporary_file.write(await image.read())
            temporary_path = temporary_file.name
        raw_text, confidence = read_text(temporary_path)
        return ExtractionResponse(fields=extract_fields(raw_text), raw_text=raw_text, confidence=round(confidence, 4))
    except Exception as exception:
        raise HTTPException(status_code=422, detail=f"OCR 识别失败：{exception}") from exception
    finally:
        if temporary_path and os.path.exists(temporary_path):
            os.unlink(temporary_path)

