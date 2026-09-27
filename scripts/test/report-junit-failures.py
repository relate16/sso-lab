#!/usr/bin/env python3
"""Expose concise, sanitized JUnit failures as GitHub Actions annotations."""

from __future__ import annotations

import pathlib
import re
import sys
import xml.etree.ElementTree as ET


SECRET_VALUE = re.compile(
    r"(?i)(secret|token|password|authorization|cookie)(\s*[=:]\s*)([^\s,;]+)"
)


def sanitize(value: str) -> str:
    compact = " ".join(value.split())
    return SECRET_VALUE.sub(r"\1\2[REDACTED]", compact)[:1500]


def main() -> int:
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "backend")
    failures = 0
    for report in sorted(root.glob("*/build/test-results/test/TEST-*.xml")):
        suite = ET.parse(report).getroot()
        for case in suite.findall("testcase"):
            failure = case.find("failure")
            if failure is None:
                failure = case.find("error")
            if failure is None:
                continue
            failures += 1
            test_name = f"{case.get('classname', '')}.{case.get('name', '')}".strip(".")
            detail = failure.get("message") or failure.text or "JUnit test failed"
            print(f"::error title=Backend test failed: {sanitize(test_name)}::{sanitize(detail)}")
    if failures == 0:
        print("backend_test_report|no_junit_failure_details")
    else:
        print(f"backend_test_report|failures={failures}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
