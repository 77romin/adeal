package com.ssafy.trip.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ssafy.trip.model.dto.CommercialAreaDto;
import com.ssafy.trip.model.dto.TripDto;

/**
 * UTF-8 RFC4180 CSV를 스트리밍으로 읽는다. 대용량 파일에서 필요한 컬럼만 문자열로 만든다.
 */
public class CommercialAreaFileLoader {

	public static final String DATA_DIRECTORY_PROPERTY = "enjoytrip.commercial.data.dir";
	public static final String DATA_DIRECTORY_ENVIRONMENT = "ENJOYTRIP_COMMERCIAL_DATA_DIR";

	private static final Map<String, String[]> REGION_ALIASES = createRegionAliases();
	private final Path dataDirectory;

	public CommercialAreaFileLoader() {
		this(resolveDataDirectory());
	}

	public CommercialAreaFileLoader(Path dataDirectory) {
		this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
	}

	public List<CommercialAreaDto> loadRegion(String region) throws IOException {
		Path csvFile = findRegionFile(region);
		List<CommercialAreaDto> stores = new ArrayList<CommercialAreaDto>();
		Map<String, String> categoryPool = new HashMap<String, String>();

		try (Reader reader = new InputStreamReader(
				new BufferedInputStream(Files.newInputStream(csvFile), 128 * 1024), StandardCharsets.UTF_8);
				CsvRecordReader csv = new CsvRecordReader(reader)) {
			String[] header = csv.readRecord();
			if (header == null) {
				throw new IOException("비어 있는 상권정보 파일입니다: " + csvFile);
			}
			header[0] = removeBom(header[0]);
			Map<String, Integer> columnIndexes = toColumnIndexes(header);

			int idIndex = requireColumn(columnIndexes, "상가업소번호", csvFile);
			int nameIndex = requireColumn(columnIndexes, "상호명", csvFile);
			int largeIndex = requireColumn(columnIndexes, "상권업종대분류명", csvFile);
			int middleIndex = requireColumn(columnIndexes, "상권업종중분류명", csvFile);
			int smallIndex = requireColumn(columnIndexes, "상권업종소분류명", csvFile);
			int lotAddressIndex = requireColumn(columnIndexes, "지번주소", csvFile);
			int roadAddressIndex = requireColumn(columnIndexes, "도로명주소", csvFile);
			int longitudeIndex = requireColumn(columnIndexes, "경도", csvFile);
			int latitudeIndex = requireColumn(columnIndexes, "위도", csvFile);

			int[] selectedIndexes = { idIndex, nameIndex, largeIndex, middleIndex, smallIndex,
					lotAddressIndex, roadAddressIndex, longitudeIndex, latitudeIndex };
			Arrays.sort(selectedIndexes);

			String[] values;
			int recordNumber = 1;
			while ((values = csv.readRecord(selectedIndexes)) != null) {
				recordNumber++;
				try {
					double longitude = Double.parseDouble(value(values, selectedIndexes, longitudeIndex));
					double latitude = Double.parseDouble(value(values, selectedIndexes, latitudeIndex));
					stores.add(new CommercialAreaDto(
							value(values, selectedIndexes, idIndex),
							value(values, selectedIndexes, nameIndex),
							pooled(categoryPool, value(values, selectedIndexes, largeIndex)),
							pooled(categoryPool, value(values, selectedIndexes, middleIndex)),
							pooled(categoryPool, value(values, selectedIndexes, smallIndex)),
							value(values, selectedIndexes, lotAddressIndex),
							value(values, selectedIndexes, roadAddressIndex),
							latitude, longitude));
				} catch (NumberFormatException e) {
					throw new IOException(csvFile.getFileName() + "의 " + recordNumber
							+ "번째 레코드 좌표를 읽을 수 없습니다.", e);
				}
			}
		}
		return stores;
	}

