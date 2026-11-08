#!/usr/bin/env python3
"""Check committed versions, stage verified assets, and publish an existing tag."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
from urllib.parse import quote
import zipfile

NUMBER = r"(?:0|[1-9][0-9]*)"
IDENTIFIER = r"(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)"
SEMVER = re.compile(NUMBER + r"\." + NUMBER + r"\." + NUMBER
                    + r"(?:-" + IDENTIFIER + r"(?:\." + IDENTIFIER + r")*)?"
                    + r"(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?")


def normalize_tag(tag):
    version = tag[1:] if tag.startswith("v") else tag
    if not SEMVER.fullmatch(version):
        raise ValueError("Expected a version such as v1.2.3, 1.2.3, or v1.2.3-rc.1")
    return version


def is_prerelease(version):
    return "-" in version.split("+", 1)[0]


def single_match(pattern, text, label):
    matches = re.findall(pattern, text, re.MULTILINE)
    if len(matches) != 1:
        raise ValueError("Expected exactly one " + label)
    return matches[0]


def check_version(root, tag=None):
    # Read the two deliberately simple version assignments without adding a TOML
    # dependency to the Python 3.9 launcher toolchain. Limit matching to this section.
    config = (root / ".cz.toml").read_text()
    section = single_match(r"^\[tool\.commitizen\]\s*\n([\s\S]*?)(?=^\[|\Z)",
                           config, "Commitizen section")
    version = single_match(r'^version\s*=\s*"([^"\n]+)"\s*$', section, "Commitizen version")
    if not SEMVER.fullmatch(version):
        raise ValueError("Commitizen version must be an unprefixed semantic version")
    gradle = single_match(r"^mod_version=([^\s]+)$", (root / "gradle.properties").read_text(),
                          "Gradle mod_version")
    if version != gradle:
        raise ValueError("Commitizen and Gradle versions disagree: " + version + " != " + gradle)
    if tag is not None and normalize_tag(tag) != version:
        raise ValueError("Tag does not match the committed version " + version)
    return version


def check_jar_version(jar, version):
    with zipfile.ZipFile(jar) as archive:
        metadata = archive.read("META-INF/neoforge.mods.toml").decode("utf-8")
        mod = single_match(r"^\[\[mods\]\]\s*\n([\s\S]*?)(?=^\[|\Z)", metadata, "mod entry")
        embedded = single_match(r'^version\s*=\s*"([^"\n]+)"\s*$', mod, "embedded mod version")
        manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8").replace("\r\n", "\n")
        manifest = manifest.replace("\n ", "")  # Unfold JAR manifest continuation lines.
        implementation = single_match(r"^Implementation-Version: (.+)$", manifest, "manifest version")
    if embedded != version or implementation != version:
        raise ValueError("JAR metadata and manifest must match version " + version)


def checked_artifacts(root):
    version = check_version(root)
    descriptor = json.loads((root / "build/runtime-artifact.json").read_text())
    if descriptor.get("version") != version:
        raise ValueError("Artifact descriptor version is stale; rebuild the artifacts")
    artifacts = {}
    for key, suffix in (("jar", ".jar"), ("sources", "-sources.jar"), ("project", "-project.zip")):
        expected = "build/libs/modularcraft-" + version + suffix
        if descriptor.get(key) != expected:
            raise ValueError("Unexpected " + key + " artifact path")
        path = root / expected
        if not path.is_file() or path.is_symlink() or not path.stat().st_size:
            raise ValueError("Missing or invalid artifact: " + expected)
        artifacts[key] = path
    check_jar_version(artifacts["jar"], version)
    return artifacts


def checksums(paths):
    return "".join(hashlib.sha256(p.read_bytes()).hexdigest() + "  " + p.name + "\n"
                   for p in sorted(paths, key=lambda p: p.name))


def stage(root, tag):
    check_version(root, tag)
    artifacts = checked_artifacts(root)
    paths = [artifacts["jar"], artifacts["sources"]]
    directory = root / "build/release"
    if directory.exists():
        raise ValueError("Release staging directory already exists; use a clean build/release directory")
    directory.mkdir(parents=True)
    for path in paths:
        shutil.copyfile(path, directory / path.name)
    (directory / "SHA256SUMS").write_text(checksums(directory.iterdir()))
    return directory


def publish(root, tag, repository, directory, runner=subprocess.run):
    version = check_version(root, tag)
    jar = directory / ("modularcraft-" + version + ".jar")
    paths = [jar, directory / ("modularcraft-" + version + "-sources.jar")]
    checksum_file = directory / "SHA256SUMS"
    expected = {p.name for p in paths} | {"SHA256SUMS"}
    if {p.name for p in directory.iterdir()} != expected:
        raise ValueError("Release asset set does not match the expected files")
    if any(not p.is_file() or p.is_symlink() for p in [*paths, checksum_file]):
        raise ValueError("Release assets must be regular files")
    if checksum_file.read_text() != checksums(paths):
        raise ValueError("Release asset checksums do not match")
    check_jar_version(jar, version)
    base = "https://github.com/" + repository + "/blob/" + quote(tag, safe="") + "/"
    notes = ("Built from tag `" + tag + "` after automated verification.\n\n"
             "- [Supported versions and installation](" + base + "README.md)\n"
             "- [Changelog and migration notes](" + base + "CHANGELOG.md)\n"
             "- [License](" + base + "LICENSE)\n"
             "- [Third-party notices](" + base + "THIRD_PARTY_NOTICES.md)\n"
             "- [Dependency inventory](" + base + "validation/dependencies.json)\n"
             "- [License inventory](" + base + "validation/licenses.json)\n"
             "- [Acceptance procedures](" + base + "docs/acceptance.md)\n"
             "- [Validation results and outstanding gates](" + base + "validation/ledger.md)\n\n"
             "Automated verification does not establish native, listening, multiplayer, "
             "or security acceptance; see the validation records for their status.\n\n"
             "### SHA-256 checksums\n\n```text\n" + checksum_file.read_text() + "```\n")
    args = ["gh", "release", "create", tag, *(str(p) for p in paths),
            "--repo", repository, "--verify-tag", "--title", "ModularCraft " + version,
            "--generate-notes", "--notes", notes]
    if is_prerelease(version):
        args.append("--prerelease")
    # gh fails on an existing release; never fall back to upload --clobber or edit.
    runner(args, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("check", "stage", "publish"))
    parser.add_argument("--tag")
    parser.add_argument("--repository")
    parser.add_argument("--directory", type=Path, default=Path("build/release"))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if args.command != "check" and not args.tag:
        parser.error("--tag is required for staging or publishing")
    if args.command == "publish" and not args.repository:
        parser.error("--repository is required for publishing")
    try:
        if args.command == "check":
            print(check_version(root, args.tag))
        elif args.command == "stage":
            print(stage(root, args.tag))
        else:
            publish(root, args.tag, args.repository, args.directory)
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, "Release validation failed: " + str(error) + "\n")


if __name__ == "__main__":
    main()
