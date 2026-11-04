"""Filesystem and command-boundary regressions; no Minecraft process is launched."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

SPEC = importlib.util.spec_from_file_location("run_validation", Path(__file__).parents[1] / "run_validation.py")
validation = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validation)


class ValidationRunTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="modularcraft launcher ")
        self.addCleanup(self.temp.cleanup)
        self.project = Path(self.temp.name).resolve()
        self.jar = self.make_jar("modularcraft-0.1.0.jar")
        self.run_dir = self.project / "run" / "make" / "client with spaces"
        (self.project / "build" / "runtime-artifact.json").write_text(json.dumps({"jar": str(self.jar.relative_to(self.project))}))

    def make_jar(self, name):
        jar = self.project / "build" / "libs" / name
        jar.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr("META-INF/neoforge.mods.toml", '[[mods]]\nmodId="modularcraft"\n')
            archive.writestr("com/malevolentgods/modularcraft/ModularCraft.class", name)
        return jar

    def test_stages_exact_runtime_jar_and_preserves_world_on_upgrade(self):
        validation.stage(self.project, self.run_dir, self.jar, "client")
        installed = self.run_dir / "mods" / self.jar.name
        self.assertTrue(installed.is_file(), "The packaged runtime must be staged")
        self.assertEqual(self.jar.read_bytes(), installed.read_bytes())
        world = self.run_dir / "saves" / "My instrument" / "level.dat"
        world.parent.mkdir(parents=True)
        world.write_bytes(b"personal composition")
        other = self.run_dir / "mods" / "another-mod.jar"
        other.write_bytes(b"keep")
        upgraded = self.make_jar("modularcraft-0.2.0.jar")
        validation.stage(self.project, self.run_dir, upgraded, "client")
        self.assertFalse(installed.exists(), "Only the old managed mod is replaced")
        self.assertEqual(world.read_bytes(), b"personal composition")
        self.assertEqual(other.read_bytes(), b"keep")
        self.assertTrue((self.run_dir / "mods" / upgraded.name).is_file())

    def test_refuses_unmanaged_nonempty_directory(self):
        self.run_dir.mkdir(parents=True)
        world = self.run_dir / "level.dat"
        world.write_bytes(b"untouched")
        with self.assertRaises(ValueError):
            validation.stage(self.project, self.run_dir, self.jar, "client")
        self.assertEqual(world.read_bytes(), b"untouched")

    def test_refuses_outside_run_root_and_symlinks(self):
        for target in (self.project / "personal", self.project / "run" / ".." / "personal"):
            with self.assertRaises(ValueError):
                validation.stage(self.project, target, self.jar, "client")
        (self.project / "run").mkdir(exist_ok=True)
        (self.project / "personal").mkdir(exist_ok=True)
        (self.project / "run" / "linked").symlink_to(self.project / "personal", target_is_directory=True)
        with self.assertRaises(ValueError):
            validation.stage(self.project, self.project / "run" / "linked", self.jar, "client")

    def test_fresh_fixture_never_reuses_a_world(self):
        validation.stage(self.project, self.run_dir, self.jar, "smoke", fresh=True)
        with self.assertRaises(ValueError):
            validation.stage(self.project, self.run_dir, self.jar, "smoke", fresh=True)

    def test_refuses_modified_managed_jar_instead_of_overwriting_it(self):
        validation.stage(self.project, self.run_dir, self.jar, "client")
        installed = self.run_dir / "mods" / self.jar.name
        self.assertTrue(installed.is_file(), "The runtime must be staged before this regression")
        installed.write_bytes(b"user replacement")
        with self.assertRaises(ValueError):
            validation.stage(self.project, self.run_dir, self.make_jar("modularcraft-0.2.0.jar"), "client")
        self.assertEqual(installed.read_bytes(), b"user replacement")

    def test_launch_uses_packaged_mode_and_preserves_space_in_path(self):
        calls = []
        validation.launch(self.project, "client", self.run_dir, runner=lambda argv, **kw: calls.append(argv))
        self.assertEqual(len(calls), 1, "Exactly one client launch is expected")
        self.assertIn("runClient", calls[0])
        self.assertIn("-Ppackaged", calls[0])
        self.assertIn("-PclientRunDir=" + str(self.run_dir), calls[0])
        self.assertNotIn("-Pqa", calls[0], "Manual play must not install a fixture")

    def test_smoke_bootstraps_seed_then_launches_a_fresh_fixture(self):
        calls = []

        def runner(argv, **kwargs):
            calls.append(argv)
            if "runGameTestServer" in argv:
                directory = Path(next(a.split("=", 1)[1] for a in argv if a.startswith("-PgameTestRunDir=")))
                seed = directory / "world" / "level.dat"
                seed.parent.mkdir(parents=True, exist_ok=True)
                seed.write_bytes(b"test world seed")

        validation.launch(self.project, "smoke-load", self.run_dir, ticks=400, runner=runner)
        self.assertEqual(len(calls), 2, "Missing world seed must be bootstrapped before native play")
        self.assertIn("runGameTestServer", calls[0])
        self.assertIn("-PqaLoad=true", calls[1])
        self.assertIn("-PqaTicks=400", calls[1])
        self.assertIn("-Pqa", calls[1])
        self.assertEqual((self.run_dir / "saves/ModularCraft-QA/level.dat").read_bytes(), b"test world seed")
        self.assertIn("onboardAccessibility:false", (self.run_dir / "options.txt").read_text())

    def test_failed_seed_server_never_launches_client(self):
        calls = []

        def runner(argv, **kwargs):
            calls.append(argv)
            raise subprocess.CalledProcessError(7, argv)

        with self.assertRaises(subprocess.CalledProcessError):
            validation.launch(self.project, "smoke", self.run_dir, runner=runner)
        self.assertEqual(len(calls), 1)
        self.assertIn("runGameTestServer", calls[0])

    def test_same_instance_cannot_be_launched_concurrently(self):
        with validation.instance_lock(self.project, self.run_dir):
            with self.assertRaises(ValueError):
                with validation.instance_lock(self.project, self.run_dir):
                    self.fail("The second launcher must not acquire the same instance")
        with validation.instance_lock(self.project, self.run_dir):
            pass  # The first owner's release allows the next launch.

    def test_server_launch_does_not_accept_or_replace_eula(self):
        calls = []
        validation.launch(self.project, "server", self.run_dir, runner=lambda argv, **kw: calls.append(argv))
        self.assertIn("runServer", calls[0])
        self.assertIn("-PserverRunDir=" + str(self.run_dir), calls[0])
        eula = self.run_dir / "eula.txt"
        self.assertFalse(eula.exists())
        eula.write_text("eula=false\n")
        validation.launch(self.project, "server", self.run_dir, runner=lambda argv, **kw: None)
        self.assertEqual(eula.read_text(), "eula=false\n")

    def test_bad_artifact_and_seed_symlink_are_refused(self):
        source = self.make_jar("modularcraft-sources.jar")
        with zipfile.ZipFile(source, "w") as archive:
            archive.writestr("ModularCraft.java", "source only")
        with self.assertRaises(ValueError):
            validation.stage(self.project, self.run_dir, source, "client")
        seed_dir = self.project / "run/make/gametest"
        validation.stage(self.project, seed_dir, self.jar, "gametest")
        seed = seed_dir / "world/level.dat"
        seed.parent.mkdir()
        seed.symlink_to(self.jar)
        calls = []
        with self.assertRaises(ValueError):
            validation.launch(self.project, "smoke", self.run_dir, runner=lambda argv, **kw: calls.append(argv))
        self.assertEqual(calls, [])

    def test_ci_keeps_no_daemon_when_user_supplies_gradle_flags(self):
        result = subprocess.run(
            ["make", "-n", "ci", "GRADLE_ARGS=--offline"],
            cwd=Path(__file__).resolve().parents[2],
            check=True, capture_output=True, text=True,
        )
        self.assertIn("--offline", result.stdout)
        self.assertIn("--no-daemon", result.stdout, "CI must retain no-daemon mode with flag overrides")


if __name__ == "__main__":
    unittest.main()
