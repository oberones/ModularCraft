"""Exercise the actual release bump in an isolated Git repository."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(shutil.which("cz"), "Commitizen is an optional local release tool")
class CommitizenTest(unittest.TestCase):
    def test_stable_bump_updates_gradle_changelog_commit_and_tag(self):
        self.check_bump(())

    def test_prerelease_bump_updates_gradle_changelog_commit_and_tag(self):
        self.check_bump(("--prerelease", "rc"))

    def check_bump(self, options):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in (".cz.toml", "gradle.properties", "build.gradle", "CHANGELOG.md"):
                shutil.copyfile(ROOT / name, root / name)

            def run(*args):
                result = subprocess.run(args, cwd=root, text=True,
                                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
                self.assertEqual(0, result.returncode, result.stdout)
                return result.stdout

            run("git", "init", "-q")
            run("git", "config", "user.name", "Release Test")
            run("git", "config", "user.email", "release-test@example.invalid")
            run("git", "config", "commit.gpgsign", "false")
            run("git", "config", "tag.gpgsign", "false")
            run("git", "add", ".")
            run("git", "commit", "-qm", "feat: isolated release fixture")
            run("git", "tag", "v" + run("cz", "version", "--project").strip())
            run("git", "commit", "--allow-empty", "-qm", "fix: release test change")
            run("cz", "bump", "--increment", "PATCH", *options,
                "--check-consistency", "--yes")
            version = run("cz", "version", "--project").strip()
            self.assertIn('version = "' + version + '"', (root / ".cz.toml").read_text())
            self.assertIn("mod_version=" + version, (root / "gradle.properties").read_text())
            self.assertIn(version, (root / "CHANGELOG.md").read_text())
            self.assertEqual(run("git", "rev-parse", "HEAD"),
                             run("git", "rev-parse", "v" + version))
            self.assertEqual("", run("git", "status", "--porcelain"))


if __name__ == "__main__":
    unittest.main()
