#!/usr/bin/env python3
"""SELECT-only current source fingerprint; never replaces the historical fixture baseline."""
import datetime
import hashlib
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
TABLES = ("material", "material_category", "bom_parent", "bom_detail",
          "bom_sync_status", "bom_snapshot", "bom_snapshot_detail")


def query(statement):
    assert statement.startswith(("SELECT", "SHOW"))
    process = subprocess.run(["docker", "exec", "-i", "mrp-mysql", "sh", "-c",
                              'MYSQL_PWD="$MYSQL_PASSWORD" mysql --raw -u"$MYSQL_USER" -N -B mrp'],
                             input=statement, text=True, capture_output=True, check=True)
    return process.stdout.splitlines()


def snapshot():
    values = {}
    for table in TABLES:
        columns = [row.split("\t")[0] for row in query("SHOW COLUMNS FROM `" + table + "`;")]
        expression = ",".join("IFNULL(HEX(`" + col + "`),'NULL')" for col in columns)
        rows = query("SELECT CONCAT_WS('|'," + expression + ") FROM `" + table + "` ORDER BY id;")
        values[table] = {"rows": len(rows), "sha256": hashlib.sha256(
            ("\n".join(rows) + "\n").encode()).hexdigest()}
    return {"checkedAt": datetime.datetime.now().astimezone().isoformat(), "tables": values}


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("before", "after"):
        raise SystemExit("usage: current_source_snapshot.py before|after")
    path = ROOT / ("evidence/source-current-2026-11-02-" + sys.argv[1] + ".json")
    result = snapshot()
    path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"path": str(path), "checkedAt": result["checkedAt"],
                      "rows": {key: value["rows"] for key, value in result["tables"].items()}}, ensure_ascii=False))
