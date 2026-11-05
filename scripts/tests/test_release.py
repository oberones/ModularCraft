"""Release version, artifact selection, and publication boundary tests."""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock
import zipfile

SPEC = importlib.util.spec_from_file_location("release", Path(__file__).resolve().parents[1] / "release.py")
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.version = "1.2.3-rc.1+build.7"
        (self.root / ".cz.toml").write_text('[tool.commitizen]\nversion = "' + self.version + '"\n')
        (self.root / "gradle.properties").write_text("mod_version=" + self.version + "\n")
        (self.root / "build/libs").mkdir(parents=True)
        self.descriptor = {"version": self.version}
        for key, suffix in (("jar", ".jar"), ("sources", "-sources.jar"), ("project", "-project.zip")):
            name = "build/libs/modularcraft-" + self.version + suffix
            self.descriptor[key] = name
            (self.root / name).write_bytes(b"fixture")
        self.write_descriptor()
        self.jar(self.version)
        for name in release.SUPPORTING_FILES:
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("supporting record\n")

    def write_descriptor(self):
        (self.root / "build/runtime-artifact.json").write_text(json.dumps(self.descriptor))

    def jar(self, metadata_version, manifest_version=None):
        with zipfile.ZipFile(self.root / self.descriptor["jar"], "w") as archive:
            archive.writestr("META-INF/neoforge.mods.toml",
                             '[[mods]]\nmodId="modularcraft"\nversion="' + metadata_version + '"\n')
            archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nImplementation-Version: "
                             + (manifest_version or metadata_version) + "\r\n\r\n")

    def test_semantic_tags_and_prereleases(self):
        for tag in ("1.2.3", "v1.2.3", "v0.0.0-rc.1+sha.42", "1.2.3+build-with-dash"):
            self.assertEqual(tag.removeprefix("v"), release.normalize_tag(tag))
        for tag in ("v1.2", "v01.2.3", "v1.2.3-01", "vv1.2.3", "v1.2.3\n", "v1.2.3;true", "v1.2.3-"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release.normalize_tag(tag)
        self.assertTrue(release.is_prerelease(self.version))
        self.assertFalse(release.is_prerelease("1.2.3+build-with-dash"))

    def test_repository_and_tag_must_agree(self):
        self.assertEqual(self.version, release.check_version(self.root, "v" + self.version))
        with self.assertRaises(ValueError):
            release.check_version(self.root, "v9.0.0")
        (self.root / "gradle.properties").write_text("mod_version=1.2.4\n")
        with self.assertRaises(ValueError):
            release.check_version(self.root)

    def test_exact_artifacts_and_embedded_versions(self):
        (self.root / "build/libs/modularcraft-99.0.0.jar").write_bytes(b"stale")
        self.assertEqual(self.root / self.descriptor["jar"], release.checked_artifacts(self.root)["jar"])
        for metadata, manifest in (("0.1.0", self.version), (self.version, "0.1.0")):
            self.jar(metadata, manifest)
            with self.assertRaises(ValueError):
                release.checked_artifacts(self.root)

    def test_missing_descriptor_artifacts_fail(self):
        (self.root / self.descriptor["sources"]).unlink()
        with self.assertRaises(ValueError):
            release.checked_artifacts(self.root)

    def test_wrong_descriptor_path_fails(self):
        self.descriptor["jar"] = "../outside.jar"
        self.write_descriptor()
        with self.assertRaises(ValueError):
            release.checked_artifacts(self.root)

    def test_stale_descriptor_version_fails(self):
        self.descriptor["version"] = "0.1.0"
        self.write_descriptor()
        with self.assertRaises(ValueError):
            release.checked_artifacts(self.root)

    def test_stage_contains_only_selected_assets_and_complete_checksums(self):
        directory = release.stage(self.root, "v" + self.version)
        expected = {Path(p).name for p in release.SUPPORTING_FILES}
        expected.add(Path(self.descriptor["jar"]).name)
        self.assertEqual(expected | {"SHA256SUMS"}, {p.name for p in directory.iterdir()})
        for line in (directory / "SHA256SUMS").read_text().splitlines():
            digest, name = line.split("  ")
            self.assertEqual(digest, hashlib.sha256((directory / name).read_bytes()).hexdigest())
        self.assertEqual(len(expected), len((directory / "SHA256SUMS").read_text().splitlines()))
        with self.assertRaises(ValueError):
            release.stage(self.root, "v" + self.version)

    def test_missing_supporting_record_fails_before_staging(self):
        (self.root / release.SUPPORTING_FILES[0]).unlink()
        with self.assertRaises(ValueError):
            release.stage(self.root, "v" + self.version)
        self.assertFalse((self.root / "build/release").exists())

    def test_publish_uses_verified_tag_and_propagates_existing_release_failure(self):
        directory = release.stage(self.root, "v" + self.version)
        runner = Mock()
        release.publish(self.root, "v" + self.version, "owner/repo", directory, runner=runner)
        args = runner.call_args.args[0]
        self.assertEqual(["gh", "release", "create", "v" + self.version], args[:4])
        for flag in ("--verify-tag", "--generate-notes", "--prerelease"):
            self.assertIn(flag, args)
        self.assertIn("ModularCraft " + self.version, args)
        self.assertIn("https://github.com/owner/repo/blob/v1.2.3-rc.1%2Bbuild.7/docs/acceptance.md",
                      args[args.index("--notes") + 1])
        runner.side_effect = subprocess.CalledProcessError(1, args, stderr="release already exists")
        with self.assertRaises(subprocess.CalledProcessError):
            release.publish(self.root, "v" + self.version, "owner/repo", directory, runner=runner)
        self.assertEqual(2, runner.call_count)  # No fallback upload, edit, or clobber.

    def test_publish_rejects_tampered_assets(self):
        directory = release.stage(self.root, self.version)
        (directory / "README.md").write_text("tampered")
        runner = Mock()
        with self.assertRaises(ValueError):
            release.publish(self.root, self.version, "owner/repo", directory, runner=runner)
        runner.assert_not_called()

    def test_stable_publish_is_not_marked_prerelease(self):
        version = "1.2.3+build-with-dash"
        for name in (".cz.toml", "gradle.properties"):
            path = self.root / name
            path.write_text(path.read_text().replace(self.version, version))
        for key in ("jar", "sources", "project"):
            old = self.descriptor[key]
            self.descriptor[key] = old.replace(self.version, version)
            (self.root / old).rename(self.root / self.descriptor[key])
        self.version = version
        self.descriptor["version"] = version
        self.write_descriptor()
        self.jar(version)
        directory = release.stage(self.root, version)
        runner = Mock()
        release.publish(self.root, version, "owner/repo", directory, runner=runner)
        self.assertNotIn("--prerelease", runner.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
