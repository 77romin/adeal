package com.ssafy.trip.model.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.ssafy.trip.model.dto.CommercialAreaDto;
import com.ssafy.trip.model.dto.CommercialAreaSearchResult;
import com.ssafy.trip.model.dto.MapBounds;
import com.ssafy.trip.model.dto.TripDto;
import com.ssafy.trip.model.repository.CommercialAreaRepository;
import com.ssafy.trip.model.repository.CommercialAreaRepositoryImpl;
import com.ssafy.trip.util.DistanceCalculator;

public class CommercialAreaServiceImpl implements CommercialAreaService {

	private static final CommercialAreaServiceImpl INSTANCE = new CommercialAreaServiceImpl();

	private final CommercialAreaRepository repository;

	public static CommercialAreaServiceImpl getInstance() {
		return INSTANCE;
	}

	public CommercialAreaServiceImpl() {
		this(new CommercialAreaRepositoryImpl());
	}

	public CommercialAreaServiceImpl(CommercialAreaRepository repository) {
		this.repository = repository;
	}

	@Override
	public void prepare(TripDto trip) throws IOException {
		requireValidTrip(trip);
		repository.prepare(trip);
	}

	@Override
	public CommercialAreaSearchResult searchRadius(TripDto trip, double radiusMeters) throws IOException {
		requireValidTrip(trip);
		MapBounds boundingBox = DistanceCalculator.boundingBox(trip.getLat(), trip.getLng(), radiusMeters);
		List<CommercialAreaDto> candidates = repository.findWithin(trip, boundingBox);
		List<CommercialAreaDto> exactMatches = new ArrayList<CommercialAreaDto>();
		for (CommercialAreaDto candidate : candidates) {
			if (DistanceCalculator.haversineMeters(trip.getLat(), trip.getLng(),
					candidate.getLatitude(), candidate.getLongitude()) <= radiusMeters) {
				exactMatches.add(candidate);
			}
		}
		return resultOf(exactMatches);
	}

	@Override
	public CommercialAreaSearchResult searchBounds(TripDto trip, MapBounds bounds) throws IOException {
		requireValidTrip(trip);
		if (bounds == null) {
			throw new IllegalArgumentException("조회 범위가 없습니다.");
		}
		return resultOf(repository.findWithin(trip, bounds));
	}

	@Override
	public int getLoadedRegionCount() {
		return repository.getLoadedRegionCount();
	}

	private static CommercialAreaSearchResult resultOf(List<CommercialAreaDto> stores) {
		Map<String, Integer> sortedCounts = new TreeMap<String, Integer>();
		for (CommercialAreaDto store : stores) {
			String category = store.getLargeCategory();
			if (category == null || category.trim().isEmpty()) {
				category = "미분류";
			}
			Integer count = sortedCounts.get(category);
			sortedCounts.put(category, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
		}
		return new CommercialAreaSearchResult(stores,
				new LinkedHashMap<String, Integer>(sortedCounts));
	}

	private static void requireValidTrip(TripDto trip) {
		if (trip == null || !MapBounds.isValidCoordinate(trip.getLat(), trip.getLng())) {
			throw new IllegalArgumentException("관광지의 위도와 경도가 올바르지 않습니다.");
		}
	}
}
