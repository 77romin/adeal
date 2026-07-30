package com.ssafy.trip.view.commercial;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.List;

import com.ssafy.trip.model.dto.CommercialAreaDto;
import com.ssafy.trip.view.commercial.CommercialMarkerPainter.MarkerHoverInfo;

/**
 * 마커 위에 상호·업종·주소를 말풍선 형태로 표시한다.
 */
public class MarkerTooltipPainter {

	private static final int BUBBLE_WIDTH = 330;
	private static final int BUBBLE_HEIGHT = 92;
	private static final int PADDING = 12;

	public void paint(Graphics2D graphics, MarkerHoverInfo hover, int canvasWidth, int canvasHeight) {
		if (hover == null) {
			return;
		}
		Graphics2D g2 = (Graphics2D) graphics.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int anchorX = hover.getX();
			int anchorY = hover.getY();
			int bubbleX = anchorX + 14;
			if (bubbleX + BUBBLE_WIDTH > canvasWidth - 8) {
				bubbleX = anchorX - BUBBLE_WIDTH - 14;
			}
			bubbleX = Math.max(8, Math.min(bubbleX, canvasWidth - BUBBLE_WIDTH - 8));
			int bubbleY = anchorY - BUBBLE_HEIGHT - 16;
			if (bubbleY < 8) {
				bubbleY = anchorY + 18;
			}
			bubbleY = Math.max(8, Math.min(bubbleY, canvasHeight - BUBBLE_HEIGHT - 8));

			g2.setColor(new Color(0, 0, 0, 48));
			g2.fillRoundRect(bubbleX + 3, bubbleY + 4, BUBBLE_WIDTH, BUBBLE_HEIGHT, 14, 14);
			g2.setColor(new Color(255, 255, 255, 246));
			g2.fillRoundRect(bubbleX, bubbleY, BUBBLE_WIDTH, BUBBLE_HEIGHT, 14, 14);
			g2.setColor(new Color(55, 71, 79));
			g2.setStroke(new BasicStroke(1.2f));
			g2.drawRoundRect(bubbleX, bubbleY, BUBBLE_WIDTH, BUBBLE_HEIGHT, 14, 14);

			Polygon pointer = pointer(anchorX, anchorY, bubbleX, bubbleY);
			g2.setColor(new Color(255, 255, 255, 246));
			g2.fillPolygon(pointer);
			g2.setColor(new Color(55, 71, 79));
			g2.drawLine(pointer.xpoints[0], pointer.ypoints[0], pointer.xpoints[1], pointer.ypoints[1]);
			g2.drawLine(pointer.xpoints[1], pointer.ypoints[1], pointer.xpoints[2], pointer.ypoints[2]);

			CommercialAreaDto store = hover.getRepresentative();
			g2.setFont(g2.getFont().deriveFont(Font.BOLD, 14f));
			g2.setColor(new Color(25, 35, 40));
			String title = hover.getCount() == 1
					? safe(store.getName())
					: safe(store.getName()) + " 외 " + (hover.getCount() - 1) + "개 업소";
			g2.drawString(ellipsize(title, g2.getFontMetrics(), BUBBLE_WIDTH - PADDING * 2),
					bubbleX + PADDING, bubbleY + 23);

			g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12f));
			g2.setColor(new Color(0, 105, 92));
			String category = joinNonEmpty(" > ", store.getLargeCategory(),
					store.getMiddleCategory(), store.getSmallCategory());
			g2.drawString(ellipsize(category, g2.getFontMetrics(), BUBBLE_WIDTH - PADDING * 2),
					bubbleX + PADDING, bubbleY + 46);

			g2.setColor(new Color(69, 79, 84));
			String detail = hover.getCount() == 1
					? safe(store.getDisplayAddress())
					: sampleSummary(hover.getSampleNames());
			g2.drawString(ellipsize(detail, g2.getFontMetrics(), BUBBLE_WIDTH - PADDING * 2),
					bubbleX + PADDING, bubbleY + 69);
		} finally {
			g2.dispose();
		}
	}

	private static Polygon pointer(int anchorX, int anchorY, int bubbleX, int bubbleY) {
		boolean bubbleIsRight = bubbleX > anchorX;
		boolean bubbleIsBelow = bubbleY > anchorY;
		int edgeX = bubbleIsRight ? bubbleX : bubbleX + BUBBLE_WIDTH;
		int edgeY = bubbleIsBelow ? bubbleY + 18 : bubbleY + BUBBLE_HEIGHT - 18;
		int[] xPoints = bubbleIsRight
				? new int[] { edgeX, anchorX, edgeX }
				: new int[] { edgeX, anchorX, edgeX };
		int[] yPoints = new int[] { edgeY - 7, anchorY, edgeY + 7 };
		return new Polygon(xPoints, yPoints, 3);
	}

	private static String sampleSummary(List<String> names) {
		StringBuilder text = new StringBuilder("포함 업소: ");
		for (String name : names) {
			if (text.length() > "포함 업소: ".length()) {
				text.append(", ");
			}
			text.append(name);
		}
		return text.toString();
	}

	private static String joinNonEmpty(String separator, String... values) {
		StringBuilder text = new StringBuilder();
		for (String value : values) {
			if (value == null || value.trim().isEmpty()) {
				continue;
			}
			if (text.length() > 0) {
				text.append(separator);
			}
			text.append(value);
		}
		return text.toString();
	}

	private static String ellipsize(String text, FontMetrics metrics, int maximumWidth) {
		if (metrics.stringWidth(text) <= maximumWidth) {
			return text;
		}
		String suffix = "...";
		int end = text.length();
		while (end > 0 && metrics.stringWidth(text.substring(0, end) + suffix) > maximumWidth) {
			end--;
		}
		return text.substring(0, end) + suffix;
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
