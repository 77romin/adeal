package com.ssafy.trip.view.commercial;

import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JDialog;
import javax.swing.JOptionPane;

import com.ssafy.trip.model.dto.MapBounds;
import com.ssafy.trip.model.dto.TripDto;
import com.ssafy.trip.model.service.CommercialAreaService;

public class CommercialAreaDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private final CommercialAreaPanel commercialAreaPanel;

	public CommercialAreaDialog(Frame owner, TripDto currentTrip, CommercialAreaService commercialAreaService) {
		super(owner, "관광지 주변 상권 조회", true);
		if (currentTrip == null || !MapBounds.isValidCoordinate(currentTrip.getLat(), currentTrip.getLng())) {
			throw new IllegalArgumentException("관광지의 위도와 경도가 올바르지 않습니다.");
		}
		if (commercialAreaService == null) {
			throw new IllegalArgumentException("상권 조회 서비스를 사용할 수 없습니다.");
		}
		commercialAreaPanel = new CommercialAreaPanel(currentTrip, commercialAreaService);
		setContentPane(commercialAreaPanel);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setSize(1_280, 760);
		setMinimumSize(new java.awt.Dimension(960, 620));
		setLocationRelativeTo(owner);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent event) {
				commercialAreaPanel.disposeResources();
			}
		});
	}

	public static boolean showFor(Frame owner, TripDto currentTrip, CommercialAreaService commercialAreaService) {
		if (currentTrip == null || !MapBounds.isValidCoordinate(currentTrip.getLat(), currentTrip.getLng())) {
			JOptionPane.showMessageDialog(owner, "관광지의 위도와 경도가 올바르지 않아 상권 지도를 열 수 없습니다.",
					"상권 조회", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		CommercialAreaDialog dialog = new CommercialAreaDialog(owner, currentTrip, commercialAreaService);
		dialog.setVisible(true);
		return true;
	}
}
