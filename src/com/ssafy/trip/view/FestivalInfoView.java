package com.ssafy.trip.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

import com.ssafy.trip.model.dto.FestivalDto;
import com.ssafy.trip.model.dto.FestivalSearchResult;
import com.ssafy.trip.model.dto.TripDto;
import com.ssafy.trip.model.service.FestivalService;
import com.ssafy.trip.model.service.FestivalServiceImpl;

/**
 * 현재 관광지와 같은 지역의 축제 목록 및 상세 정보를 보여주는 별도 창.
 */
public class FestivalInfoView {

	private static final String[] TABLE_COLUMNS = { "번호", "축제명", "장소", "지역", "개최 기간" };
	private static FestivalService festivalService;

	private final JFrame frame;
	private final TripDto trip;
	private FestivalSearchResult searchResult;
	private DefaultTableModel festivalModel;
	private JTable festivalTable;

	private JTextArea festivalNameValue;
	private JTextArea festivalTypeValue;
	private JTextArea placeValue;
	private JTextArea placeTypeValue;
	private JTextArea regionValue;
	private JTextArea periodValue;
	private JTextArea totalDaysValue;
	private JTextArea cycleValue;
	private JTextArea firstYearValue;
	private JTextArea budgetValue;
	private JTextArea visitorsValue;
	private JTextArea organizationValue;
	private JTextArea contactValue;
	private JTextArea noteValue;

	public FestivalInfoView(JFrame owner, TripDto trip) {
		this.trip = trip;
		frame = new JFrame("Enjoy! Trip - 주변 축제");
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		try {
			searchResult = getFestivalService().searchNearby(trip);
		} catch (RuntimeException e) {
			JOptionPane.showMessageDialog(owner,
					"축제 데이터를 불러오지 못했습니다.\n" + rootMessage(e),
					"축제 데이터 오류", JOptionPane.ERROR_MESSAGE);
			return;
		}

		setMain();
		frame.setSize(1200, 760);
		frame.setMinimumSize(new Dimension(900, 600));
		frame.setLocationRelativeTo(owner);
		frame.setVisible(true);
	}

	private static synchronized FestivalService getFestivalService() {
		if (festivalService == null) {
			festivalService = new FestivalServiceImpl();
		}
		return festivalService;
	}

	private void setMain() {
		JPanel header = new JPanel(new BorderLayout());
		JLabel title = new JLabel("'" + trip.getTouristDestination() + "' 주변 축제", SwingConstants.CENTER);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
		title.setBorder(BorderFactory.createEmptyBorder(12, 10, 6, 10));

		String rangeText = "검색 지역: " + searchResult.getSearchArea()
				+ " · " + searchResult.getFestivals().size() + "개";
		if (!searchResult.isDistrictMatched() && !searchResult.getFestivals().isEmpty()) {
			rangeText += " (시군구 결과가 없어 시도 전체로 확대)";
		}
		JLabel range = new JLabel(rangeText, SwingConstants.CENTER);
		range.setForeground(new Color(70, 70, 70));
		range.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

		header.add(title, BorderLayout.NORTH);
		header.add(range, BorderLayout.SOUTH);

		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
				createDetailPanel(), createListPanel());
		splitPane.setResizeWeight(0.47);
		splitPane.setDividerLocation(540);

		frame.add(header, BorderLayout.NORTH);
		frame.add(splitPane, BorderLayout.CENTER);

