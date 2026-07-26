from enum import Enum
from datetime import date, datetime
from decimal import Decimal
from typing import Optional
from pydantic import BaseModel, Field


class EventType(str, Enum):
    ACQUISITION = "ACQUISITION"
    SIP_INSTALMENT = "SIP_INSTALMENT"
    DISPOSAL = "DISPOSAL"
    BONUS = "BONUS"
    SPLIT = "SPLIT"
    DIVIDEND_REINVEST = "DIVIDEND_REINVEST"
    SGB_INTEREST = "SGB_INTEREST"
    SGB_MATURITY = "SGB_MATURITY"
    MERGER = "MERGER"


class TaxEventSchema(BaseModel):
    id: str
    asset_id: str = Field(..., alias="assetId")
    asset_name: str = Field(..., alias="assetName")
    isin: Optional[str] = None
    event_type: EventType = Field(..., alias="eventType")
    event_date: date = Field(..., alias="eventDate")
    units: Decimal
    price_per_unit: Decimal = Field(..., alias="pricePerUnit")
    gross_amount: Decimal = Field(..., alias="grossAmount")
    source_document_id: str = Field(..., alias="sourceDocumentId")
    ingested_at: datetime = Field(default_factory=datetime.utcnow, alias="ingestedAt")

    class Config:
        populate_by_name = True

    def unit_delta(self) -> Decimal:
        if self.event_type == EventType.DISPOSAL:
            return -self.units
        elif self.event_type == EventType.SGB_INTEREST:
            return Decimal("0.0")
        return self.units
