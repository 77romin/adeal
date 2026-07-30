package com.ssafy.trip.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ssafy.trip.model.dto.FestivalDto;
import com.ssafy.trip.model.dto.TripDto;

/**
 * 관광지 주소와 축제 데이터의 서로 다른 시도 표기를 같은 기준으로 맞춘다.
 */
public final class RegionMatcher {

	private static final Map<String, String[]> REGION_ALIASES = new LinkedHashMap<String, String[]>();

	static {
		REGION_ALIASES.put("서울", new String[] { "서울특별시", "서울" });
		REGION_ALIASES.put("부산", new String[] { "부산광역시", "부산" });
		REGION_ALIASES.put("대구", new String[] { "대구광역시", "대구" });
		REGION_ALIASES.put("인천", new String[] { "인천광역시", "인천" });
		REGION_ALIASES.put("광주", new String[] { "광주광역시", "광주" });
		REGION_ALIASES.put("대전", new String[] { "대전광역시", "대전" });
		REGION_ALIASES.put("울산", new String[] { "울산광역시", "울산" });
		REGION_ALIASES.put("세종", new String[] { "세종특별자치시", "세종시", "세종" });
		REGION_ALIASES.put("경기", new String[] { "경기도", "경기" });
		REGION_ALIASES.put("강원", new String[] { "강원특별자치도", "강원도", "강원" });
		REGION_ALIASES.put("충북", new String[] { "충청북도", "충북" });
		REGION_ALIASES.put("충남", new String[] { "충청남도", "충남" });
		REGION_ALIASES.put("전북", new String[] { "전북특별자치도", "전라북도", "전북" });
		REGION_ALIASES.put("전남", new String[] { "전라남도", "전남" });
		REGION_ALIASES.put("경북", new String[] { "경상북도", "경북" });
		REGION_ALIASES.put("경남", new String[] { "경상남도", "경남" });
		REGION_ALIASES.put("제주", new String[] { "제주특별자치도", "제주도", "제주" });
	}

	private RegionMatcher() {
	}

	public static RegionCriteria fromTrip(TripDto trip) {
		String address = joinAddress(trip == null ? null : trip.getStreetAddress(),
				trip == null ? null : trip.getLotAddress());
		return new RegionCriteria(normalizeSido(address), normalizeText(address));
	}

	public static String normalizeSido(String value) {
		String cleanValue = FestivalDto.cleanCode(value);
		for (Map.Entry<String, String[]> entry : REGION_ALIASES.entrySet()) {
			for (String alias : entry.getValue()) {
				if (cleanValue.contains(alias)) {
					return entry.getKey();
				}
			}
		}
		return "";
	}

	private static String joinAddress(String streetAddress, String lotAddress) {
		String street = streetAddress == null ? "" : streetAddress.trim();
		String lot = lotAddress == null ? "" : lotAddress.trim();
		if (street.isEmpty()) {
			return lot;
		}
		if (lot.isEmpty() || street.equals(lot)) {
			return street;
		}
		return street + " " + lot;
	}

	private static String normalizeText(String value) {
		return value == null ? "" : value.replaceAll("\\s+", "");
	}

	public static final class RegionCriteria {

		private final String sido;
		private final String normalizedAddress;

		private RegionCriteria(String sido, String normalizedAddress) {
			this.sido = sido;
			this.normalizedAddress = normalizedAddress;
		}

		public String getSido() {
			return sido;
		}

		public String findDistrict(List<FestivalDto> festivals) {
			String bestDistrict = "";
			int bestPosition = Integer.MAX_VALUE;
			int bestLength = -1;

			for (FestivalDto festival : festivals) {
				String district = festival.getSigungu();
				if (district == null || district.trim().isEmpty() || "-".equals(district.trim())) {
					continue;
				}

				String normalizedDistrict = normalizeText(FestivalDto.cleanCode(district));
				int position = normalizedAddress.indexOf(normalizedDistrict);
				if (position >= 0
						&& (position < bestPosition
								|| (position == bestPosition && normalizedDistrict.length() > bestLength))) {
					bestDistrict = FestivalDto.cleanCode(district);
					bestPosition = position;
					bestLength = normalizedDistrict.length();
				}
			}
			return bestDistrict;
		}

		public boolean matchesDistrict(FestivalDto festival, String district) {
			return normalizeText(FestivalDto.cleanCode(festival.getSigungu()))
					.equals(normalizeText(FestivalDto.cleanCode(district)));
		}
	}
}
