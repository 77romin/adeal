package com.ssafy.trip.model.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.ssafy.trip.model.dao.FestivalDao;
import com.ssafy.trip.model.dao.FestivalDaoImpl;
import com.ssafy.trip.model.dto.FestivalDto;
import com.ssafy.trip.model.dto.FestivalSearchResult;
import com.ssafy.trip.model.dto.TripDto;
import com.ssafy.trip.util.RegionMatcher;
import com.ssafy.trip.util.RegionMatcher.RegionCriteria;

public class FestivalServiceImpl implements FestivalService {

	private final FestivalDao festivalDao;

	public FestivalServiceImpl() {
		festivalDao = new FestivalDaoImpl();
	}

	@Override
	public FestivalSearchResult searchNearby(TripDto trip) {
		RegionCriteria criteria = RegionMatcher.fromTrip(trip);
		if (criteria.getSido().isEmpty()) {
			return new FestivalSearchResult(Collections.<FestivalDto>emptyList(),
					"관광지 주소에서 지역을 확인할 수 없습니다.", false);
		}

		List<FestivalDto> sameSido = festivalDao.searchBySido(criteria.getSido());
		String district = criteria.findDistrict(sameSido);
		if (!district.isEmpty()) {
			List<FestivalDto> sameDistrict = new ArrayList<FestivalDto>();
			for (FestivalDto festival : sameSido) {
				if (criteria.matchesDistrict(festival, district)) {
					sameDistrict.add(festival);
				}
			}
			if (!sameDistrict.isEmpty()) {
				sortByStartDate(sameDistrict);
				return new FestivalSearchResult(sameDistrict,
						criteria.getSido() + " " + district, true);
			}
		}

		sortByStartDate(sameSido);
		return new FestivalSearchResult(sameSido, criteria.getSido() + " 전체", false);
	}

	private void sortByStartDate(List<FestivalDto> festivals) {
		Collections.sort(festivals, new Comparator<FestivalDto>() {
			@Override
			public int compare(FestivalDto first, FestivalDto second) {
				LocalDate firstDate = first.getStartDate();
				LocalDate secondDate = second.getStartDate();
				if (firstDate == null && secondDate == null) {
					return compareNames(first, second);
				}
				if (firstDate == null) {
					return 1;
				}
				if (secondDate == null) {
					return -1;
				}
				int dateCompare = firstDate.compareTo(secondDate);
				return dateCompare == 0 ? compareNames(first, second) : dateCompare;
			}
		});
	}

	private int compareNames(FestivalDto first, FestivalDto second) {
		String firstName = first.getFestivalName() == null ? "" : first.getFestivalName();
		String secondName = second.getFestivalName() == null ? "" : second.getFestivalName();
		return firstName.compareTo(secondName);
	}

	@Override
	public int count() {
		return festivalDao.count();
	}
}
