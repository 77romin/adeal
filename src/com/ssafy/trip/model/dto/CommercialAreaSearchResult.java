package com.ssafy.trip.model.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CommercialAreaSearchResult {

	private final List<CommercialAreaDto> stores;
	private final Map<String, Integer> categoryCounts;

	public CommercialAreaSearchResult(List<CommercialAreaDto> stores, Map<String, Integer> categoryCounts) {
		this.stores = Collections.unmodifiableList(new ArrayList<CommercialAreaDto>(stores));
		this.categoryCounts = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(categoryCounts));
	}

	public List<CommercialAreaDto> getStores() {
		return stores;
	}

	public Map<String, Integer> getCategoryCounts() {
		return categoryCounts;
	}

	public int getTotalCount() {
		return stores.size();
	}
}
