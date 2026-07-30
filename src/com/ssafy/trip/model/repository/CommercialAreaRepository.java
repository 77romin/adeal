package com.ssafy.trip.model.repository;

import java.io.IOException;
import java.util.List;

import com.ssafy.trip.model.dto.CommercialAreaDto;
import com.ssafy.trip.model.dto.MapBounds;
import com.ssafy.trip.model.dto.TripDto;

public interface CommercialAreaRepository {

	void prepare(TripDto trip) throws IOException;

	List<CommercialAreaDto> findWithin(TripDto trip, MapBounds bounds) throws IOException;

	int getLoadedRegionCount();
}
