from decimal import Decimal
from typing import List, Tuple
from .models import TaxEventSchema


def reconcile_statement(events: List[TaxEventSchema], declared_closing_units: Decimal) -> Tuple[bool, Decimal, Decimal]:
    calculated = sum((e.unit_delta() for e in events), Decimal("0.0"))
    delta = abs(calculated - declared_closing_units)
    is_matched = delta < Decimal("0.0001")
    return is_matched, calculated, delta
