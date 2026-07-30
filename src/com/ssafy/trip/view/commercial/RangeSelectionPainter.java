package com.ssafy.trip.view.commercial;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public class RangeSelectionPainter {

	public void paint(Graphics2D graphics, Rectangle selection) {
		if (selection == null) {
			return;
		}
		Graphics2D g2 = (Graphics2D) graphics.create();
		try {
			g2.setColor(new Color(30, 136, 229, 45));
			g2.fill(selection);
			g2.setColor(new Color(21, 101, 192));
			g2.setStroke(new BasicStroke(2f));
			g2.draw(selection);
		} finally {
			g2.dispose();
		}
	}
}
