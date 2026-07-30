package com.ssafy.trip.model.service;

import com.ssafy.trip.model.dto.FestivalSearchResult;
import com.ssafy.trip.model.dto.TripDto;

public interface FestivalService {

	public FestivalSearchResult searchNearby(TripDto trip);

	public int count();
}
