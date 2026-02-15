from __future__ import annotations

import subprocess

import pytest

from tools.earthgen import generate_unciv_earth_map


def test_ensure_topology_dump_is_noop_when_file_exists(tmp_path, monkeypatch) -> None:
    topology_path = tmp_path / "topology_f31.json"
    topology_path.write_text("{}", encoding="utf-8")

    called = {"run": False}

    def fake_run(*args, **kwargs):  # noqa: ANN002, ANN003
        called["run"] = True
        return None

    monkeypatch.setattr(generate_unciv_earth_map.subprocess, "run", fake_run)
    generate_unciv_earth_map.ensure_topology_dump(topology_path=topology_path, frequency=31, auto_generate=True)
    assert called["run"] is False


def test_ensure_topology_dump_raises_when_missing_and_auto_disabled(tmp_path) -> None:
    topology_path = tmp_path / "missing" / "topology_f31.json"
    with pytest.raises(FileNotFoundError):
        generate_unciv_earth_map.ensure_topology_dump(topology_path=topology_path, frequency=31, auto_generate=False)


def test_ensure_topology_dump_runs_gradle_when_missing(tmp_path, monkeypatch) -> None:
    repo_root = tmp_path / "repo"
    repo_root.mkdir(parents=True, exist_ok=True)
    gradlew = repo_root / "gradlew"
    gradlew.write_text("#!/usr/bin/env bash\n", encoding="utf-8")

    topology_path = tmp_path / "generated" / "topology_f31.json"
    called: dict[str, object] = {}

    def fake_run(cmd: list[str], cwd: str, env: dict[str, str], check: bool):  # noqa: ANN201
        called["cmd"] = cmd
        called["cwd"] = cwd
        called["check"] = check
        called["has_path_env"] = "PATH" in env
        topology_path.write_text("{}", encoding="utf-8")
        return subprocess.CompletedProcess(args=cmd, returncode=0)

    monkeypatch.setattr(generate_unciv_earth_map, "REPO_ROOT", repo_root)
    monkeypatch.setattr(generate_unciv_earth_map.subprocess, "run", fake_run)

    generate_unciv_earth_map.ensure_topology_dump(topology_path=topology_path, frequency=31, auto_generate=True)

    expected_args = f"--args=--dump-icosa-topology={topology_path.resolve()} --frequency=31"
    assert called["cmd"] == [str(gradlew), "-q", ":desktop:run", expected_args]
    assert called["cwd"] == str(repo_root)
    assert called["check"] is True
    assert called["has_path_env"] is True
    assert topology_path.exists()
