"""
Broker CSV Parser module (Zerodha, Groww, ICICI Direct, CAMS CSVs).
"""
import uuid
from datetime import datetime
from decimal import Decimal
from typing import List, Optional
import pandas as pd
from .models import TaxEventSchema, EventType


class BrokerCsvParser:
    def __init__(self, csv_path: str, broker_type: str = "generic"):
        self.csv_path = csv_path
        self.broker_type = broker_type

    def parse(self) -> List[TaxEventSchema]:
        events: List[TaxEventSchema] = []
        try:
            df = pd.read_csv(self.csv_path)
            if df.empty:
                return events

            # Normalize column names
            col_map = {str(c).strip().lower(): c for c in df.columns}

            # Find matching column names
            date_col = next((col_map[k] for k in col_map if any(x in k for x in ["date", "txn_date", "trade_date"])), None)
            symbol_col = next((col_map[k] for k in col_map if any(x in k for x in ["symbol", "scheme", "scrip", "asset", "description"])), None)
            type_col = next((col_map[k] for k in col_map if any(x in k for x in ["type", "buy/sell", "transaction", "action"])), None)
            qty_col = next((col_map[k] for k in col_map if any(x in k for x in ["qty", "quantity", "units"])), None)
            price_col = next((col_map[k] for k in col_map if any(x in k for x in ["price", "nav", "rate"])), None)
            amount_col = next((col_map[k] for k in col_map if any(x in k for x in ["amount", "value", "total"])), None)

            for _, row in df.iterrows():
                try:
                    asset_name = str(row[symbol_col]) if symbol_col and pd.notna(row[symbol_col]) else "Broker Asset"
                    date_str = str(row[date_col]) if date_col and pd.notna(row[date_col]) else ""

                    event_date = datetime.now().date()
                    if date_str:
                        for fmt in ("%Y-%m-%d", "%d-%m-%Y", "%d/%m/%Y", "%d-%b-%Y"):
                            try:
                                event_date = datetime.strptime(date_str.strip(), fmt).date()
                                break
                            except ValueError:
                                pass

                    txn_type_str = str(row[type_col]).upper() if type_col and pd.notna(row[type_col]) else "BUY"
                    if any(x in txn_type_str for x in ["SELL", "REDEMPTION", "DISPOSAL", "SWITCH OUT"]):
                        event_type = EventType.DISPOSAL
                    elif "BONUS" in txn_type_str:
                        event_type = EventType.BONUS
                    elif "SPLIT" in txn_type_str:
                        event_type = EventType.SPLIT
                    else:
                        event_type = EventType.ACQUISITION

                    units = Decimal(str(abs(float(row[qty_col])))) if qty_col and pd.notna(row[qty_col]) else Decimal("1")
                    price = Decimal(str(abs(float(row[price_col])))) if price_col and pd.notna(row[price_col]) else Decimal("0")
                    amount = Decimal(str(abs(float(row[amount_col])))) if amount_col and pd.notna(row[amount_col]) else (units * price)

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
