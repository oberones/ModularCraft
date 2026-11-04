#!/usr/bin/env python3
"""Launch the built JAR, preserving manual worlds and using fresh automated fixtures.

Python 3.9+ on macOS/POSIX. The per-instance advisory lock covers staging and the child
process. No shell interpolation, save deletion, or automatic EULA acceptance is used.
"""
import argparse
from contextlib import contextmanager
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import tempfile
import zipfile

MARKER = ".modularcraft-validation.json"
MODES = ("client", "server", "gametest", "smoke", "smoke-load", "smoke-recovery")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def checked_directory(project, directory):
    project = Path(project).resolve()
    directory = Path(directory)
    if not directory.is_absolute():
        directory = project / directory
    if ".." in directory.parts:
        raise ValueError("Validation paths must not contain '..'")
    root = project / "run"
    canonical = directory.resolve()
    if canonical == root or root not in canonical.parents:
        raise ValueError("Choose an isolated validation directory below this project's run/ folder")
    for part in (directory, *directory.parents):
        if part.resolve() == project:
            break
        if part.is_symlink():
            raise ValueError("Validation directories must not traverse symlinks: " + str(part))
    return canonical


@contextmanager
def instance_lock(project, directory):
    directory = checked_directory(project, directory)
    directory.mkdir(parents=True, exist_ok=True)
    lock = directory / ".launch.lock"
    if lock.is_symlink():
        raise ValueError("Refusing a symlinked launch lock")
    with lock.open("a") as handle:
        try:
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise ValueError("This validation instance is already running: " + str(directory)) from error
        try:
            yield
        finally:
            fcntl.flock(handle, fcntl.LOCK_UN)


def stage(project, run_dir, jar, kind, fresh=False):
    run_dir = checked_directory(project, run_dir)
    jar = Path(jar)
    with zipfile.ZipFile(jar) as archive:
        if "com/malevolentgods/modularcraft/ModularCraft.class" not in archive.namelist():
            raise ValueError("Expected the installable runtime JAR, not sources or a project ZIP")
        if not re.search(rb'\bmodId\s*=\s*"modularcraft"', archive.read("META-INF/neoforge.mods.toml")):
            raise ValueError("Artifact does not identify ModularCraft")
    run_dir.mkdir(parents=True, exist_ok=True)
    marker = run_dir / MARKER
    if marker.is_symlink():
        raise ValueError("Refusing a symlinked instance marker")
    previous = json.loads(marker.read_text()) if marker.exists() else None
    contents = [p for p in run_dir.iterdir() if p.name != ".launch.lock"]
    if fresh and contents:
        raise ValueError("Automated fixtures require a fresh directory; omit --run-dir to create one")
    if previous is None and contents:
        raise ValueError("Refusing to adopt a nonempty unmanaged instance: " + str(run_dir))
    if previous and (previous.get("schema") != 1 or previous.get("kind") != kind):
        raise ValueError("Instance belongs to another validation mode or schema")
    mods = checked_directory(project, run_dir / "mods")
    mods.mkdir(exist_ok=True)
    target = mods / jar.name
    old = None
    if previous:
        name = previous.get("jar", "")
        if not name or Path(name).name != name or not name.endswith(".jar"):
            raise ValueError("Invalid managed artifact name")
        old = mods / name
        if old.is_symlink() or (old.exists() and digest(old) != previous.get("sha256")):
            raise ValueError("Managed JAR was changed outside this launcher; preserve or move it before retrying")
    if target.is_symlink() or (target.exists() and target != old):
        raise ValueError("Refusing to overwrite an unmanaged artifact: " + str(target))
    for candidate in mods.glob("modularcraft-*.jar"):
        if candidate != old:
            raise ValueError("Move the additional ModularCraft JAR before launching: " + str(candidate))
    # Replace only the launcher-owned artifact; all saves/configuration/other mods survive.
    with tempfile.NamedTemporaryFile(dir=mods, prefix=".staging-", delete=False) as temporary:
        pending = Path(temporary.name)
        try:
            with jar.open("rb") as source:
                shutil.copyfileobj(source, temporary)
            temporary.flush()
            os.fsync(temporary.fileno())
            os.replace(pending, target)
        finally:
            pending.unlink(missing_ok=True)
    if old and old != target:
        old.unlink(missing_ok=True)
    record = {"schema": 1, "kind": kind, "jar": jar.name, "sha256": digest(target)}
    marker.write_text(json.dumps(record, indent=2) + "\n")
    print("Staged " + jar.name + " SHA-256 " + record["sha256"], flush=True)
    return run_dir


