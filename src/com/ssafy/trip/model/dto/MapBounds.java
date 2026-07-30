package com.ssafy.trip.model.dto;

/**
 * WGS84 위도/경도의 정규화된 사각 범위다.
 */
public class MapBounds {

	private final double minLatitude;
	private final double minLongitude;
	private final double maxLatitude;
	private final double maxLongitude;

	private MapBounds(double minLatitude, double minLongitude, double maxLatitude, double maxLongitude) {
		this.minLatitude = minLatitude;
		this.minLongitude = minLongitude;
		this.maxLatitude = maxLatitude;
		this.maxLongitude = maxLongitude;
	}

	public static MapBounds of(double latitude1, double longitude1, double latitude2, double longitude2) {
		validateCoordinate(latitude1, longitude1);
		validateCoordinate(latitude2, longitude2);
		return new MapBounds(Math.min(latitude1, latitude2), Math.min(longitude1, longitude2),
				Math.max(latitude1, latitude2), Math.max(longitude1, longitude2));
	}

	public static boolean isValidCoordinate(double latitude, double longitude) {
		return !Double.isNaN(latitude) && !Double.isInfinite(latitude)
				&& !Double.isNaN(longitude) && !Double.isInfinite(longitude)
				&& latitude >= -90.0 && latitude <= 90.0
				&& longitude >= -180.0 && longitude <= 180.0
				&& !(latitude == 0.0 && longitude == 0.0);
	}

	private static void validateCoordinate(double latitude, double longitude) {
		if (!isValidCoordinate(latitude, longitude)) {
			throw new IllegalArgumentException("올바르지 않은 위도/경도입니다: " + latitude + ", " + longitude);
		}
	}

	public boolean contains(double latitude, double longitude) {
		return latitude >= minLatitude && latitude <= maxLatitude
				&& longitude >= minLongitude && longitude <= maxLongitude;
	}

	public double getMinLatitude() {
		return minLatitude;
	}

	public double getMinLongitude() {
		return minLongitude;
	}

	public double getMaxLatitude() {
		return maxLatitude;
	}

	public double getMaxLongitude() {
		return maxLongitude;
	}

	public double getCenterLatitude() {
		return (minLatitude + maxLatitude) / 2.0;
	}

	public double getCenterLongitude() {
		return (minLongitude + maxLongitude) / 2.0;
	}
}
