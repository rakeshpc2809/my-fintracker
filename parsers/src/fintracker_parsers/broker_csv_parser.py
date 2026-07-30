"""
Broker CSV Parser module (Zerodha, Groww, ICICI Direct, CAMS CSVs) using Polars.
"""
import uuid
from datetime import datetime
from decimal import Decimal
from typing import List, Optional
import polars as pl
from .models import TaxEventSchema, EventType


class BrokerCsvParser:
    def __init__(self, csv_path: str, broker_type: str = "generic"):
        self.csv_path = csv_path
        self.broker_type = broker_type

    def parse(self) -> List[TaxEventSchema]:
        events: List[TaxEventSchema] = []
        try:
            df = pl.read_csv(self.csv_path, infer_schema_length=0)
            if df.is_empty():
                return events

            col_map = {str(c).strip().lower(): c for c in df.columns}

            date_col = next((col_map[k] for k in col_map if any(x in k for x in ["date", "txn_date", "trade_date"])), None)
            symbol_col = next((col_map[k] for k in col_map if any(x in k for x in ["symbol", "scheme", "scrip", "asset", "description"])), None)
            type_col = next((col_map[k] for k in col_map if any(x in k for x in ["type", "buy/sell", "transaction", "action"])), None)
            qty_col = next((col_map[k] for k in col_map if any(x in k for x in ["qty", "quantity", "units"])), None)
            price_col = next((col_map[k] for k in col_map if any(x in k for x in ["price", "nav", "rate"])), None)
            amount_col = next((col_map[k] for k in col_map if any(x in k for x in ["amount", "value", "total"])), None)

            for row in df.to_dicts():
                try:
                    asset_name = str(row[symbol_col]) if symbol_col and row.get(symbol_col) else "Broker Asset"
                    date_str = str(row[date_col]) if date_col and row.get(date_col) else ""

                    event_date = datetime.now().date()
                    if date_str:
                        for fmt in ("%Y-%m-%d", "%d-%m-%Y", "%d/%m/%Y", "%d-%b-%Y"):
                            try:
                                event_date = datetime.strptime(date_str.strip(), fmt).date()
                                break
                            except ValueError:
                                pass

                    txn_type_str = str(row[type_col]).upper() if type_col and row.get(type_col) else "BUY"
                    if any(x in txn_type_str for x in ["SELL", "REDEMPTION", "DISPOSAL", "SWITCH OUT"]):
                        event_type = EventType.DISPOSAL
                    elif "BONUS" in txn_type_str:
                        event_type = EventType.BONUS
                    elif "SPLIT" in txn_type_str:
                        event_type = EventType.SPLIT
                    else:
                        event_type = EventType.ACQUISITION

                    units_val = row.get(qty_col)
                    units = Decimal(str(abs(float(units_val)))) if units_val is not None and str(units_val).strip() != "" else Decimal("1")
                    
                    price_val = row.get(price_col)
                    price = Decimal(str(abs(float(price_val)))) if price_val is not None and str(price_val).strip() != "" else Decimal("0")
                    
                    amt_val = row.get(amount_col)
                    amount = Decimal(str(abs(float(amt_val)))) if amt_val is not None and str(amt_val).strip() != "" else (units * price)

                    events.append(
                        TaxEventSchema(
                            id=str(uuid.uuid4()),
                            assetId=asset_name.replace(" ", "_").upper()[:20],
                            assetName=asset_name,
                            isin=None,
                            eventType=event_type,
                            eventDate=event_date,
                            units=units,
                            pricePerUnit=price,
                            grossAmount=amount,
                            sourceDocumentId=self.csv_path
                        )
                    )
                except Exception:
                    continue
        except Exception:
            pass

        return events

