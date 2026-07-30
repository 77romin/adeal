package com.ssafy.trip.view.commercial;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ssafy.trip.model.dto.CommercialAreaDto;

/**
 * 현재 화면에 보이는 업소만 그리고, 가까운 마커는 화면 격자별로 묶는다.
 */
public class CommercialMarkerPainter {

	private static final int CLUSTER_CELL_PIXELS = 30;
	private static final Color STORE_COLOR = new Color(0, 121, 107);
	private static final Color STORE_BORDER_COLOR = new Color(0, 77, 64);
	private static final Color CLUSTER_COLOR = new Color(245, 124, 0);

	private List<MarkerHoverInfo> hitTargets = Collections.emptyList();

	public void paint(Graphics2D graphics, CommercialMapCanvas map,
			List<CommercialAreaDto> stores, double attractionLatitude, double attractionLongitude) {
		Graphics2D g2 = (Graphics2D) graphics.create();
		List<MarkerHoverInfo> newHitTargets = new ArrayList<MarkerHoverInfo>();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			Map<Long, Cluster> clusters = new LinkedHashMap<Long, Cluster>();
			for (CommercialAreaDto store : stores) {
				Point point = map.geoToPoint(store.getLatitude(), store.getLongitude());
				if (point.x < -16 || point.y < -16
						|| point.x > map.getWidth() + 16 || point.y > map.getHeight() + 16) {
					continue;
				}
				int gridX = Math.floorDiv(point.x, CLUSTER_CELL_PIXELS);
				int gridY = Math.floorDiv(point.y, CLUSTER_CELL_PIXELS);
				Long key = Long.valueOf(((long) gridX << 32) ^ (gridY & 0xffffffffL));
				Cluster cluster = clusters.get(key);
				if (cluster == null) {
					cluster = new Cluster(point.x, point.y, store);
					clusters.put(key, cluster);
				} else {
					cluster.add(point.x, point.y, store);
				}
			}

			for (Cluster cluster : clusters.values()) {
				int x = cluster.averageX();
				int y = cluster.averageY();
				if (cluster.count == 1) {
					paintStorePin(g2, x, y);
					newHitTargets.add(new MarkerHoverInfo(x, y - 5, 12,
							cluster.representative, cluster.count, cluster.sampleNames));
				} else {
					int diameter = cluster.count < 100 ? 22 : 27;
					g2.setColor(new Color(255, 255, 255, 235));
					g2.fillOval(x - diameter / 2 - 2, y - diameter / 2 - 2, diameter + 4, diameter + 4);
					g2.setColor(CLUSTER_COLOR);
					g2.fillOval(x - diameter / 2, y - diameter / 2, diameter, diameter);
					g2.setColor(new Color(230, 81, 0));
					g2.setStroke(new BasicStroke(1.2f));
					g2.drawOval(x - diameter / 2, y - diameter / 2, diameter, diameter);
					g2.setColor(Color.WHITE);
					g2.setFont(g2.getFont().deriveFont(Font.BOLD, 10f));
					String label = cluster.count > 999 ? "999+" : Integer.toString(cluster.count);
					int width = g2.getFontMetrics().stringWidth(label);
					g2.drawString(label, x - width / 2, y + 4);
					newHitTargets.add(new MarkerHoverInfo(x, y, diameter / 2 + 4,
							cluster.representative, cluster.count, cluster.sampleNames));
				}
			}

			Point attraction = map.geoToPoint(attractionLatitude, attractionLongitude);
			g2.setStroke(new BasicStroke(2f));
			g2.setColor(new Color(255, 255, 255, 235));
			g2.fillOval(attraction.x - 10, attraction.y - 10, 20, 20);
			g2.setColor(new Color(198, 40, 40));
			g2.fillOval(attraction.x - 7, attraction.y - 7, 14, 14);
			g2.setColor(Color.WHITE);
			g2.fillOval(attraction.x - 2, attraction.y - 2, 4, 4);
		} finally {
			hitTargets = newHitTargets;
			g2.dispose();
		}
	}

	public MarkerHoverInfo findHover(Point mousePoint) {
		MarkerHoverInfo closest = null;
		double closestDistance = Double.MAX_VALUE;
		for (MarkerHoverInfo target : hitTargets) {
			double distance = mousePoint.distance(target.x, target.y);
			if (distance <= target.hitRadius && distance < closestDistance) {
				closest = target;
				closestDistance = distance;
			}
		}
		return closest;
	}

	private static void paintStorePin(Graphics2D g2, int x, int y) {
		Path2D pin = new Path2D.Double();
		pin.moveTo(x, y + 6);
		pin.curveTo(x - 2, y + 1, x - 7, y - 2, x - 7, y - 7);
		pin.curveTo(x - 7, y - 12, x - 4, y - 15, x, y - 15);
		pin.curveTo(x + 4, y - 15, x + 7, y - 12, x + 7, y - 7);
		pin.curveTo(x + 7, y - 2, x + 2, y + 1, x, y + 6);
		pin.closePath();
		g2.setColor(new Color(255, 255, 255, 230));
		g2.setStroke(new BasicStroke(4f));
		g2.draw(pin);
		g2.setColor(STORE_COLOR);
		g2.fill(pin);
		g2.setColor(STORE_BORDER_COLOR);
		g2.setStroke(new BasicStroke(1.2f));
		g2.draw(pin);
		g2.setColor(Color.WHITE);
		g2.fillOval(x - 2, y - 9, 4, 4);
	}

	public static final class MarkerHoverInfo {

		private final int x;
		private final int y;
		private final int hitRadius;
		private final CommercialAreaDto representative;
		private final int count;
		private final List<String> sampleNames;

		private MarkerHoverInfo(int x, int y, int hitRadius, CommercialAreaDto representative,
				int count, List<String> sampleNames) {
			this.x = x;
			this.y = y;
			this.hitRadius = hitRadius;
			this.representative = representative;
			this.count = count;
			this.sampleNames = Collections.unmodifiableList(new ArrayList<String>(sampleNames));
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

		public CommercialAreaDto getRepresentative() {
			return representative;
		}

		public int getCount() {
			return count;
		}

		public List<String> getSampleNames() {
			return sampleNames;
		}
	}

	private static final class Cluster {

		private int xSum;
		private int ySum;
		private int count = 1;
		private final CommercialAreaDto representative;
		private final List<String> sampleNames = new ArrayList<String>();

		private Cluster(int x, int y, CommercialAreaDto representative) {
			xSum = x;
			ySum = y;
			this.representative = representative;
			addSampleName(representative);
		}

		private void add(int x, int y, CommercialAreaDto store) {
			xSum += x;
			ySum += y;
			count++;
			addSampleName(store);
		}

		private void addSampleName(CommercialAreaDto store) {
			if (sampleNames.size() < 3 && store.getName() != null && !store.getName().trim().isEmpty()) {
				sampleNames.add(store.getName());
			}
		}

		private int averageX() {
			return xSum / count;
		}

		private int averageY() {
			return ySum / count;
		}
	}
}
