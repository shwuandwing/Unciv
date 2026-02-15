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
