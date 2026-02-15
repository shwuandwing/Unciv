from __future__ import annotations

import unittest

import numpy as np

from tools.earthgen.dataset_sampling import EarthDatasets, GeoRaster, _polygon_shape_from_coords


class DatasetSamplingTests(unittest.TestCase):
    def test_extreme_nodata_sentinel_returns_none(self) -> None:
        raster = GeoRaster(
            data=np.array([[-3.4e38, 10.0]], dtype=np.float32),
            nodata=None,
            width=2,
            height=1,
            origin_lon=0.0,
            origin_lat=1.0,
            pixel_lon=1.0,
            pixel_lat=1.0,
        )
        self.assertIsNone(raster.sample(0.2, 0.5))
        self.assertEqual(10.0, raster.sample(1.2, 0.5))

    def test_int16_nodata_sentinel_returns_none(self) -> None:
        raster = GeoRaster(
            data=np.array([[-32768.0, 12.0]], dtype=np.float32),
            nodata=None,
            width=2,
            height=1,
            origin_lon=0.0,
            origin_lat=1.0,
            pixel_lon=1.0,
            pixel_lat=1.0,
        )
        self.assertIsNone(raster.sample(0.2, 0.5))
        self.assertEqual(12.0, raster.sample(1.2, 0.5))

    def test_monthly_aggregates_skip_int16_nodata(self) -> None:
        nodata_month = GeoRaster(
            data=np.array([[-32768.0]], dtype=np.float32),
            nodata=None,
            width=1,
            height=1,
            origin_lon=0.0,
            origin_lat=1.0,
            pixel_lon=1.0,
            pixel_lat=1.0,
        )
        valid_temp = GeoRaster(
            data=np.array([[25.0]], dtype=np.float32),
            nodata=None,
            width=1,
            height=1,
            origin_lon=0.0,
            origin_lat=1.0,
            pixel_lon=1.0,
            pixel_lat=1.0,
        )
        valid_prec = GeoRaster(
            data=np.array([[100.0]], dtype=np.float32),
            nodata=None,
            width=1,
            height=1,
            origin_lon=0.0,
            origin_lat=1.0,
            pixel_lon=1.0,
            pixel_lat=1.0,
        )
        datasets = EarthDatasets(
            land_polygons=[],
            lake_polygons=[],
            river_lines=[],
            elevation=valid_temp,
            monthly_temperature=[nodata_month, valid_temp],
            monthly_precipitation=[nodata_month, valid_prec],
        )

        self.assertEqual(25.0, datasets.sample_temperature(0.1, 0.1))
        self.assertEqual(100.0, datasets.sample_precipitation(0.1, 0.1))

    def test_polygon_contains_handles_antimeridian_crossing(self) -> None:
        # Rectangle from lon 170..-170 (crosses antimeridian), lat -10..10.
        shape = _polygon_shape_from_coords(
            [
                [
                    [170.0, -10.0],
                    [170.0, 10.0],
                    [-170.0, 10.0],
                    [-170.0, -10.0],
                    [170.0, -10.0],
                ]
            ]
        )
        assert shape is not None

        self.assertTrue(shape.contains(179.0, 0.0))
        self.assertTrue(shape.contains(-179.0, 0.0))
        self.assertFalse(shape.contains(0.0, 0.0))


if __name__ == "__main__":
    unittest.main()
