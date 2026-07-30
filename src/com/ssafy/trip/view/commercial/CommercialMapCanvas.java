package com.ssafy.trip.view.commercial;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

import javax.swing.JPanel;

import com.ssafy.trip.model.dto.CommercialAreaDto;
import com.ssafy.trip.model.dto.MapBounds;
import com.ssafy.trip.view.commercial.CommercialMarkerPainter.MarkerHoverInfo;

/**
 * OpenStreetMap 타일과 동일한 Web Mercator 투영을 사용하는 Swing 지도다.
 */
public class CommercialMapCanvas extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final double EARTH_CIRCUMFERENCE_METERS = 40_075_016.686;
	private static final double MAXIMUM_MERCATOR_LATITUDE = 85.05112878;

	public enum InteractionMode {
		MOVE, SELECT_RANGE
	}

	public interface RangeSelectionListener {
		void rangeChanged(MapBounds bounds, boolean finished);
	}

	private final double attractionLatitude;
	private final double attractionLongitude;
	private final CommercialMarkerPainter markerPainter = new CommercialMarkerPainter();
	private final MarkerTooltipPainter tooltipPainter = new MarkerTooltipPainter();
	private final RangeSelectionPainter selectionPainter = new RangeSelectionPainter();
	private final OpenStreetMapTileProvider tileProvider = OpenStreetMapTileProvider.getInstance();

	private List<CommercialAreaDto> stores = Collections.emptyList();
	private double centerLatitude;
	private double centerLongitude;
	private int zoom = 15;
	private Double radiusMeters = Double.valueOf(1_000.0);
	private InteractionMode mode = InteractionMode.MOVE;
	private RangeSelectionListener rangeSelectionListener;
	private Point dragStart;
	private double dragStartCenterWorldX;
	private double dragStartCenterWorldY;
	private Point selectionStart;
	private Rectangle selectionRectangle;
	private MarkerHoverInfo hoveredMarker;

	public CommercialMapCanvas(double attractionLatitude, double attractionLongitude) {
		this.attractionLatitude = attractionLatitude;
		this.attractionLongitude = attractionLongitude;
		this.centerLatitude = attractionLatitude;
		this.centerLongitude = attractionLongitude;
		setPreferredSize(new Dimension(760, 600));
		setMinimumSize(new Dimension(420, 360));
		setBackground(new Color(232, 235, 238));
		installMouseControls();
	}

	public void setStores(List<CommercialAreaDto> stores) {
		this.stores = stores == null ? Collections.<CommercialAreaDto>emptyList() : stores;
		hoveredMarker = null;
		repaint();
	}

	public void setInteractionMode(InteractionMode mode) {
		this.mode = mode;
		hoveredMarker = null;
		updateCursor();
		repaint();
	}

	public void setRangeSelectionListener(RangeSelectionListener listener) {
		this.rangeSelectionListener = listener;
	}

	public void resetViewToRadius(double radius) {
		centerLatitude = attractionLatitude;
		centerLongitude = attractionLongitude;
		radiusMeters = Double.valueOf(radius);
		selectionRectangle = null;
		hoveredMarker = null;
		int smallerSide = Math.max(300, Math.min(getWidth(), getHeight()));
		double requiredMetersPerPixel = radius / (smallerSide * 0.45);
		zoom = bestZoomFor(requiredMetersPerPixel, centerLatitude);
		repaint();
	}

	public void clearRadiusOverlay() {
		radiusMeters = null;
		repaint();
	}

	public int getZoom() {
		return zoom;
	}

	public Point geoToPoint(double latitude, double longitude) {
		double worldSize = worldSize(zoom);
		double centerX = longitudeToWorldX(centerLongitude, zoom);
		double centerY = latitudeToWorldY(centerLatitude, zoom);
		double pointX = longitudeToWorldX(longitude, zoom);
		double pointY = latitudeToWorldY(latitude, zoom);
		double deltaX = pointX - centerX;
		if (deltaX > worldSize / 2.0) {
			deltaX -= worldSize;
		} else if (deltaX < -worldSize / 2.0) {
			deltaX += worldSize;
		}
		return new Point(
				(int) Math.round(getWidth() / 2.0 + deltaX),
				(int) Math.round(getHeight() / 2.0 + pointY - centerY));
	}

	public MapBounds getVisibleBounds() {
		double[] northwest = pointToGeo(new Point(0, 0));
		double[] southeast = pointToGeo(new Point(getWidth(), getHeight()));
		return MapBounds.of(northwest[0], northwest[1], southeast[0], southeast[1]);
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		Graphics2D g2 = (Graphics2D) graphics.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int loadedTileCount = paintMapTiles(g2);
			if (loadedTileCount == 0) {
				paintCoordinateGrid(g2);
				paintTileLoadingMessage(g2);
			}
			paintRadius(g2);
			markerPainter.paint(g2, this, stores, attractionLatitude, attractionLongitude);
			selectionPainter.paint(g2, selectionRectangle);
			tooltipPainter.paint(g2, hoveredMarker, getWidth(), getHeight());
			paintScaleAndAttribution(g2);
		} finally {
			g2.dispose();
		}
	}

	private int paintMapTiles(Graphics2D g2) {
		double centerWorldX = longitudeToWorldX(centerLongitude, zoom);
		double centerWorldY = latitudeToWorldY(centerLatitude, zoom);
		double topLeftWorldX = centerWorldX - getWidth() / 2.0;
		double topLeftWorldY = centerWorldY - getHeight() / 2.0;
		int firstTileX = (int) Math.floor(topLeftWorldX / OpenStreetMapTileProvider.TILE_SIZE);
		int lastTileX = (int) Math.floor(
				(topLeftWorldX + getWidth()) / OpenStreetMapTileProvider.TILE_SIZE);
		int firstTileY = (int) Math.floor(topLeftWorldY / OpenStreetMapTileProvider.TILE_SIZE);
		int lastTileY = (int) Math.floor(
				(topLeftWorldY + getHeight()) / OpenStreetMapTileProvider.TILE_SIZE);
		int loaded = 0;

		for (int tileY = firstTileY; tileY <= lastTileY; tileY++) {
			for (int tileX = firstTileX; tileX <= lastTileX; tileX++) {
				int screenX = (int) Math.round(
						tileX * OpenStreetMapTileProvider.TILE_SIZE - topLeftWorldX);
				int screenY = (int) Math.round(
						tileY * OpenStreetMapTileProvider.TILE_SIZE - topLeftWorldY);
				BufferedImage tile = tileProvider.getTile(zoom, tileX, tileY, this::repaint);
				if (tile != null) {
					g2.drawImage(tile, screenX, screenY,
							OpenStreetMapTileProvider.TILE_SIZE, OpenStreetMapTileProvider.TILE_SIZE, null);
					loaded++;
				} else {
					paintTilePlaceholder(g2, screenX, screenY, tileX, tileY);
				}
			}
		}
		return loaded;
	}

	private void installMouseControls() {
		MouseAdapter mouseControls = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent event) {
				hoveredMarker = null;
				if (mode == InteractionMode.MOVE) {
					dragStart = event.getPoint();
					dragStartCenterWorldX = longitudeToWorldX(centerLongitude, zoom);
					dragStartCenterWorldY = latitudeToWorldY(centerLatitude, zoom);
					selectionRectangle = null;
				} else {
					selectionStart = event.getPoint();
					selectionRectangle = new Rectangle(selectionStart);
					radiusMeters = null;
					repaint();
				}
			}

			@Override
			public void mouseDragged(MouseEvent event) {
				hoveredMarker = null;
				if (mode == InteractionMode.MOVE && dragStart != null) {
					double newCenterWorldX = dragStartCenterWorldX - (event.getX() - dragStart.x);
					double newCenterWorldY = dragStartCenterWorldY - (event.getY() - dragStart.y);
					double[] center = worldToGeo(newCenterWorldX, newCenterWorldY, zoom);
					centerLatitude = center[0];
					centerLongitude = center[1];
					repaint();
				} else if (mode == InteractionMode.SELECT_RANGE && selectionStart != null) {
					selectionRectangle = normalizedRectangle(selectionStart, event.getPoint());
					notifyRangeChanged(false);
					repaint();
				}
			}

			@Override
			public void mouseReleased(MouseEvent event) {
				dragStart = null;
				if (mode == InteractionMode.SELECT_RANGE && selectionStart != null) {
					selectionRectangle = normalizedRectangle(selectionStart, event.getPoint());
					selectionStart = null;
					notifyRangeChanged(true);
					repaint();
				}
			}

			@Override
			public void mouseMoved(MouseEvent event) {
				MarkerHoverInfo nextHover = mode == InteractionMode.MOVE
						? markerPainter.findHover(event.getPoint()) : null;
				if (!sameHover(hoveredMarker, nextHover)) {
					hoveredMarker = nextHover;
					updateCursor();
					repaint();
				}
			}

			@Override
			public void mouseExited(MouseEvent event) {
				if (hoveredMarker != null) {
					hoveredMarker = null;
					updateCursor();
					repaint();
				}
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent event) {
				double[] coordinateUnderMouse = pointToGeo(event.getPoint());
				int direction = event.getPreciseWheelRotation() > 0.0 ? -1 : 1;
				int newZoom = Math.max(OpenStreetMapTileProvider.MINIMUM_ZOOM,
						Math.min(OpenStreetMapTileProvider.MAXIMUM_ZOOM, zoom + direction));
				if (newZoom == zoom) {
					return;
				}
				double anchorWorldX = longitudeToWorldX(coordinateUnderMouse[1], newZoom);
				double anchorWorldY = latitudeToWorldY(coordinateUnderMouse[0], newZoom);
				double centerWorldX = anchorWorldX - (event.getX() - getWidth() / 2.0);
				double centerWorldY = anchorWorldY - (event.getY() - getHeight() / 2.0);
				double[] center = worldToGeo(centerWorldX, centerWorldY, newZoom);
				zoom = newZoom;
				centerLatitude = center[0];
				centerLongitude = center[1];
				hoveredMarker = null;
				selectionRectangle = null;
				repaint();
			}
		};
		addMouseListener(mouseControls);
		addMouseMotionListener(mouseControls);
		addMouseWheelListener(mouseControls);
		setInteractionMode(InteractionMode.MOVE);
	}

	private void notifyRangeChanged(boolean finished) {
		if (selectionRectangle == null || selectionRectangle.width < 2 || selectionRectangle.height < 2
				|| rangeSelectionListener == null) {
			return;
		}
		double[] corner1 = pointToGeo(new Point(selectionRectangle.x, selectionRectangle.y));
		double[] corner2 = pointToGeo(new Point(selectionRectangle.x + selectionRectangle.width,
				selectionRectangle.y + selectionRectangle.height));
		rangeSelectionListener.rangeChanged(
				MapBounds.of(corner1[0], corner1[1], corner2[0], corner2[1]), finished);
	}

	private double[] pointToGeo(Point point) {
		double centerWorldX = longitudeToWorldX(centerLongitude, zoom);
		double centerWorldY = latitudeToWorldY(centerLatitude, zoom);
		double worldX = centerWorldX + point.x - getWidth() / 2.0;
		double worldY = centerWorldY + point.y - getHeight() / 2.0;
		return worldToGeo(worldX, worldY, zoom);
	}

	private void paintTilePlaceholder(Graphics2D g2, int x, int y, int tileX, int tileY) {
		boolean alternate = ((tileX + tileY) & 1) == 0;
		g2.setColor(alternate ? new Color(235, 238, 240) : new Color(229, 233, 236));
		g2.fillRect(x, y, OpenStreetMapTileProvider.TILE_SIZE, OpenStreetMapTileProvider.TILE_SIZE);
		g2.setColor(new Color(205, 211, 215));
		g2.drawRect(x, y, OpenStreetMapTileProvider.TILE_SIZE, OpenStreetMapTileProvider.TILE_SIZE);
	}

	private void paintCoordinateGrid(Graphics2D g2) {
		g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 10f));
		double metersPerPixel = metersPerPixel(centerLatitude, zoom);
		double desiredMeters = metersPerPixel * 110.0;
		double gridMeters = niceDistance(desiredMeters);
		double latitudeStep = gridMeters / 111_320.0;
		double longitudeStep = gridMeters
				/ (111_320.0 * Math.max(0.000001, Math.cos(Math.toRadians(centerLatitude))));
		MapBounds visible = getVisibleBounds();

		g2.setStroke(new BasicStroke(1f));
		g2.setColor(new Color(160, 168, 174, 115));
		double firstLatitude = Math.ceil(visible.getMinLatitude() / latitudeStep) * latitudeStep;
		for (double latitude = firstLatitude; latitude <= visible.getMaxLatitude(); latitude += latitudeStep) {
			Point point = geoToPoint(latitude, centerLongitude);
			g2.drawLine(0, point.y, getWidth(), point.y);
			g2.setColor(new Color(90, 100, 108));
			g2.drawString(String.format("%.5f°", latitude), 6, point.y - 3);
			g2.setColor(new Color(160, 168, 174, 115));
		}

		double firstLongitude = Math.ceil(visible.getMinLongitude() / longitudeStep) * longitudeStep;
		for (double longitude = firstLongitude; longitude <= visible.getMaxLongitude(); longitude += longitudeStep) {
			Point point = geoToPoint(centerLatitude, longitude);
			g2.drawLine(point.x, 0, point.x, getHeight());
			g2.setColor(new Color(90, 100, 108));
			g2.drawString(String.format("%.5f°", longitude), point.x + 3, 14);
			g2.setColor(new Color(160, 168, 174, 115));
		}
	}

	private void paintTileLoadingMessage(Graphics2D g2) {
		String text = "실제 지도 타일 로딩 중 · 인터넷 연결이 없으면 좌표 지도로 표시됩니다.";
		g2.setFont(g2.getFont().deriveFont(Font.BOLD, 12f));
		int width = g2.getFontMetrics().stringWidth(text) + 22;
		g2.setColor(new Color(255, 255, 255, 225));
		g2.fillRoundRect(12, 12, width, 31, 10, 10);
		g2.setColor(new Color(55, 71, 79));
		g2.drawString(text, 23, 33);
	}

	private void paintRadius(Graphics2D g2) {
		if (radiusMeters == null) {
			return;
		}
		Point center = geoToPoint(attractionLatitude, attractionLongitude);
		int radiusPixels = (int) Math.round(radiusMeters.doubleValue()
				/ metersPerPixel(attractionLatitude, zoom));
		g2.setColor(new Color(30, 136, 229, 28));
		g2.fillOval(center.x - radiusPixels, center.y - radiusPixels, radiusPixels * 2, radiusPixels * 2);
		g2.setColor(new Color(30, 136, 229, 175));
		g2.setStroke(new BasicStroke(2f));
		g2.drawOval(center.x - radiusPixels, center.y - radiusPixels, radiusPixels * 2, radiusPixels * 2);
	}

	private void paintScaleAndAttribution(Graphics2D g2) {
		double currentMetersPerPixel = metersPerPixel(centerLatitude, zoom);
		double scaleMeters = niceDistance(100.0 * currentMetersPerPixel);
		int scalePixels = (int) Math.round(scaleMeters / currentMetersPerPixel);
		int x = 18;
		int y = getHeight() - 24;
		g2.setColor(new Color(255, 255, 255, 225));
		g2.fillRoundRect(8, getHeight() - 52, Math.max(145, scalePixels + 28), 42, 8, 8);
		g2.setColor(new Color(55, 71, 79));
		g2.setStroke(new BasicStroke(3f));
		g2.drawLine(x, y, x + scalePixels, y);
		g2.drawLine(x, y - 4, x, y + 4);
		g2.drawLine(x + scalePixels, y - 4, x + scalePixels, y + 4);
		String scaleLabel = scaleMeters >= 1_000.0
				? String.format("%.1f km", scaleMeters / 1_000.0)
				: String.format("%.0f m", scaleMeters);
		g2.drawString(scaleLabel + " · z" + zoom, x, y - 8);

		String attribution = "© OpenStreetMap contributors · openstreetmap.org/copyright";
		g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 11f));
		int attributionWidth = g2.getFontMetrics().stringWidth(attribution) + 16;
		int attributionX = Math.max(8, getWidth() - attributionWidth - 8);
		g2.setColor(new Color(255, 255, 255, 225));
		g2.fillRoundRect(attributionX, getHeight() - 31, attributionWidth, 22, 7, 7);
		g2.setColor(new Color(45, 55, 60));
		g2.drawString(attribution, attributionX + 8, getHeight() - 16);
	}

	private void updateCursor() {
		if (mode == InteractionMode.SELECT_RANGE) {
			setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
		} else if (hoveredMarker != null) {
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		} else {
			setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
		}
	}

	private static boolean sameHover(MarkerHoverInfo first, MarkerHoverInfo second) {
		if (first == second) {
			return true;
		}
		if (first == null || second == null || first.getCount() != second.getCount()) {
			return false;
		}
		String firstId = first.getRepresentative().getId();
		String secondId = second.getRepresentative().getId();
		return firstId == null ? secondId == null : firstId.equals(secondId);
	}

	private static int bestZoomFor(double requiredMetersPerPixel, double latitude) {
		int selected = OpenStreetMapTileProvider.MINIMUM_ZOOM;
		for (int candidate = OpenStreetMapTileProvider.MINIMUM_ZOOM;
				candidate <= OpenStreetMapTileProvider.MAXIMUM_ZOOM; candidate++) {
			if (metersPerPixel(latitude, candidate) >= requiredMetersPerPixel) {
				selected = candidate;
			} else {
				break;
			}
		}
		return selected;
	}

	private static double longitudeToWorldX(double longitude, int zoom) {
		return (normalizeLongitude(longitude) + 180.0) / 360.0 * worldSize(zoom);
	}

	private static double latitudeToWorldY(double latitude, int zoom) {
		double limitedLatitude = Math.max(-MAXIMUM_MERCATOR_LATITUDE,
				Math.min(MAXIMUM_MERCATOR_LATITUDE, latitude));
		double latitudeRadians = Math.toRadians(limitedLatitude);
		double mercator = Math.log(Math.tan(latitudeRadians)
				+ 1.0 / Math.cos(latitudeRadians));
		return (1.0 - mercator / Math.PI) / 2.0 * worldSize(zoom);
	}

	private static double[] worldToGeo(double worldX, double worldY, int zoom) {
		double size = worldSize(zoom);
		double wrappedX = ((worldX % size) + size) % size;
		double limitedY = Math.max(0.0, Math.min(size, worldY));
		double longitude = wrappedX / size * 360.0 - 180.0;
		double mercator = Math.PI * (1.0 - 2.0 * limitedY / size);
		double latitude = Math.toDegrees(Math.atan(Math.sinh(mercator)));
		return new double[] { latitude, longitude };
	}

	private static double worldSize(int zoom) {
		return OpenStreetMapTileProvider.TILE_SIZE * Math.pow(2.0, zoom);
	}

	private static double metersPerPixel(double latitude, int zoom) {
		return Math.cos(Math.toRadians(latitude)) * EARTH_CIRCUMFERENCE_METERS / worldSize(zoom);
	}

	private static double normalizeLongitude(double longitude) {
		double normalized = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
		return normalized == -180.0 && longitude > 0.0 ? 180.0 : normalized;
	}

	private static Rectangle normalizedRectangle(Point first, Point second) {
		int x = Math.min(first.x, second.x);
		int y = Math.min(first.y, second.y);
		return new Rectangle(x, y, Math.abs(first.x - second.x), Math.abs(first.y - second.y));
	}

	private static double niceDistance(double distance) {
		double power = Math.pow(10.0, Math.floor(Math.log10(Math.max(1.0, distance))));
		double normalized = distance / power;
		double multiplier = normalized <= 1.0 ? 1.0
				: normalized <= 2.0 ? 2.0 : normalized <= 5.0 ? 5.0 : 10.0;
		return multiplier * power;
	}
}
