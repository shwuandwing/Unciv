import sys

from tools.earthgen import generate_unciv_earth_map


def test_generate_cli_defaults_include_topology_pole_alignment(monkeypatch):
    monkeypatch.setattr(
        sys,
        "argv",
        ["generate_unciv_earth_map.py", "--output", "android/assets/maps/Earth-Icosa-Test"],
    )
    args = generate_unciv_earth_map.parse_args()
    assert args.pole_alignment == "topology"
    assert args.flip_latitude is True
    assert args.flip_longitude is False
    assert args.enable_resources is True
    assert args.resource_density == "default"
    assert args.resource_seed == 1337


def test_generate_cli_accepts_disable_resources_flag(monkeypatch):
    monkeypatch.setattr(
        sys,
        "argv",
        [
            "generate_unciv_earth_map.py",
            "--output",
            "android/assets/maps/Earth-Icosa-Test",
            "--disable-resources",
        ],
    )
    args = generate_unciv_earth_map.parse_args()
    assert args.enable_resources is False
