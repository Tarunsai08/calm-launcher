#!/usr/bin/env python3
"""Turn lint / JUnit XML reports into GitHub Actions annotations.

Annotations show up on the public run summary page, so failures can be read without
downloading artifacts or opening raw logs.

Usage: annotate.py lint <lint-results.xml>...   |   annotate.py junit <dir-or-xml>...
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET


def esc(text: str) -> str:
    return (text or "").replace("%", "%25").replace("\r", "").replace("\n", "%0A")


def files(args):
    for a in args:
        if os.path.isdir(a):
            yield from glob.glob(os.path.join(a, "**", "*.xml"), recursive=True)
        else:
            yield from glob.glob(a)


def lint(paths):
    count = 0
    for path in files(paths):
        root = ET.parse(path).getroot()
        for issue in root.iter("issue"):
            severity = issue.get("severity", "")
            level = "error" if severity in ("Error", "Fatal") else "warning"
            if level != "error":
                continue
            loc = issue.find("location")
            file = loc.get("file", "") if loc is not None else ""
            line = loc.get("line", "1") if loc is not None else "1"
            file = os.path.relpath(file) if file.startswith("/") else file
            msg = f"[{issue.get('id')}] {issue.get('message')}"
            print(f"::error file={file},line={line},title=Lint {issue.get('id')}::{esc(msg)}")
            count += 1
    print(f"{count} lint error(s) annotated")


def junit(paths):
    count = 0
    for path in files(paths):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for case in root.iter("testcase"):
            for kind in ("failure", "error"):
                node = case.find(kind)
                if node is None:
                    continue
                name = f"{case.get('classname')}.{case.get('name')}"
                body = (node.get("message") or "") + "\n" + (node.text or "")
                print(f"::error title=Test failed: {esc(name)}::{esc(body[:3000])}")
                count += 1
    print(f"{count} failing test(s) annotated")


if __name__ == "__main__":
    mode, rest = sys.argv[1], sys.argv[2:]
    {"lint": lint, "junit": junit}[mode](rest)