def launch(project, mode, run_dir=None, ticks=1200, gradle="./gradlew", gradle_args=(), runner=subprocess.run):
    project = Path(project).resolve()
    if mode not in MODES:
        raise ValueError("Unknown validation mode: " + mode)
    automated = mode.startswith("smoke")
    if automated and not (400 if mode == "smoke-recovery" else 40) <= ticks <= 2147483647:
        raise ValueError("QA ticks must be 40..2147483647 (at least 400 for all recovery cycles)")
    descriptor = project / "build" / "runtime-artifact.json"
    if not descriptor.is_file():
        raise ValueError("Build the current runtime first with 'make jar'")
    jar = (project / json.loads(descriptor.read_text())["jar"]).resolve()
    if project / "build" / "libs" not in jar.parents:
        raise ValueError("The runtime descriptor must name an artifact under build/libs/")
    if run_dir is None:
        base = checked_directory(project, project / "run" / "make")
        base.mkdir(parents=True, exist_ok=True)
        run_dir = Path(tempfile.mkdtemp(prefix=mode + "-", dir=base)) if automated else base / mode
    run_dir = checked_directory(project, run_dir)
    with instance_lock(project, run_dir):
        stage(project, run_dir, jar, mode, fresh=automated)
        print("Validation instance: " + str(run_dir), flush=True)
        args = [gradle, *gradle_args, "--dependency-verification", "strict", "-Ppackaged"]
        if automated:
            seed_dir = project / "run" / "make" / "gametest"
            seed = seed_dir / "world" / "level.dat"
            if not seed.is_file() or not (seed_dir / MARKER).is_file():
                launch(project, "gametest", seed_dir, gradle=gradle, gradle_args=gradle_args, runner=runner)
            checked_directory(project, seed.parent)
            if seed.is_symlink() or not seed.is_file():
                raise ValueError("GameTest server did not produce a usable world/level.dat")
            world = run_dir / "saves" / "ModularCraft-QA"
            world.mkdir(parents=True)
            shutil.copyfile(seed, world / "level.dat")
            (run_dir / "options.txt").write_text(
                "onboardAccessibility:false\npauseOnLostFocus:false\ntutorialStep:none\n"
                "enableVsync:false\nrenderDistance:12\nsimulationDistance:8\n"
                "guiScale:2\nfullscreen:false\nsoundCategory_master:1.0\nsoundCategory_records:1.0\n"
            )
            args += ["-Pqa", "-PqaTicks=" + str(ticks),
                     "-PqaLoad=" + str(mode == "smoke-load").lower(),
                     "-PqaCycles=" + str(mode == "smoke-recovery").lower()]
        elif mode == "client" and not (run_dir / "options.txt").exists():
            (run_dir / "options.txt").write_text("onboardAccessibility:false\n")
        task, property_name = {
            "server": ("runServer", "serverRunDir"),
            "gametest": ("runGameTestServer", "gameTestRunDir"),
        }.get(mode, ("runClient", "clientRunDir"))
        args += ["-P" + property_name + "=" + str(run_dir), task]
        runner(args, cwd=project, check=True)
    return run_dir


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=MODES)
    parser.add_argument("--run-dir", help="Managed instance below run/; smoke modes require an empty directory")
    parser.add_argument("--ticks", type=int, default=1200, help="Automatic fixture duration in game ticks")
    args = parser.parse_args()
    try:
        launch(Path(__file__).resolve().parents[1], args.mode, args.run_dir, args.ticks,
               os.environ.get("GRADLEW", "./gradlew"), shlex.split(os.environ.get("GRADLE_ARGS", "")))
    except subprocess.CalledProcessError as error:
        return error.returncode
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(2, "Validation launch failed: " + str(error) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
