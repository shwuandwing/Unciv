from __future__ import annotations

import unittest
from pathlib import Path

import numpy as np

from tools.earthgen.dataset_sampling import (
    EarthDatasets,
    GeoRaster,
    LatLonRaster,
    _load_monthly_latlon_rasters_from_netcdf,
    _polygon_shape_from_coords,
)


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

    def test_monthly_ocean_temperature_aggregate_uses_latlon_rasters(self) -> None:
        jan = LatLonRaster(
            data=np.array([[8.0, 10.0], [4.0, 6.0]], dtype=np.float32),
            nodata=None,
            width=2,
            height=2,
            lon_start=0.0,
            lon_step=2.0,
            lat_start=2.0,
            lat_step=-2.0,
        )
        feb = LatLonRaster(
            data=np.array([[7.0, 9.0], [3.0, 5.0]], dtype=np.float32),
            nodata=None,
            width=2,
            height=2,
            lon_start=0.0,
            lon_step=2.0,
            lat_start=2.0,
            lat_step=-2.0,
        )
        datasets = EarthDatasets(
            land_polygons=[],
            lake_polygons=[],
            river_lines=[],
            elevation=GeoRaster(
                data=np.array([[0.0]], dtype=np.float32),
                nodata=None,
                width=1,
                height=1,
                origin_lon=0.0,
                origin_lat=1.0,
                pixel_lon=1.0,
                pixel_lat=1.0,
            ),
            monthly_temperature=[],
            monthly_precipitation=[],
            monthly_sea_surface_temperature=[jan, feb],
        )

        self.assertEqual(7.5, datasets.sample_ocean_temperature(0.2, 1.8))
        self.assertEqual(7.0, datasets.sample_ocean_coldest_month_temperature(0.2, 1.8))

    def test_latlon_raster_sample_uses_nearest_valid_neighbor_for_masked_cell(self) -> None:
        raster = LatLonRaster(
            data=np.array(
                [
                    [1.0, 2.0, 3.0],
                    [4.0, np.nan, 6.0],
                    [7.0, 8.0, 9.0],
                ],
                dtype=np.float32,
            ),
            nodata=None,
            width=3,
            height=3,
            lon_start=0.0,
            lon_step=2.0,
            lat_start=4.0,
            lat_step=-2.0,
        )

        self.assertEqual(2.0, raster.sample(2.0, 2.0))

    def test_latlon_raster_sample_wraps_to_valid_neighbor_across_antimeridian(self) -> None:
        raster = LatLonRaster(
            data=np.array([[5.0, np.nan, np.nan]], dtype=np.float32),
            nodata=None,
            width=3,
            height=1,
            lon_start=0.0,
            lon_step=120.0,
            lat_start=0.0,
            lat_step=-1.0,
        )

        self.assertEqual(5.0, raster.sample(240.0, 0.0))

    def test_load_monthly_latlon_rasters_from_netcdf_reads_regular_grid(self) -> None:
        nc_path = Path("/tmp/test_earthgen_sst.nc")
        try:
            from netCDF4 import Dataset

            with Dataset(nc_path, "w") as ds:
                ds.createDimension("time", 2)
                ds.createDimension("lat", 2)
                ds.createDimension("lon", 3)
                time = ds.createVariable("time", "i4", ("time",))
                lat = ds.createVariable("lat", "f4", ("lat",))
                lon = ds.createVariable("lon", "f4", ("lon",))
                sst = ds.createVariable("sst", "f4", ("time", "lat", "lon"), fill_value=-9999.0)
                time[:] = [0, 1]
                lat[:] = [2.0, 0.0]
                lon[:] = [0.0, 2.0, 4.0]
                sst[:] = np.array(
                    [
                        [[11.0, 12.0, 13.0], [1.0, 2.0, 3.0]],
                        [[21.0, 22.0, 23.0], [4.0, 5.0, 6.0]],
                    ],
                    dtype=np.float32,
                )

            rasters = _load_monthly_latlon_rasters_from_netcdf(nc_path, variable_name="sst")
            self.assertEqual(2, len(rasters))
            self.assertEqual(12.0, rasters[0].sample(1.7, 1.7))
            self.assertEqual(22.0, rasters[1].sample(2.1, 2.0))
        finally:
            nc_path.unlink(missing_ok=True)

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

    def test_polygon_contains_handles_south_pole_cap(self) -> None:
        # Synthetic Antarctica-style polygon: a polar cap bounded by latitude -70 with
        # explicit pole vertices on the antimeridian. The planar lon/lat test fails on
        # this shape because the ring crosses the south-pole singularity.
        shape = _polygon_shape_from_coords(
            [
                [
                    [-180.0, -70.0],
                    [-120.0, -70.0],
                    [-60.0, -70.0],
                    [0.0, -70.0],
                    [60.0, -70.0],
                    [120.0, -70.0],
                    [180.0, -70.0],
                    [180.0, -90.0],
                    [-180.0, -90.0],
                    [-180.0, -70.0],
                ]
            ]
        )
        assert shape is not None

        self.assertTrue(shape.contains(0.0, -80.0))
        self.assertTrue(shape.contains(90.0, -80.0))
        self.assertTrue(shape.contains(-90.0, -80.0))
        self.assertFalse(shape.contains(0.0, -60.0))


if __name__ == "__main__":
    unittest.main()
