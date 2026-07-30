package com.ssafy.trip;

import javax.swing.SwingUtilities;

import com.ssafy.trip.view.TripInfoView;

public class Main {

	public static void main(String[] args) {
		SwingUtilities.invokeLater(TripInfoView::new);
	}
	
}
