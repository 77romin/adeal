package com.ssafy.trip.util;

import com.ssafy.trip.model.dto.MapBounds;

public final class DistanceCalculator {

	private static final double EARTH_RADIUS_METERS = 6_371_000.0;
	private static final double METERS_PER_DEGREE = 111_320.0;

	private DistanceCalculator() {
	}

	public static double haversineMeters(double latitude1, double longitude1,
			double latitude2, double longitude2) {
		double latitudeDelta = Math.toRadians(latitude2 - latitude1);
		double longitudeDelta = Math.toRadians(longitude2 - longitude1);
		double a = Math.sin(latitudeDelta / 2.0) * Math.sin(latitudeDelta / 2.0)
				+ Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
				* Math.sin(longitudeDelta / 2.0) * Math.sin(longitudeDelta / 2.0);
		return EARTH_RADIUS_METERS * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
	}

	public static MapBounds boundingBox(double latitude, double longitude, double radiusMeters) {
		if (!MapBounds.isValidCoordinate(latitude, longitude) || radiusMeters <= 0.0) {
			throw new IllegalArgumentException("중심 좌표와 반경을 확인해 주세요.");
		}
		double latitudeDelta = radiusMeters / METERS_PER_DEGREE;
		double cosine = Math.max(0.000001, Math.cos(Math.toRadians(latitude)));
		double longitudeDelta = radiusMeters / (METERS_PER_DEGREE * cosine);
		return MapBounds.of(latitude - latitudeDelta, longitude - longitudeDelta,
				latitude + latitudeDelta, longitude + longitudeDelta);
	}
}
