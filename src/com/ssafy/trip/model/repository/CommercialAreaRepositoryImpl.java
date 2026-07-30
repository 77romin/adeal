package com.ssafy.trip.model.repository;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import com.ssafy.trip.model.dto.CommercialAreaDto;
import com.ssafy.trip.model.dto.MapBounds;
import com.ssafy.trip.model.dto.TripDto;
import com.ssafy.trip.util.CommercialAreaFileLoader;

/**
 * 지역 CSV를 최초 한 번만 읽고 FutureTask와 격자 인덱스를 모든 다이얼로그가 공유한다.
 */
public class CommercialAreaRepositoryImpl implements CommercialAreaRepository {

	private final CommercialAreaFileLoader loader;
	private final ConcurrentMap<String, FutureTask<SpatialGridIndex>> regionIndexes =
			new ConcurrentHashMap<String, FutureTask<SpatialGridIndex>>();

	public CommercialAreaRepositoryImpl() {
		this(new CommercialAreaFileLoader());
	}

	public CommercialAreaRepositoryImpl(CommercialAreaFileLoader loader) {
		this.loader = loader;
	}

	@Override
	public void prepare(TripDto trip) throws IOException {
		indexFor(trip);
	}

	@Override
	public List<CommercialAreaDto> findWithin(TripDto trip, MapBounds bounds) throws IOException {
		return indexFor(trip).query(bounds);
	}

	@Override
	public int getLoadedRegionCount() {
		int count = 0;
		for (FutureTask<SpatialGridIndex> task : regionIndexes.values()) {
			if (task.isDone() && !task.isCancelled()) {
				count++;
			}
		}
		return count;
	}

	private SpatialGridIndex indexFor(TripDto trip) throws IOException {
		final String region = loader.resolveRegion(trip);
		FutureTask<SpatialGridIndex> candidate = new FutureTask<SpatialGridIndex>(() ->
				new SpatialGridIndex(loader.loadRegion(region)));
		FutureTask<SpatialGridIndex> task = regionIndexes.putIfAbsent(region, candidate);
		if (task == null) {
			task = candidate;
			candidate.run();
		}
		try {
			return task.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("상권정보 로딩이 중단되었습니다.", e);
		} catch (ExecutionException e) {
			regionIndexes.remove(region, task);
			Throwable cause = e.getCause();
			if (cause instanceof IOException) {
				throw (IOException) cause;
			}
			throw new IOException("상권정보를 로딩할 수 없습니다.", cause);
		}
	}
}