	public String resolveRegion(TripDto trip) throws IOException {
		String address = "";
		if (trip != null) {
			address = safe(trip.getStreetAddress()) + " " + safe(trip.getLotAddress());
		}
		for (Map.Entry<String, String[]> entry : REGION_ALIASES.entrySet()) {
			for (String alias : entry.getValue()) {
				if (address.contains(alias)) {
					return entry.getKey();
				}
			}
		}
		throw new IOException("관광지 주소에서 시·도를 확인할 수 없습니다: " + address.trim());
	}

	public Path getDataDirectory() {
		return dataDirectory;
	}

	public static String[] parseCsvRecord(String record) throws IOException {
		try (CsvRecordReader reader = new CsvRecordReader(new StringReader(record))) {
			return reader.readRecord();
		}
	}

	private Path findRegionFile(String region) throws IOException {
		if (!Files.isDirectory(dataDirectory)) {
			throw new IOException("상권정보 폴더를 찾을 수 없습니다: " + dataDirectory);
		}
		String marker = "_" + region + "_";
		try (DirectoryStream<Path> files = Files.newDirectoryStream(dataDirectory, "*.csv")) {
			for (Path file : files) {
				if (file.getFileName().toString().contains(marker)) {
					return file;
				}
			}
		}
		throw new IOException(region + " 상권정보 CSV를 찾을 수 없습니다: " + dataDirectory);
	}

	private static Path resolveDataDirectory() {
		String configured = System.getProperty(DATA_DIRECTORY_PROPERTY);
		if (configured == null || configured.trim().isEmpty()) {
			configured = System.getenv(DATA_DIRECTORY_ENVIRONMENT);
		}
		if (configured != null && !configured.trim().isEmpty()) {
			return Paths.get(configured.trim());
		}

		return ProjectResourceLocator.resolve("res", "상권정보");
	}

	private static Map<String, Integer> toColumnIndexes(String[] header) {
		Map<String, Integer> indexes = new HashMap<String, Integer>();
		for (int i = 0; i < header.length; i++) {
			indexes.put(header[i], Integer.valueOf(i));
		}
		return indexes;
	}

	private static int requireColumn(Map<String, Integer> indexes, String name, Path file) throws IOException {
		Integer index = indexes.get(name);
		if (index == null) {
			throw new IOException(file.getFileName() + "에 필수 컬럼이 없습니다: " + name);
		}
		return index.intValue();
	}

	private static String value(String[] selectedValues, int[] selectedIndexes, int originalIndex) {
		int position = Arrays.binarySearch(selectedIndexes, originalIndex);
		return position < 0 || selectedValues[position] == null ? "" : selectedValues[position];
	}

	private static String pooled(Map<String, String> pool, String value) {
		String existing = pool.get(value);
		if (existing != null) {
			return existing;
		}
		pool.put(value, value);
		return value;
	}

