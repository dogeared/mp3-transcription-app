"""Build shields.io endpoint badges from the Maven build output.

Usage: python3 badges.py <out-dir>

Reads surefire XML reports, the JaCoCo CSV report and the version in pom.xml, then writes
tests.json, coverage.json and version.json to <out-dir>. Also writes `tests_failed`,
`coverage` and `version` to $GITHUB_OUTPUT and a summary table to $GITHUB_STEP_SUMMARY.
"""
import csv
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET

POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def test_counts():
    total = failed = skipped = 0
    for path in glob.glob("target/surefire-reports/TEST-*.xml"):
        suite = ET.parse(path).getroot()
        total += int(suite.get("tests", 0))
        failed += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
        skipped += int(suite.get("skipped", 0))
    return total, failed, skipped


def line_coverage():
    path = "target/site/jacoco/jacoco.csv"
    if not os.path.exists(path):
        return None
    missed = covered = 0
    with open(path, newline="") as f:
        for row in csv.DictReader(f):
            missed += int(row["LINE_MISSED"])
            covered += int(row["LINE_COVERED"])
    return 100.0 * covered / (missed + covered) if missed + covered else None


def project_version():
    return ET.parse("pom.xml").getroot().findtext("m:version", namespaces=POM_NS)


def coverage_color(pct):
    if pct is None:
        return "lightgrey"
    for threshold, color in ((80, "brightgreen"), (60, "green"), (40, "yellow"), (20, "orange")):
        if pct >= threshold:
            return color
    return "red"


def badge(label, message, color):
    return {"schemaVersion": 1, "label": label, "message": message, "color": color}


def main(out_dir):
    total, failed, skipped = test_counts()
    passed = total - failed - skipped
    coverage = line_coverage()
    version = project_version()

    if total == 0:
        tests = badge("tests", "no results", "lightgrey")
    elif failed:
        tests = badge("tests", f"{failed} failed, {passed} passed", "red")
    else:
        tests = badge("tests", f"{passed} passed" + (f", {skipped} skipped" if skipped else ""), "brightgreen")

    badges = {
        "tests.json": tests,
        # Color by the rounded value so the badge text and color agree
        "coverage.json": badge("coverage", f"{coverage:.0f}%" if coverage is not None else "unknown",
                               coverage_color(round(coverage) if coverage is not None else None)),
        "version.json": badge("version", version, "blue"),
    }

    os.makedirs(out_dir, exist_ok=True)
    for name, data in badges.items():
        with open(os.path.join(out_dir, name), "w") as f:
            json.dump(data, f)

    if "GITHUB_OUTPUT" in os.environ:
        with open(os.environ["GITHUB_OUTPUT"], "a") as f:
            # No results at all means the build broke before tests ran
            f.write(f"tests_failed={'true' if failed or total == 0 else 'false'}\n")
            f.write(f"coverage={coverage:.1f}\n" if coverage is not None else "coverage=\n")
            f.write(f"version={version}\n")

    if "GITHUB_STEP_SUMMARY" in os.environ:
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as f:
            f.write("| Tests | Line coverage | Version |\n|---|---|---|\n")
            f.write(f"| {tests['message']} | {badges['coverage.json']['message']} | {version} |\n")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "badges")
