package com.ssafy.trip.model.dto;

/**
 * 상권정보 CSV의 한 업소를 나타낸다. 좌표는 업소 위치의 WGS84 위도/경도다.
 */
public class CommercialAreaDto {

	private final String id;
	private final String name;
	private final String largeCategory;
	private final String middleCategory;
	private final String smallCategory;
	private final String lotAddress;
	private final String roadAddress;
	private final double latitude;
	private final double longitude;

	public CommercialAreaDto(String id, String name, String largeCategory, String middleCategory,
			String smallCategory, String lotAddress, String roadAddress, double latitude, double longitude) {
		this.id = id;
		this.name = name;
		this.largeCategory = largeCategory;
		this.middleCategory = middleCategory;
		this.smallCategory = smallCategory;
		this.lotAddress = lotAddress;
		this.roadAddress = roadAddress;
		this.latitude = latitude;
		this.longitude = longitude;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getLargeCategory() {
		return largeCategory;
	}

	public String getMiddleCategory() {
		return middleCategory;
	}

	public String getSmallCategory() {
		return smallCategory;
	}

	public String getLotAddress() {
		return lotAddress;
	}

	public String getRoadAddress() {
		return roadAddress;
	}

	public String getDisplayAddress() {
		return roadAddress == null || roadAddress.trim().isEmpty() ? lotAddress : roadAddress;
	}

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}
}
