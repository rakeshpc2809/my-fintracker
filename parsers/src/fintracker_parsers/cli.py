"""
Ephemeral On-Demand CLI Entry point for CAMS/KFintech CAS and Broker CSV parsing.
"""
import sys
import json
import argparse
from decimal import Decimal
from .cas_parser import CasPdfParser
from .broker_csv_parser import BrokerCsvParser
from .reconciler import reconcile_statement


def main():
    parser = argparse.ArgumentParser(description="Tax Ledger Statement Parsing Engine")
    parser.add_argument("--file", required=True, help="Path to statement file (PDF/CSV)")
    parser.add_argument("--type", choices=["cas", "broker_csv"], default="cas", help="Statement type")
    parser.add_argument("--password", required=False, help="PDF password if encrypted")
    parser.add_argument("--closing-units", type=str, required=False, help="Declared closing units for reconciliation")

    args = parser.parse_args()

    if args.type == "cas":
        cas_parser = CasPdfParser(args.file, args.password)
        events = cas_parser.parse_events()
    else:
        broker_parser = BrokerCsvParser(args.file, "generic")
        events = broker_parser.parse()

    output = {
        "status": "SUCCESS",
        "events": [e.model_dump(by_alias=True, mode="json") for e in events]
    }

    if args.closing_units is not None:
        declared = Decimal(args.closing_units)
        is_matched, calculated, delta = reconcile_statement(events, declared)
        output["reconciliation"] = {
            "isMatched": is_matched,
            "calculatedClosingUnits": str(calculated),
            "declaredClosingUnits": str(declared),
            "delta": str(delta)
        }

    print(json.dumps(output, indent=2))


if __name__ == "__main__":
    main()
