package com.ssafy.trip.model.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 주변 축제 검색 결과와 실제 적용된 검색 범위를 함께 전달한다.
 */
public class FestivalSearchResult {

	private final List<FestivalDto> festivals;
	private final String searchArea;
	private final boolean districtMatched;

	public FestivalSearchResult(List<FestivalDto> festivals, String searchArea, boolean districtMatched) {
		this.festivals = Collections.unmodifiableList(new ArrayList<FestivalDto>(festivals));
		this.searchArea = searchArea;
		this.districtMatched = districtMatched;
	}

	public List<FestivalDto> getFestivals() {
		return festivals;
	}

	public String getSearchArea() {
		return searchArea;
	}

	public boolean isDistrictMatched() {
		return districtMatched;
	}
}
