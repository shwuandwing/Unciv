from __future__ import annotations

import unittest

from tools.earthgen.generate_unciv_earth_map import TileClassification, build_map_payload
from tools.earthgen.river_projection import _NeighborCycle, canonical_edge, project_river_lines_to_edges
from tools.earthgen.topology_io import RiverWriter, TopologyDump, TopologyEdge, TopologyTile


class RiverProjectionTests(unittest.TestCase):
    def build_chain_topology(self) -> TopologyDump:
        tiles = (
            TopologyTile(0, 0, 0, 0.0, 0.0, (1,)),
            TopologyTile(1, 1, 0, 0.0, 5.0, (0, 2)),
            TopologyTile(2, 2, 0, 0.0, 10.0, (1, 3)),
            TopologyTile(3, 3, 0, 0.0, 15.0, (2, 4)),
            TopologyTile(4, 4, 0, 0.0, 20.0, (3,)),
        )
        edges = (
            TopologyEdge(0, 1, True, RiverWriter(0, "hasBottomRiver")),
            TopologyEdge(1, 2, True, RiverWriter(1, "hasBottomRiver")),
            TopologyEdge(2, 3, True, RiverWriter(2, "hasBottomRiver")),
            TopologyEdge(3, 4, True, RiverWriter(3, "hasBottomRiver")),
        )
        return TopologyDump(
            frequency=1,
            layout_id="IcosaNetV2",
            tile_count=5,
            ruleset="Civ V - Gods & Kings",
            tiles=tiles,
            edges=edges,
            map_parameters_template={"shape": "Icosahedron", "goldbergFrequency": 1},
        )

    def test_projection_edges_are_neighbor_pairs_and_deduplicated(self) -> None:
        topology = self.build_chain_topology()
        lines = [
            [(0.2, 0.0), (6.0, 0.0), (12.0, 0.0), (19.8, 0.0)],
            [(10.1, 0.0), (15.2, 0.0)],
        ]

        result = project_river_lines_to_edges(topology, lines, max_rivers=2)

        self.assertGreaterEqual(len(result.edges), 4)
        self.assertEqual(len(set(result.edges)), len(result.edges))

        neighbor_pairs = {canonical_edge(edge.a, edge.b) for edge in topology.edges}
        for edge in result.edges:
            self.assertIn(edge, neighbor_pairs)

        for chain in result.chains:
            for a, b in chain:
                self.assertIn(canonical_edge(a, b), neighbor_pairs)
            for (a1, b1), (a2, b2) in zip(chain, chain[1:]):
                self.assertTrue(len({a1, b1} & {a2, b2}) > 0)

    def test_chains_are_corner_continuous_after_projection(self) -> None:
        topology = self.build_chain_topology()
        lines = [[(0.2, 0.0), (19.8, 0.0)]]
        result = project_river_lines_to_edges(topology, lines, max_rivers=1)

        cycle = _NeighborCycle(topology)
        for chain in result.chains:
            for prev, nxt in zip(chain, chain[1:]):
                shared = set(prev) & set(nxt)
                self.assertEqual(1, len(shared))
                tile = next(iter(shared))
                prev_other = prev[0] if prev[1] == tile else prev[1]
                next_other = nxt[0] if nxt[1] == tile else nxt[1]
                self.assertEqual([], cycle.intermediate_neighbors(tile, prev_other, next_other))

    def test_includes_coastal_mouth_endpoint(self) -> None:
        topology = self.build_chain_topology()
        line = [[(1.0, 0.0), (19.0, 0.0)]]
        result = project_river_lines_to_edges(topology, line, max_rivers=1)

        # Simulate water endpoints at tiles 0 and 4.
        water_tiles = {0, 4}
        self.assertTrue(result.chains)
        chain = result.chains[0]
        first_edge = chain[0]
        last_edge = chain[-1]
        self.assertTrue((first_edge[0] in water_tiles) or (first_edge[1] in water_tiles))
        self.assertTrue((last_edge[0] in water_tiles) or (last_edge[1] in water_tiles))

    def test_payload_serializes_only_supported_river_fields(self) -> None:
        topology = self.build_chain_topology()
        tiles = [
            TileClassification(
                index=i,
                x=i,
                y=0,
                latitude=0.0,
                longitude=float(i * 5),
                neighbors=topology.tiles[i].neighbors,
                base_terrain="Grassland",
                features=[],
            )
            for i in range(topology.tile_count)
        ]

        result = project_river_lines_to_edges(topology, [[(0.0, 0.0), (20.0, 0.0)]], max_rivers=1)
        payload = build_map_payload(
            topology=topology,
            tiles=tiles,
            ruleset_name="Civ V - Gods & Kings",
            map_name="RiverFieldTest",
            river_edges=result.edges,
        )

        allowed = {"hasBottomRiver", "hasBottomLeftRiver", "hasBottomRightRiver"}
        for tile in payload["tileList"]:
            for key in tile.keys():
                if key.startswith("hasBottom"):
                    self.assertIn(key, allowed)


if __name__ == "__main__":
    unittest.main()