	private static String removeBom(String text) {
		return text != null && !text.isEmpty() && text.charAt(0) == '\ufeff' ? text.substring(1) : text;
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private static Map<String, String[]> createRegionAliases() {
		Map<String, String[]> aliases = new LinkedHashMap<String, String[]>();
		aliases.put("강원", new String[] { "강원특별자치도", "강원도" });
		aliases.put("경기", new String[] { "경기도" });
		aliases.put("경남", new String[] { "경상남도", "경남" });
		aliases.put("경북", new String[] { "경상북도", "경북", "안동시" });
		aliases.put("광주", new String[] { "광주광역시" });
		aliases.put("대구", new String[] { "대구광역시" });
		aliases.put("대전", new String[] { "대전광역시" });
		aliases.put("부산", new String[] { "부산광역시" });
		aliases.put("서울", new String[] { "서울특별시" });
		aliases.put("세종", new String[] { "세종특별자치시", "세종시" });
		aliases.put("울산", new String[] { "울산광역시" });
		aliases.put("인천", new String[] { "인천광역시" });
		aliases.put("전남", new String[] { "전라남도", "전남" });
		aliases.put("전북", new String[] { "전북특별자치도", "전라북도" });
		aliases.put("제주", new String[] { "제주특별자치도", "제주도" });
		aliases.put("충남", new String[] { "충청남도" });
		aliases.put("충북", new String[] { "충청북도" });
		return aliases;
	}

	/**
	 * 레코드 내부 줄바꿈, 인용부호 안 쉼표, 이중 인용부호를 처리한다.
	 */
	private static final class CsvRecordReader implements AutoCloseable {

		private static final int NO_PUSHBACK = Integer.MIN_VALUE;
		private final Reader reader;
		private final char[] buffer = new char[64 * 1024];
		private int position;
		private int limit;
		private int pushedBack = NO_PUSHBACK;

		private CsvRecordReader(Reader reader) {
			this.reader = reader;
		}

		private String[] readRecord() throws IOException {
			List<Integer> indexes = new ArrayList<Integer>();
			for (int i = 0; i < 128; i++) {
				indexes.add(Integer.valueOf(i));
			}
			int[] all = new int[indexes.size()];
			for (int i = 0; i < all.length; i++) {
				all[i] = indexes.get(i).intValue();
			}
			String[] values = readRecord(all);
			if (values == null) {
				return null;
			}
			int length = values.length;
			while (length > 0 && values[length - 1] == null) {
				length--;
			}
			return Arrays.copyOf(values, length);
		}

		private String[] readRecord(int[] selectedIndexes) throws IOException {
			String[] result = new String[selectedIndexes.length];
			int fieldIndex = 0;
			int selectedPosition = selectedPosition(selectedIndexes, fieldIndex);
			StringBuilder field = selectedPosition >= 0 ? new StringBuilder(32) : null;
			boolean inQuotes = false;
			boolean atFieldStart = true;
			boolean sawCharacter = false;

			while (true) {
				int current = readChar();
				if (current == -1) {
					if (!sawCharacter && fieldIndex == 0 && atFieldStart) {
						return null;
					}
					store(result, selectedPosition, field);
					return result;
				}
				sawCharacter = true;
				char character = (char) current;

				if (inQuotes) {
					if (character == '"') {
						int next = readChar();
						if (next == '"') {
							if (field != null) {
								field.append('"');
							}
						} else {
							inQuotes = false;
							pushBack(next);
						}
					} else if (field != null) {
						field.append(character);
					}
					continue;
				}

				if (atFieldStart && character == '"') {
					inQuotes = true;
					atFieldStart = false;
				} else if (character == ',') {
					store(result, selectedPosition, field);
					fieldIndex++;
					selectedPosition = selectedPosition(selectedIndexes, fieldIndex);
					field = selectedPosition >= 0 ? new StringBuilder(32) : null;
					atFieldStart = true;
				} else if (character == '\r' || character == '\n') {
					if (character == '\r') {
						int next = readChar();
						if (next != '\n') {
							pushBack(next);
						}
					}
					store(result, selectedPosition, field);
					return result;
				} else {
					if (field != null) {
						field.append(character);
					}
					atFieldStart = false;
				}
			}
		}

		private int readChar() throws IOException {
			if (pushedBack != NO_PUSHBACK) {
				int value = pushedBack;
				pushedBack = NO_PUSHBACK;
				return value;
			}
			if (position >= limit) {
				limit = reader.read(buffer);
				position = 0;
				if (limit < 0) {
					return -1;
				}
			}
			return buffer[position++];
		}

		private void pushBack(int character) {
			if (character != -1) {
				pushedBack = character;
			}
		}

		private static int selectedPosition(int[] selectedIndexes, int fieldIndex) {
			int position = Arrays.binarySearch(selectedIndexes, fieldIndex);
			return position >= 0 ? position : -1;
		}

		private static void store(String[] result, int selectedPosition, StringBuilder field) {
			if (selectedPosition >= 0) {
				result[selectedPosition] = field == null ? "" : field.toString();
			}
		}

		@Override
		public void close() throws IOException {
			reader.close();
		}
	}
}
