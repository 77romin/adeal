package com.ssafy.trip.model.service;

import java.io.IOException;

import com.ssafy.trip.model.dto.CommercialAreaSearchResult;
import com.ssafy.trip.model.dto.MapBounds;
import com.ssafy.trip.model.dto.TripDto;

public interface CommercialAreaService {

	void prepare(TripDto trip) throws IOException;

	CommercialAreaSearchResult searchRadius(TripDto trip, double radiusMeters) throws IOException;

	CommercialAreaSearchResult searchBounds(TripDto trip, MapBounds bounds) throws IOException;

	int getLoadedRegionCount();
}
