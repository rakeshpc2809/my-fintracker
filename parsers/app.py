import os
import tempfile
from typing import List, Optional
from fastapi import FastAPI, File, Form, UploadFile, HTTPException
from pydantic import BaseModel
import polars as pl

from src.cas_parser import parse_cas_pdf
from src.broker_csv_parser import parse_broker_csv

app = FastAPI(title="Portfolio OS Parsers API", version="3.0.0")

class TaxEventDto(BaseModel):
    id: str
    assetId: str
    assetName: str
    isin: Optional[str] = None
    eventType: str
    eventDate: str
    units: str
    pricePerUnit: str
    grossAmount: str
    sourceDocumentId: str
    ingestedAt: str

@app.get("/health")
def health_check():
    return {"status": "UP", "engine": "Polars + FastAPI", "version": "3.0.0"}

@app.post("/api/v1/parse", response_model=List[TaxEventDto])
async def parse_statement(
    file: UploadFile = File(...),
    password: Optional[str] = Form(None)
):
    filename = file.filename or "statement"
    ext = os.path.splitext(filename)[1].lower()

    with tempfile.NamedTemporaryFile(delete=False, suffix=ext) as tmp:
        content = await file.read()
        tmp.write(content)
        tmp_path = tmp.name

    try:
        events = []
        if ext == ".pdf":
            events = parse_cas_pdf(tmp_path, password=password)
        elif ext == ".csv":
            events = parse_broker_csv(tmp_path)
        else:
            raise HTTPException(status_code=400, detail="Unsupported file format. Please upload PDF or CSV.")

        # Polars multi-threaded data frame validation
        if events:
            df = pl.DataFrame([e.model_dump() for e in events])
            # Verify required columns with Polars
            required_cols = ["id", "assetId", "assetName", "eventType", "eventDate", "units", "grossAmount"]
            for col in required_cols:
                if col not in df.columns:
                    raise HTTPException(status_code=422, detail=f"Missing column in parsed dataframe: {col}")

        result = [
            TaxEventDto(
                id=str(e.id),
                assetId=str(e.assetId),
                assetName=str(e.assetName),
                isin=str(e.isin) if e.isin else None,
                eventType=e.eventType.value if hasattr(e.eventType, 'value') else str(e.eventType),
                eventDate=str(e.eventDate),
                units=str(e.units),
                pricePerUnit=str(e.pricePerUnit),
                grossAmount=str(e.grossAmount),
                sourceDocumentId=str(e.sourceDocumentId),
                ingestedAt=e.ingestedAt.isoformat()
            )
            for e in events
        ]
        return result
    except Exception as err:
        raise HTTPException(status_code=500, detail=str(err))
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
