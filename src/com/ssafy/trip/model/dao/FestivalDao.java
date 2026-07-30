package com.ssafy.trip.model.dao;

import java.util.List;

import com.ssafy.trip.model.dto.FestivalDto;

public interface FestivalDao {

	public List<FestivalDto> searchBySido(String sido);

	public int count();
}
