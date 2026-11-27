#!/usr/bin/env python3
"""Fail on unreviewed groups, forbidden coordinates, or non-allowlisted licenses."""

import json
from pathlib import Path
import sys
import tomllib

from dependency_licenses import APPROVED_LICENSES, license_for_group

ROOT = Path(__file__).resolve().parents[1]
APPROVED_GROUPS = {
    "androidx.activity",
    "androidx.appcompat",
    "androidx.core",
    "androidx.espresso",
    "androidx.test",
    "androidx.test.espresso",
    "androidx.test.ext",
    "app.cash.sqldelight",
    "com.squareup.okio",
    "com.squareup.zstd",
    "io.ktor",
    "junit",
    "org.jetbrains.androidx.lifecycle",
    "org.jetbrains.compose.components",
    "org.jetbrains.compose.foundation",
    "org.jetbrains.compose.material",
    "org.jetbrains.compose.material3",
    "org.jetbrains.compose.runtime",
    "org.jetbrains.compose.ui",
    "org.jetbrains.kotlin",
    "org.jetbrains.kotlinx",
    "org.openjfx",
}
FORBIDDEN_MARKERS = ("anki", "rslib", "copyleft", "agpl", "gpl")


def main() -> int:
    catalog = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text())
    failures: list[str] = []
    for alias, declaration in catalog.get("libraries", {}).items():
        module = declaration.get("module", "")
        group = module.partition(":")[0]
        lowered = module.lower()
        if group not in APPROVED_GROUPS:
            failures.append(f"{alias}: unreviewed dependency group {group}")
        license_id = license_for_group(group)
        if license_id not in APPROVED_LICENSES:
            failures.append(f"{alias}: unapproved or unknown license for {module}")
        if any(marker in lowered for marker in FORBIDDEN_MARKERS):
            failures.append(f"{alias}: forbidden dependency coordinate {module}")
    for required in (
        "LICENSE",
        "NOTICE",
        "THIRD_PARTY_NOTICES.md",
        "sbom.cdx.json",
    ):
        if not (ROOT / required).is_file():
            failures.append(f"missing {required}")
    bundled_legal_root = ROOT / "shared/src/commonMain/composeResources/files/legal"
    bundled_notices = {
        bundled_legal_root / "LICENSE.txt": ROOT / "LICENSE",
        bundled_legal_root / "NOTICE.txt": ROOT / "NOTICE",
        bundled_legal_root / "THIRD_PARTY_NOTICES.md": ROOT / "THIRD_PARTY_NOTICES.md",
    }
    for bundled, source in bundled_notices.items():
        if not bundled.is_file():
            failures.append(f"missing bundled legal notice {bundled.relative_to(ROOT)}")
        elif bundled.read_bytes() != source.read_bytes():
            failures.append(f"bundled legal notice differs from {source.relative_to(ROOT)}")
    sbom_path = ROOT / "sbom.cdx.json"
    if sbom_path.is_file():
        sbom = json.loads(sbom_path.read_text())
        root_licenses = {
            entry.get("license", {}).get("id")
            for entry in sbom.get("metadata", {}).get("component", {}).get("licenses", [])
        }
        if root_licenses != {"Apache-2.0"}:
            failures.append("SBOM root component must declare Apache-2.0")
        sbom_components = sbom.get("components", [])
        for component in sbom_components:
            identities = {
                entry.get("license", {}).get("id")
                for entry in component.get("licenses", [])
            }
            if not identities or not identities.issubset(APPROVED_LICENSES):
                coordinate = f"{component.get('group')}:{component.get('name')}:{component.get('version')}"
                failures.append(f"SBOM component has unapproved or unknown license: {coordinate}")
    if failures:
        print("\n".join(failures), file=sys.stderr)
        return 1
    print("Dependency policy passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
