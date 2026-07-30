package com.ssafy.trip.view.commercial;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;

import com.ssafy.trip.model.dto.CommercialAreaDto;
import com.ssafy.trip.model.dto.CommercialAreaSearchResult;
import com.ssafy.trip.model.dto.MapBounds;
import com.ssafy.trip.model.dto.TripDto;
import com.ssafy.trip.model.service.CommercialAreaService;

public class CommercialAreaPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final double DEFAULT_RADIUS_METERS = 1_000.0;
	private static final int QUERY_DEBOUNCE_MILLIS = 80;

	private final TripDto trip;
	private final CommercialAreaService service;
	private final CommercialMapCanvas mapCanvas;
	private final CommercialTableModel tableModel = new CommercialTableModel();
	private final JTable resultTable = new JTable(tableModel);
	private final JLabel countLabel = new JLabel("선택된 결과: 0개");
	private final JTextArea categorySummary = new JTextArea();
	private final JLabel statusLabel = new JLabel("상권정보를 불러오는 중입니다...");
	private final JRadioButton moveModeButton = new JRadioButton("지도 이동", true);
	private final JRadioButton rangeModeButton = new JRadioButton("범위 선택");
	private final JButton resetButton = new JButton("반경 1km로 초기화");
	private final AtomicInteger requestSequence = new AtomicInteger();
	private final ExecutorService queryExecutor;
	private final Timer queryDebounceTimer;

	private volatile boolean disposed;
	private MapBounds pendingBounds;
	private SwingWorker<CommercialAreaSearchResult, Void> initialLoadWorker;

	public CommercialAreaPanel(TripDto trip, CommercialAreaService service) {
		this.trip = trip;
		this.service = service;
		this.mapCanvas = new CommercialMapCanvas(trip.getLat(), trip.getLng());
		this.queryExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable runnable) {
				Thread thread = new Thread(runnable, "commercial-area-query");
				thread.setDaemon(true);
				return thread;
			}
		});
		this.queryDebounceTimer = new Timer(QUERY_DEBOUNCE_MILLIS, event -> submitPendingBounds());
		this.queryDebounceTimer.setRepeats(false);

		buildUi();
		installEvents();
		setControlsEnabled(false);
		loadInitialData();
		SwingUtilities.invokeLater(() -> mapCanvas.resetViewToRadius(DEFAULT_RADIUS_METERS));
	}

	public void disposeResources() {
		disposed = true;
		requestSequence.incrementAndGet();
		queryDebounceTimer.stop();
		if (initialLoadWorker != null) {
			initialLoadWorker.cancel(true);
		}
		queryExecutor.shutdownNow();
	}

	private void buildUi() {
		setLayout(new BorderLayout(8, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
		ButtonGroup modes = new ButtonGroup();
		modes.add(moveModeButton);
		modes.add(rangeModeButton);
		toolbar.add(new JLabel("지도 모드"));
		toolbar.add(moveModeButton);
		toolbar.add(rangeModeButton);
		toolbar.add(resetButton);
		JLabel attractionLabel = new JLabel("관광지: " + safe(trip.getTouristDestination()));
		attractionLabel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
		toolbar.add(attractionLabel);
		add(toolbar, BorderLayout.NORTH);

		JPanel resultPanel = new JPanel(new BorderLayout(6, 6));
		resultPanel.setPreferredSize(new Dimension(440, 600));
		countLabel.setFont(countLabel.getFont().deriveFont(Font.BOLD, 14f));
		resultPanel.add(countLabel, BorderLayout.NORTH);

		resultTable.setAutoCreateRowSorter(true);
		resultTable.setFillsViewportHeight(true);
		resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		setColumnWidths();
		JScrollPane tableScroll = new JScrollPane(resultTable);

		categorySummary.setEditable(false);
		categorySummary.setLineWrap(true);
		categorySummary.setWrapStyleWord(true);
		categorySummary.setBackground(new Color(248, 249, 250));
		categorySummary.setBorder(BorderFactory.createTitledBorder("업종별 개수"));
		JScrollPane categoryScroll = new JScrollPane(categorySummary);
		categoryScroll.setPreferredSize(new Dimension(400, 135));

		JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, categoryScroll);
		rightSplit.setResizeWeight(0.78);
		rightSplit.setBorder(null);
		resultPanel.add(rightSplit, BorderLayout.CENTER);

		JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mapCanvas, resultPanel);
		mainSplit.setResizeWeight(0.66);
		mainSplit.setContinuousLayout(true);
		add(mainSplit, BorderLayout.CENTER);

		statusLabel.setOpaque(true);
		statusLabel.setBackground(new Color(245, 247, 248));
		statusLabel.setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 9));
		add(statusLabel, BorderLayout.SOUTH);
	}

	private void installEvents() {
		moveModeButton.addActionListener(event ->
				mapCanvas.setInteractionMode(CommercialMapCanvas.InteractionMode.MOVE));
		rangeModeButton.addActionListener(event ->
				mapCanvas.setInteractionMode(CommercialMapCanvas.InteractionMode.SELECT_RANGE));
		resetButton.addActionListener(event -> resetToDefaultRadius());
		mapCanvas.setRangeSelectionListener((bounds, finished) -> {
			pendingBounds = bounds;
			if (finished) {
				queryDebounceTimer.stop();
				submitPendingBounds();
				moveModeButton.setSelected(true);
				mapCanvas.setInteractionMode(CommercialMapCanvas.InteractionMode.MOVE);
			} else {
				queryDebounceTimer.restart();
			}
		});
	}

	private void loadInitialData() {
		showLoading("상권정보 파일을 최초 1회 로딩하고 있습니다...");
		initialLoadWorker = new SwingWorker<CommercialAreaSearchResult, Void>() {
			@Override
			protected CommercialAreaSearchResult doInBackground() throws Exception {
				service.prepare(trip);
				return service.searchRadius(trip, DEFAULT_RADIUS_METERS);
			}

			@Override
			protected void done() {
				if (disposed || isCancelled()) {
					return;
				}
				try {
					applyResult(get(), "관광지 중심 반경 1km");
					setControlsEnabled(true);
				} catch (Exception e) {
					showFailure(rootMessage(e));
				}
			}
		};
		initialLoadWorker.execute();
	}

	private void resetToDefaultRadius() {
		queryDebounceTimer.stop();
		pendingBounds = null;
		mapCanvas.resetViewToRadius(DEFAULT_RADIUS_METERS);
		moveModeButton.setSelected(true);
		mapCanvas.setInteractionMode(CommercialMapCanvas.InteractionMode.MOVE);
		int requestNumber = requestSequence.incrementAndGet();
		showLoading("관광지 중심 반경 1km를 조회하는 중입니다...");
		queryExecutor.execute(() -> {
			try {
				CommercialAreaSearchResult result = service.searchRadius(trip, DEFAULT_RADIUS_METERS);
				publishResult(requestNumber, result, "관광지 중심 반경 1km");
			} catch (Exception e) {
				publishFailure(requestNumber, e);
			}
		});
	}

	private void submitPendingBounds() {
		final MapBounds bounds = pendingBounds;
		if (bounds == null || disposed) {
			return;
		}
		final int requestNumber = requestSequence.incrementAndGet();
		showLoading("선택 범위를 조회하는 중입니다...");
		queryExecutor.execute(() -> {
			try {
				CommercialAreaSearchResult result = service.searchBounds(trip, bounds);
				publishResult(requestNumber, result, "선택 범위");
			} catch (IOException | RuntimeException e) {
				publishFailure(requestNumber, e);
			}
		});
	}

	private void publishResult(int requestNumber, CommercialAreaSearchResult result, String description) {
		SwingUtilities.invokeLater(() -> {
			if (!disposed && requestNumber == requestSequence.get()) {
				applyResult(result, description);
			}
		});
	}

	private void publishFailure(int requestNumber, Exception error) {
		SwingUtilities.invokeLater(() -> {
			if (!disposed && requestNumber == requestSequence.get()) {
				showFailure(rootMessage(error));
			}
		});
	}

	private void applyResult(CommercialAreaSearchResult result, String description) {
		tableModel.setStores(result.getStores());
		mapCanvas.setStores(result.getStores());
		countLabel.setText("선택된 결과: " + result.getTotalCount() + "개");
		categorySummary.setText(formatCategoryCounts(result.getCategoryCounts()));
		categorySummary.setCaretPosition(0);
		if (result.getTotalCount() == 0) {
			statusLabel.setForeground(new Color(96, 74, 0));
			statusLabel.setText(description + " 안에 조회된 업소가 없습니다. 다른 범위를 선택해 보세요.");
		} else {
			statusLabel.setForeground(new Color(27, 94, 32));
			statusLabel.setText(description + " 조회 완료 · " + result.getTotalCount()
					+ "개 · 로딩된 지역 데이터 " + service.getLoadedRegionCount() + "개");
		}
	}

	private void showLoading(String message) {
		statusLabel.setForeground(new Color(13, 71, 161));
		statusLabel.setText(message);
	}

	private void showFailure(String message) {
		tableModel.setStores(new ArrayList<CommercialAreaDto>());
		mapCanvas.setStores(new ArrayList<CommercialAreaDto>());
		countLabel.setText("선택된 결과: 0개");
		categorySummary.setText("");
		statusLabel.setForeground(new Color(183, 28, 28));
		statusLabel.setText("상권정보 로딩 실패: " + message);
		setControlsEnabled(false);
	}

	private void setControlsEnabled(boolean enabled) {
		moveModeButton.setEnabled(enabled);
		rangeModeButton.setEnabled(enabled);
		resetButton.setEnabled(enabled);
	}

	private void setColumnWidths() {
		int[] widths = { 145, 75, 105, 135, 285, 95, 95 };
		for (int i = 0; i < widths.length; i++) {
			resultTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
		}
	}

	private static String formatCategoryCounts(Map<String, Integer> counts) {
		if (counts.isEmpty()) {
			return "조회 결과 없음";
		}
		StringBuilder text = new StringBuilder();
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			if (text.length() > 0) {
				text.append('\n');
			}
			text.append(entry.getKey()).append(": ").append(entry.getValue()).append("개");
		}
		return text.toString();
	}

	private static String rootMessage(Throwable error) {
		Throwable current = error;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		String message = current.getMessage();
		return message == null || message.trim().isEmpty() ? current.getClass().getSimpleName() : message;
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private static final class CommercialTableModel extends AbstractTableModel {

		private static final long serialVersionUID = 1L;
		private static final String[] COLUMNS = {
				"상호명", "대분류", "중분류", "소분류", "주소", "위도", "경도"
		};
		private List<CommercialAreaDto> stores = new ArrayList<CommercialAreaDto>();

		private void setStores(List<CommercialAreaDto> stores) {
			this.stores = new ArrayList<CommercialAreaDto>(stores);
			fireTableDataChanged();
		}

		@Override
		public int getRowCount() {
			return stores.size();
		}

		@Override
		public int getColumnCount() {
			return COLUMNS.length;
		}

		@Override
		public String getColumnName(int column) {
			return COLUMNS[column];
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			return columnIndex >= 5 ? Double.class : String.class;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			CommercialAreaDto store = stores.get(rowIndex);
			switch (columnIndex) {
			case 0:
				return store.getName();
			case 1:
				return store.getLargeCategory();
			case 2:
				return store.getMiddleCategory();
			case 3:
				return store.getSmallCategory();
			case 4:
				return store.getDisplayAddress();
			case 5:
				return Double.valueOf(store.getLatitude());
			case 6:
				return Double.valueOf(store.getLongitude());
			default:
				return "";
			}
		}
	}
}
