package com.ssafy.trip.view.commercial;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * 현재 화면에 필요한 OpenStreetMap 타일만 비동기로 읽고 HTTP 캐시 정보를 보존한다.
 */
public final class OpenStreetMapTileProvider {

	public static final int TILE_SIZE = 256;
	public static final int MINIMUM_ZOOM = 3;
	public static final int MAXIMUM_ZOOM = 19;
	public static final String TILE_URL_PROPERTY = "enjoytrip.map.tile.url";
	public static final String USER_AGENT_PROPERTY = "enjoytrip.map.userAgent";

	private static final String DEFAULT_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
	private static final String DEFAULT_USER_AGENT =
			"EnjoyTrip-SSAFY/1.0 (Java Swing educational application)";
	private static final long DEFAULT_CACHE_MILLIS = 7L * 24L * 60L * 60L * 1_000L;
	private static final int MEMORY_CACHE_SIZE = 256;
	private static final OpenStreetMapTileProvider INSTANCE = new OpenStreetMapTileProvider();

	private final Map<TileKey, BufferedImage> memoryCache =
			Collections.synchronizedMap(new LinkedHashMap<TileKey, BufferedImage>(MEMORY_CACHE_SIZE, 0.75f, true) {
				private static final long serialVersionUID = 1L;

				@Override
				protected boolean removeEldestEntry(Map.Entry<TileKey, BufferedImage> eldest) {
					return size() > MEMORY_CACHE_SIZE;
				}
			});
	private final ConcurrentMap<TileKey, List<Runnable>> pendingLoads =
			new ConcurrentHashMap<TileKey, List<Runnable>>();
	private final ExecutorService loaderExecutor;
	private final Path diskCacheDirectory;
	private final String tileUrlTemplate;
	private final String userAgent;

	private OpenStreetMapTileProvider() {
		tileUrlTemplate = System.getProperty(TILE_URL_PROPERTY, DEFAULT_TILE_URL);
		userAgent = System.getProperty(USER_AGENT_PROPERTY, DEFAULT_USER_AGENT);
		diskCacheDirectory = Paths.get(System.getProperty("java.io.tmpdir"),
				"enjoytrip-osm-tile-cache").toAbsolutePath().normalize();
		loaderExecutor = Executors.newFixedThreadPool(4, new ThreadFactory() {
			private int number;

			@Override
			public synchronized Thread newThread(Runnable runnable) {
				Thread thread = new Thread(runnable, "osm-tile-loader-" + (++number));
				thread.setDaemon(true);
				return thread;
			}
		});
	}

	public static OpenStreetMapTileProvider getInstance() {
		return INSTANCE;
	}

	public BufferedImage getTile(int zoom, int tileX, int tileY, Runnable repaintCallback) {
		int tileCount = 1 << zoom;
		if (zoom < MINIMUM_ZOOM || zoom > MAXIMUM_ZOOM || tileY < 0 || tileY >= tileCount) {
			return null;
		}
		int normalizedX = ((tileX % tileCount) + tileCount) % tileCount;
		TileKey key = new TileKey(zoom, normalizedX, tileY);
		BufferedImage memoryImage = memoryCache.get(key);
		if (memoryImage != null) {
			return memoryImage;
		}
		requestTile(key, repaintCallback);
		return null;
	}

	public Path getDiskCacheDirectory() {
		return diskCacheDirectory;
	}

	private void requestTile(final TileKey key, Runnable repaintCallback) {
		List<Runnable> callbacks = Collections.synchronizedList(new ArrayList<Runnable>());
		if (repaintCallback != null) {
			callbacks.add(repaintCallback);
		}
		List<Runnable> existingCallbacks = pendingLoads.putIfAbsent(key, callbacks);
		if (existingCallbacks != null) {
			if (repaintCallback != null) {
				existingCallbacks.add(repaintCallback);
			}
			return;
		}

		loaderExecutor.execute(() -> {
			try {
				BufferedImage image = loadTile(key);
				if (image != null) {
					memoryCache.put(key, image);
				}
			} finally {
				final List<Runnable> completedCallbacks = pendingLoads.remove(key);
				if (completedCallbacks != null) {
					SwingUtilities.invokeLater(() -> {
						synchronized (completedCallbacks) {
							for (Runnable callback : completedCallbacks) {
								callback.run();
							}
						}
					});
				}
			}
		});
	}

	private BufferedImage loadTile(TileKey key) {
		Path imageFile = imageFile(key);
		CacheMetadata metadata = readMetadata(key);
		long now = System.currentTimeMillis();
		if (Files.isRegularFile(imageFile) && metadata.expiresAt > now) {
			BufferedImage cached = readImage(imageFile);
			if (cached != null) {
				return cached;
			}
		}

		BufferedImage downloaded = downloadTile(key, metadata, imageFile);
		if (downloaded != null) {
			return downloaded;
		}
		return readImage(imageFile);
	}