		showInitialFestival();
	}

	private Component createDetailPanel() {
		JPanel form = new JPanel(new GridBagLayout());
		form.setBorder(BorderFactory.createEmptyBorder(10, 14, 14, 14));

		int row = 0;
		festivalNameValue = addDetailRow(form, row++, "축제명");
		festivalTypeValue = addDetailRow(form, row++, "축제 유형");
		placeValue = addDetailRow(form, row++, "개최 장소");
		placeTypeValue = addDetailRow(form, row++, "장소 유형");
		regionValue = addDetailRow(form, row++, "지역");
		periodValue = addDetailRow(form, row++, "개최 기간");
		totalDaysValue = addDetailRow(form, row++, "총 일수");
		cycleValue = addDetailRow(form, row++, "개최 주기");
		firstYearValue = addDetailRow(form, row++, "최초 개최연도");
		budgetValue = addDetailRow(form, row++, "총 예산");
		visitorsValue = addDetailRow(form, row++, "전년도 방문객");
		organizationValue = addDetailRow(form, row++, "주최 전담조직");
		contactValue = addDetailRow(form, row++, "공식 연락처");
		noteValue = addDetailRow(form, row++, "비고");

		GridBagConstraints filler = new GridBagConstraints();
		filler.gridx = 0;
		filler.gridy = row;
		filler.gridwidth = 2;
		filler.weighty = 1.0;
		filler.fill = GridBagConstraints.VERTICAL;
		form.add(new JPanel(), filler);

		JPanel panel = new JPanel(new BorderLayout());
		panel.add(new JLabel("축제 상세 정보", SwingConstants.CENTER), BorderLayout.NORTH);
		panel.add(new JScrollPane(form), BorderLayout.CENTER);
		return panel;
	}

	private JTextArea addDetailRow(JPanel form, int row, String labelText) {
		GridBagConstraints labelConstraints = new GridBagConstraints();
		labelConstraints.gridx = 0;
		labelConstraints.gridy = row;
		labelConstraints.anchor = GridBagConstraints.NORTHWEST;
		labelConstraints.insets = new Insets(7, 0, 7, 12);

		JLabel label = new JLabel(labelText);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		form.add(label, labelConstraints);

		JTextArea value = new JTextArea("-");
		value.setEditable(false);
		value.setOpaque(false);
		value.setLineWrap(true);
		value.setWrapStyleWord(true);
		value.setFont(UIManager.getFont("Label.font"));
		value.setBorder(null);

		GridBagConstraints valueConstraints = new GridBagConstraints();
		valueConstraints.gridx = 1;
		valueConstraints.gridy = row;
		valueConstraints.weightx = 1.0;
		valueConstraints.fill = GridBagConstraints.HORIZONTAL;
		valueConstraints.anchor = GridBagConstraints.NORTHWEST;
		valueConstraints.insets = new Insets(7, 0, 7, 0);
		form.add(value, valueConstraints);
		return value;
	}

	private Component createListPanel() {
		festivalModel = new DefaultTableModel(TABLE_COLUMNS, 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};

		List<FestivalDto> festivals = searchResult.getFestivals();
		for (FestivalDto festival : festivals) {
			festivalModel.addRow(new Object[] {
					festival.getNum(),
					valueOrDash(festival.getFestivalName()),
					valueOrDash(festival.getPlaceName()),
					festival.getRegionText(),
					festival.getPeriodText()
			});
		}

		festivalTable = new JTable(festivalModel);
		festivalTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		festivalTable.setRowHeight(25);
		festivalTable.setAutoCreateRowSorter(true);
		festivalTable.getColumnModel().getColumn(0).setPreferredWidth(45);
		festivalTable.getColumnModel().getColumn(1).setPreferredWidth(220);
		festivalTable.getColumnModel().getColumn(2).setPreferredWidth(150);
		festivalTable.getColumnModel().getColumn(3).setPreferredWidth(120);
		festivalTable.getColumnModel().getColumn(4).setPreferredWidth(170);
		festivalTable.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				showSelectedFestival();
			}
		});

		JPanel panel = new JPanel(new BorderLayout());
		panel.add(new JLabel("주변 축제 목록", SwingConstants.CENTER), BorderLayout.NORTH);
		panel.add(new JScrollPane(festivalTable), BorderLayout.CENTER);
		return panel;
	}

	private void showInitialFestival() {
		if (searchResult.getFestivals().isEmpty()) {
			clearDetail("검색된 축제가 없습니다.");
			return;
		}
		festivalTable.setRowSelectionInterval(0, 0);
		showFestival(searchResult.getFestivals().get(0));
	}

	private void showSelectedFestival() {
		int viewRow = festivalTable.getSelectedRow();
		if (viewRow < 0) {
			return;
		}
		int modelRow = festivalTable.convertRowIndexToModel(viewRow);
		showFestival(searchResult.getFestivals().get(modelRow));
	}

	private void showFestival(FestivalDto festival) {
		festivalNameValue.setText(valueOrDash(festival.getFestivalName()));
		festivalTypeValue.setText(valueOrDash(FestivalDto.cleanCode(festival.getFestivalType())));
		placeValue.setText(valueOrDash(festival.getPlaceName()));
		placeTypeValue.setText(valueOrDash(FestivalDto.cleanCode(festival.getPlaceType())));
		regionValue.setText(festival.getRegionText());
		periodValue.setText(festival.getPeriodText());
		totalDaysValue.setText(festival.getTotalDays() > 0 ? festival.getTotalDays() + "일" : "-");
		cycleValue.setText(valueOrDash(FestivalDto.cleanCode(festival.getCycle())));
		firstYearValue.setText(withUnit(festival.getFirstYear(), "년"));
		budgetValue.setText(withUnit(formatNumber(festival.getTotalBudget()), "백만원"));
		visitorsValue.setText(withUnit(formatNumber(festival.getPreviousVisitors()), "명"));
		organizationValue.setText(festival.getOrganizationText());
		contactValue.setText(valueOrDash(festival.getContact()));
		noteValue.setText(joinNotes(festival.getScheduleNote(), festival.getNote()));
		resetCaretPositions();
	}

	private void clearDetail(String message) {
		festivalNameValue.setText(message);
		JTextArea[] otherValues = {
				festivalTypeValue, placeValue, placeTypeValue, regionValue, periodValue,
				totalDaysValue, cycleValue, firstYearValue, budgetValue, visitorsValue,
				organizationValue, contactValue, noteValue
		};
		for (JTextArea value : otherValues) {
			value.setText("-");
		}
	}

	private void resetCaretPositions() {
		JTextArea[] values = {
				festivalNameValue, festivalTypeValue, placeValue, placeTypeValue, regionValue,
				periodValue, totalDaysValue, cycleValue, firstYearValue, budgetValue,
				visitorsValue, organizationValue, contactValue, noteValue
		};
		for (JTextArea value : values) {
			value.setCaretPosition(0);
		}
	}

	private String joinNotes(String first, String second) {
		String firstValue = valueOrDash(first);
		String secondValue = valueOrDash(second);
		if ("-".equals(firstValue)) {
			return secondValue;
		}
		if ("-".equals(secondValue) || firstValue.equals(secondValue)) {
			return firstValue;
		}
		return firstValue + "\n" + secondValue;
	}

	private String withUnit(String value, String unit) {
		String cleanValue = valueOrDash(value);
		return "-".equals(cleanValue) ? "-" : cleanValue + unit;
	}

	private String formatNumber(String value) {
		if (value == null || value.trim().isEmpty()) {
			return "";
		}
		try {
			return new DecimalFormat("#,##0.##").format(new BigDecimal(value.trim()));
		} catch (NumberFormatException e) {
			return value.trim();
		}
	}

	private static String valueOrDash(String value) {
		return value == null || value.trim().isEmpty() || "-".equals(value.trim())
				? "-" : value.trim();
	}

	private String rootMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current.getCause() != null) {
			current = current.getCause();
		}
		return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
	}
}
