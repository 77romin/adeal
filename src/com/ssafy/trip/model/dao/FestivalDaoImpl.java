package com.ssafy.trip.model.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ssafy.trip.model.dto.FestivalDto;
import com.ssafy.trip.util.FestivalExcelParser;
import com.ssafy.trip.util.RegionMatcher;

public class FestivalDaoImpl implements FestivalDao {

	private final List<FestivalDto> festivals;

	public FestivalDaoImpl() {
		festivals = Collections.unmodifiableList(new FestivalExcelParser().parse());
	}

	@Override
	public List<FestivalDto> searchBySido(String sido) {
		List<FestivalDto> result = new ArrayList<FestivalDto>();
		for (FestivalDto festival : festivals) {
			if (sido.equals(RegionMatcher.normalizeSido(festival.getSido()))) {
				result.add(festival);
			}
		}
		return result;
	}

	@Override
	public int count() {
		return festivals.size();
	}
}