	private BufferedImage downloadTile(TileKey key, CacheMetadata metadata, Path imageFile) {
		HttpURLConnection connection = null;
		try {
			String urlText = tileUrlTemplate
					.replace("{z}", Integer.toString(key.zoom))
					.replace("{x}", Integer.toString(key.x))
					.replace("{y}", Integer.toString(key.y));
			connection = (HttpURLConnection) new URL(urlText).openConnection();
			connection.setConnectTimeout(5_000);
			connection.setReadTimeout(8_000);
			connection.setRequestProperty("User-Agent", userAgent);
			connection.setRequestProperty("Accept", "image/png,image/*;q=0.8");
			if (!metadata.etag.isEmpty()) {
				connection.setRequestProperty("If-None-Match", metadata.etag);
			}
			if (metadata.lastModified > 0L) {
				connection.setIfModifiedSince(metadata.lastModified);
			}

			int responseCode = connection.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED && Files.isRegularFile(imageFile)) {
				CacheMetadata refreshed = metadata.withExpiry(expiryFrom(connection));
				writeMetadata(key, refreshed);
				return readImage(imageFile);
			}
			if (responseCode != HttpURLConnection.HTTP_OK) {
				return null;
			}

			BufferedImage image;
			try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
				image = ImageIO.read(input);
			}
			if (image == null) {
				return null;
			}
			writeImage(imageFile, image);
			CacheMetadata downloadedMetadata = new CacheMetadata(
					expiryFrom(connection),
					safe(connection.getHeaderField("ETag")),
					connection.getLastModified());
			writeMetadata(key, downloadedMetadata);
			return image;
		} catch (IOException | RuntimeException e) {
			return null;
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	private long expiryFrom(HttpURLConnection connection) {
		long now = System.currentTimeMillis();
		String cacheControl = connection.getHeaderField("Cache-Control");
		if (cacheControl != null) {
			for (String directive : cacheControl.split(",")) {
				String trimmed = directive.trim();
				if (trimmed.startsWith("max-age=")) {
					try {
						return now + Long.parseLong(trimmed.substring("max-age=".length())) * 1_000L;
					} catch (NumberFormatException e) {
						break;
					}
				}
			}
		}
		long expires = connection.getExpiration();
		return expires > now ? expires : now + DEFAULT_CACHE_MILLIS;
	}

	private BufferedImage readImage(Path imageFile) {
		if (!Files.isRegularFile(imageFile)) {
			return null;
		}
		try (InputStream input = Files.newInputStream(imageFile)) {
			return ImageIO.read(input);
		} catch (IOException e) {
			return null;
		}
	}

	private void writeImage(Path imageFile, BufferedImage image) {
		try {
			Files.createDirectories(imageFile.getParent());
			Path temporaryFile = imageFile.resolveSibling(imageFile.getFileName().toString() + ".tmp");
			ImageIO.write(image, "png", temporaryFile.toFile());
			Files.move(temporaryFile, imageFile, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			// 메모리 캐시는 계속 사용할 수 있다.
		}
	}

	private CacheMetadata readMetadata(TileKey key) {
		Path metadataFile = metadataFile(key);
		if (!Files.isRegularFile(metadataFile)) {
			return CacheMetadata.EMPTY;
		}
		Properties properties = new Properties();
		try (BufferedReader reader = Files.newBufferedReader(metadataFile, StandardCharsets.UTF_8)) {
			properties.load(reader);
			return new CacheMetadata(
					parseLong(properties.getProperty("expiresAt")),
					safe(properties.getProperty("etag")),
					parseLong(properties.getProperty("lastModified")));
		} catch (IOException e) {
			return CacheMetadata.EMPTY;
		}
	}

	private void writeMetadata(TileKey key, CacheMetadata metadata) {
		Path metadataFile = metadataFile(key);
		Properties properties = new Properties();
		properties.setProperty("expiresAt", Long.toString(metadata.expiresAt));
		properties.setProperty("etag", metadata.etag);
		properties.setProperty("lastModified", Long.toString(metadata.lastModified));
		try {
			Files.createDirectories(metadataFile.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(metadataFile, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
				properties.store(writer, "OpenStreetMap HTTP cache metadata");
			}
		} catch (IOException e) {
			// 메모리 캐시는 계속 사용할 수 있다.
		}
	}

	private Path imageFile(TileKey key) {
		return diskCacheDirectory.resolve(Integer.toString(key.zoom))
				.resolve(Integer.toString(key.x)).resolve(key.y + ".png");
	}

	private Path metadataFile(TileKey key) {
		return diskCacheDirectory.resolve(Integer.toString(key.zoom))
				.resolve(Integer.toString(key.x)).resolve(key.y + ".properties");
	}

	private static long parseLong(String value) {
		try {
			return value == null ? 0L : Long.parseLong(value);
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private static final class CacheMetadata {

		private static final CacheMetadata EMPTY = new CacheMetadata(0L, "", 0L);
		private final long expiresAt;
		private final String etag;
		private final long lastModified;

		private CacheMetadata(long expiresAt, String etag, long lastModified) {
			this.expiresAt = expiresAt;
			this.etag = etag;
			this.lastModified = lastModified;
		}

		private CacheMetadata withExpiry(long expiry) {
			return new CacheMetadata(expiry, etag, lastModified);
		}
	}

	private static final class TileKey {

		private final int zoom;
		private final int x;
		private final int y;

		private TileKey(int zoom, int x, int y) {
			this.zoom = zoom;
			this.x = x;
			this.y = y;
		}

		@Override
		public int hashCode() {
			int result = zoom;
			result = 31 * result + x;
			result = 31 * result + y;
			return result;
		}

		@Override
		public boolean equals(Object object) {
			if (this == object) {
				return true;
			}
			if (!(object instanceof TileKey)) {
				return false;
			}
			TileKey other = (TileKey) object;
			return zoom == other.zoom && x == other.x && y == other.y;
		}
	}
}
