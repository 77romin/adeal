package com.ssafy.trip.model.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ssafy.trip.model.dto.CommercialAreaDto;
import com.ssafy.trip.model.dto.MapBounds;

/**
 * 약 1.1km 크기의 위경도 격자에 점 데이터를 넣어 작은 범위를 빠르게 조회한다.
 */
public class SpatialGridIndex {

	private static final double CELL_SIZE_DEGREES = 0.01;

	private final Map<Long, List<CommercialAreaDto>> cells = new HashMap<Long, List<CommercialAreaDto>>();
	private int minimumLatitudeCell = Integer.MAX_VALUE;
	private int maximumLatitudeCell = Integer.MIN_VALUE;
	private int minimumLongitudeCell = Integer.MAX_VALUE;
	private int maximumLongitudeCell = Integer.MIN_VALUE;

	public SpatialGridIndex(List<CommercialAreaDto> stores) {
		for (CommercialAreaDto store : stores) {
			add(store);
		}
	}

	public void add(CommercialAreaDto store) {
		int latitudeCell = cell(store.getLatitude());
		int longitudeCell = cell(store.getLongitude());
		Long key = Long.valueOf(key(latitudeCell, longitudeCell));
		List<CommercialAreaDto> bucket = cells.get(key);
		if (bucket == null) {
			bucket = new ArrayList<CommercialAreaDto>();
			cells.put(key, bucket);
		}
		bucket.add(store);
		minimumLatitudeCell = Math.min(minimumLatitudeCell, latitudeCell);
		maximumLatitudeCell = Math.max(maximumLatitudeCell, latitudeCell);
		minimumLongitudeCell = Math.min(minimumLongitudeCell, longitudeCell);
		maximumLongitudeCell = Math.max(maximumLongitudeCell, longitudeCell);
	}

	public List<CommercialAreaDto> query(MapBounds bounds) {
		if (cells.isEmpty()) {
			return Collections.emptyList();
		}
		int firstLatitudeCell = Math.max(minimumLatitudeCell, cell(bounds.getMinLatitude()));
		int lastLatitudeCell = Math.min(maximumLatitudeCell, cell(bounds.getMaxLatitude()));
		int firstLongitudeCell = Math.max(minimumLongitudeCell, cell(bounds.getMinLongitude()));
		int lastLongitudeCell = Math.min(maximumLongitudeCell, cell(bounds.getMaxLongitude()));
		if (firstLatitudeCell > lastLatitudeCell || firstLongitudeCell > lastLongitudeCell) {
			return Collections.emptyList();
		}

		List<CommercialAreaDto> found = new ArrayList<CommercialAreaDto>();
		for (int latitudeCell = firstLatitudeCell; latitudeCell <= lastLatitudeCell; latitudeCell++) {
			for (int longitudeCell = firstLongitudeCell; longitudeCell <= lastLongitudeCell; longitudeCell++) {
				List<CommercialAreaDto> bucket = cells.get(Long.valueOf(key(latitudeCell, longitudeCell)));
				if (bucket == null) {
					continue;
				}
				for (CommercialAreaDto store : bucket) {
					if (bounds.contains(store.getLatitude(), store.getLongitude())) {
						found.add(store);
					}
				}
			}
		}
		return found;
	}

	private static int cell(double coordinate) {
		return (int) Math.floor(coordinate / CELL_SIZE_DEGREES);
	}

	private static long key(int latitudeCell, int longitudeCell) {
		return ((long) latitudeCell << 32) ^ (longitudeCell & 0xffffffffL);
	}
}
