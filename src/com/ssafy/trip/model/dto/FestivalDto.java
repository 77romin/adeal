package com.ssafy.trip.model.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 지역 축제 한 건의 정보를 담는 객체.
 */
public class FestivalDto {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

	private int num;
	private String metropolitanName;
	private String basicMunicipalityName;
	private String festivalName;
	private String festivalType;
	private String placeName;
	private String placeType;
	private String sido;
	private String sigungu;
	private String eupmyeondong;
	private LocalDate startDate;
	private LocalDate endDate;
	private int totalDays;
	private String scheduleNote;
	private String cycle;
	private String firstYear;
	private String totalBudget;
	private String previousVisitors;
	private String dedicatedOrganization;
	private String affiliation;
	private String department;
	private String contact;
	private String note;

	public int getNum() {
		return num;
	}

	public void setNum(int num) {
		this.num = num;
	}

	public String getMetropolitanName() {
		return metropolitanName;
	}

	public void setMetropolitanName(String metropolitanName) {
		this.metropolitanName = metropolitanName;
	}

	public String getBasicMunicipalityName() {
		return basicMunicipalityName;
	}

	public void setBasicMunicipalityName(String basicMunicipalityName) {
		this.basicMunicipalityName = basicMunicipalityName;
	}

	public String getFestivalName() {
		return festivalName;
	}

	public void setFestivalName(String festivalName) {
		this.festivalName = festivalName;
	}

	public String getFestivalType() {
		return festivalType;
	}

	public void setFestivalType(String festivalType) {
		this.festivalType = festivalType;
	}

	public String getPlaceName() {
		return placeName;
	}

	public void setPlaceName(String placeName) {
		this.placeName = placeName;
	}

	public String getPlaceType() {
		return placeType;
	}

	public void setPlaceType(String placeType) {
		this.placeType = placeType;
	}

	public String getSido() {
		return sido;
	}

	public void setSido(String sido) {
		this.sido = sido;
	}

	public String getSigungu() {
		return sigungu;
	}

	public void setSigungu(String sigungu) {
		this.sigungu = sigungu;
	}

	public String getEupmyeondong() {
		return eupmyeondong;
	}

	public void setEupmyeondong(String eupmyeondong) {
		this.eupmyeondong = eupmyeondong;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public void setEndDate(LocalDate endDate) {
		this.endDate = endDate;
	}

	public int getTotalDays() {
		return totalDays;
	}

	public void setTotalDays(int totalDays) {
		this.totalDays = totalDays;
	}

	public String getScheduleNote() {
		return scheduleNote;
	}

	public void setScheduleNote(String scheduleNote) {
		this.scheduleNote = scheduleNote;
	}

	public String getCycle() {
		return cycle;
	}

	public void setCycle(String cycle) {
		this.cycle = cycle;
	}

	public String getFirstYear() {
		return firstYear;
	}

	public void setFirstYear(String firstYear) {
		this.firstYear = firstYear;
	}

	public String getTotalBudget() {
		return totalBudget;
	}

	public void setTotalBudget(String totalBudget) {
		this.totalBudget = totalBudget;
	}

	public String getPreviousVisitors() {
		return previousVisitors;
	}

	public void setPreviousVisitors(String previousVisitors) {
		this.previousVisitors = previousVisitors;
	}

	public String getDedicatedOrganization() {
		return dedicatedOrganization;
	}

	public void setDedicatedOrganization(String dedicatedOrganization) {
		this.dedicatedOrganization = dedicatedOrganization;
	}

	public String getAffiliation() {
		return affiliation;
	}

	public void setAffiliation(String affiliation) {
		this.affiliation = affiliation;
	}

	public String getDepartment() {
		return department;
	}

	public void setDepartment(String department) {
		this.department = department;
	}

	public String getContact() {
		return contact;
	}

	public void setContact(String contact) {
		this.contact = contact;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public String getRegionText() {
		return joinNonBlank(" ", cleanCode(sido), sigungu, eupmyeondong);
	}

	public String getPeriodText() {
		if (startDate == null && endDate == null) {
			return "-";
		}
		if (startDate == null) {
			return "~ " + DATE_FORMATTER.format(endDate);
		}
		if (endDate == null) {
			return DATE_FORMATTER.format(startDate) + " ~";
		}
		return DATE_FORMATTER.format(startDate) + " ~ " + DATE_FORMATTER.format(endDate);
	}

	public String getOrganizationText() {
		return joinNonBlank(" / ", dedicatedOrganization, affiliation, department);
	}

	public static String cleanCode(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().replaceFirst("^\\d{2}\\.\\s*", "");
	}

	private static String joinNonBlank(String delimiter, String... values) {
		StringBuilder result = new StringBuilder();
		for (String value : values) {
			if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
				continue;
			}
			if (result.length() > 0) {
				result.append(delimiter);
			}
			result.append(value.trim());
		}
		return result.length() == 0 ? "-" : result.toString();
	}
}
