package com.ssafy.trip.util;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import com.ssafy.trip.model.dto.FestivalDto;

/**
 * 별도 라이브러리 없이 Office Open XML(.xlsx)에서 조사표 시트의 축제 정보를 읽는다.
 */
public class FestivalExcelParser {

	private static final String FILE_NAME = "2026년지역축제개최계획현황.xlsx";
	private static final String SHEET_NAME = "조사표";
	private static final String RELATIONSHIP_NS =
			"http://schemas.openxmlformats.org/officeDocument/2006/relationships";

	public List<FestivalDto> parse() {
		Path filePath = findFestivalFile();
		try (ZipFile zipFile = new ZipFile(filePath.toFile())) {
			String worksheetPath = findWorksheetPath(zipFile, SHEET_NAME);
			List<String> sharedStrings = readSharedStrings(zipFile);
			return readFestivals(zipFile, worksheetPath, sharedStrings);
		} catch (Exception e) {
			throw new IllegalStateException("축제 Excel 데이터를 읽는 중 오류가 발생했습니다: " + filePath, e);
		}
	}

	private Path findFestivalFile() {
		Path path = Paths.get("res", FILE_NAME);
		if (Files.exists(path)) {
			return path;
		}

		path = Paths.get("EnjoyTrip", "res", FILE_NAME);
		if (Files.exists(path)) {
			return path;
		}
		throw new IllegalStateException("축제 Excel 파일을 찾을 수 없습니다: " + FILE_NAME);
	}

	private String findWorksheetPath(ZipFile zipFile, String sheetName) throws Exception {
		Document workbook = parseXml(zipFile, "xl/workbook.xml");
		NodeList sheets = workbook.getElementsByTagNameNS("*", "sheet");
		String relationshipId = null;
		for (int i = 0; i < sheets.getLength(); i++) {
			Element sheet = (Element) sheets.item(i);
			if (sheetName.equals(sheet.getAttribute("name"))) {
				relationshipId = sheet.getAttributeNS(RELATIONSHIP_NS, "id");
				break;
			}
		}
		if (relationshipId == null || relationshipId.isEmpty()) {
			throw new IllegalStateException("'" + sheetName + "' 시트를 찾을 수 없습니다.");
		}

		Document relationships = parseXml(zipFile, "xl/_rels/workbook.xml.rels");
		NodeList relationNodes = relationships.getElementsByTagNameNS("*", "Relationship");
		for (int i = 0; i < relationNodes.getLength(); i++) {
			Element relation = (Element) relationNodes.item(i);
			if (relationshipId.equals(relation.getAttribute("Id"))) {
				String target = relation.getAttribute("Target").replace('\\', '/');
				if (target.startsWith("/")) {
					return target.substring(1);
				}
				return target.startsWith("xl/") ? target : "xl/" + target;
			}
		}
		throw new IllegalStateException("'" + sheetName + "' 시트 파일의 연결 정보를 찾을 수 없습니다.");
	}

	private Document parseXml(ZipFile zipFile, String entryName) throws Exception {
		ZipEntry entry = requiredEntry(zipFile, entryName);
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		try (InputStream input = zipFile.getInputStream(entry)) {
			return factory.newDocumentBuilder().parse(input);
		}
	}

	private List<String> readSharedStrings(ZipFile zipFile) throws Exception {
		ZipEntry entry = zipFile.getEntry("xl/sharedStrings.xml");
		if (entry == null) {
			return new ArrayList<String>();
		}

		SharedStringsHandler handler = new SharedStringsHandler();
		try (InputStream input = zipFile.getInputStream(entry)) {
			createSaxParser().parse(input, handler);
		}
		return handler.getValues();
	}

	private List<FestivalDto> readFestivals(ZipFile zipFile, String worksheetPath,
			List<String> sharedStrings) throws Exception {
		ZipEntry entry = requiredEntry(zipFile, worksheetPath);
		FestivalSheetHandler handler = new FestivalSheetHandler(sharedStrings);
		try (InputStream input = zipFile.getInputStream(entry)) {
			createSaxParser().parse(input, handler);
		}
		return handler.getFestivals();
	}

	private SAXParser createSaxParser() throws Exception {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
		setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
		setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
		return factory.newSAXParser();
	}

	private void setFeatureIfSupported(SAXParserFactory factory, String feature, boolean value)
			throws Exception {
		factory.setFeature(feature, value);
	}

	private ZipEntry requiredEntry(ZipFile zipFile, String entryName) throws IOException {
		ZipEntry entry = zipFile.getEntry(entryName);
		if (entry == null) {
			throw new IOException("Excel 내부 파일을 찾을 수 없습니다: " + entryName);
		}
		return entry;
	}

	private static class SharedStringsHandler extends DefaultHandler {

		private final List<String> values = new ArrayList<String>();
		private final StringBuilder currentValue = new StringBuilder();
		private boolean inSharedItem;
		private boolean inText;

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) {
			if ("si".equals(localName)) {
				inSharedItem = true;
				currentValue.setLength(0);
			} else if (inSharedItem && "t".equals(localName)) {
				inText = true;
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) {
			if (inText) {
				currentValue.append(ch, start, length);
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) {
			if ("t".equals(localName)) {
				inText = false;
			} else if ("si".equals(localName)) {
				values.add(currentValue.toString());
				inSharedItem = false;
			}
		}

		public List<String> getValues() {
			return values;
		}
	}

	private static class FestivalSheetHandler extends DefaultHandler {

		private final List<String> sharedStrings;
		private final List<FestivalDto> festivals = new ArrayList<FestivalDto>();
		private final Map<Integer, String> rowValues = new HashMap<Integer, String>();
		private final StringBuilder cellText = new StringBuilder();

		private int rowNumber;
		private int columnIndex;
		private String cellType;
		private boolean readingCellText;

		FestivalSheetHandler(List<String> sharedStrings) {
			this.sharedStrings = sharedStrings;
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) {
			if ("row".equals(localName)) {
				rowValues.clear();
				rowNumber = parseInteger(attributes.getValue("r"));
			} else if ("c".equals(localName)) {
				columnIndex = columnIndex(attributes.getValue("r"));
				cellType = attributes.getValue("t");
				cellText.setLength(0);
			} else if ("v".equals(localName) || ("t".equals(localName) && "inlineStr".equals(cellType))) {
				readingCellText = true;
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) {
			if (readingCellText) {
				cellText.append(ch, start, length);
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) {
			if ("v".equals(localName) || ("t".equals(localName) && "inlineStr".equals(cellType))) {
				readingCellText = false;
			} else if ("c".equals(localName)) {
				rowValues.put(columnIndex, decodeValue(cellText.toString(), cellType));
			} else if ("row".equals(localName) && rowNumber >= 9) {
				FestivalDto festival = createFestival(rowValues);
				if (festival != null) {
					festivals.add(festival);
				}
			}
		}

		private String decodeValue(String raw, String type) {
			if (raw == null || raw.isEmpty()) {
				return "";
			}
			if ("s".equals(type)) {
				int index = parseInteger(raw);
				return index >= 0 && index < sharedStrings.size() ? sharedStrings.get(index) : "";
			}
			return raw.trim();
		}

		private FestivalDto createFestival(Map<Integer, String> values) {
			String festivalName = value(values, 4);
			if (festivalName.isEmpty()) {
				return null;
			}

			FestivalDto festival = new FestivalDto();
			festival.setNum(parseInteger(value(values, 1)));
			festival.setMetropolitanName(value(values, 2));
			festival.setBasicMunicipalityName(value(values, 3));
			festival.setFestivalName(festivalName);
			festival.setFestivalType(value(values, 5));
			festival.setPlaceName(value(values, 6));
			festival.setPlaceType(value(values, 7));
			festival.setSido(value(values, 8));
			festival.setSigungu(value(values, 9));
			festival.setEupmyeondong(value(values, 10));
			festival.setStartDate(createDate(values, 11, 12, 13));
			festival.setEndDate(createDate(values, 14, 15, 16));
			festival.setTotalDays(parseInteger(value(values, 17)));
			festival.setScheduleNote(value(values, 18));
			festival.setCycle(value(values, 19));
			festival.setFirstYear(toPlainNumber(value(values, 20)));
			festival.setTotalBudget(toPlainNumber(value(values, 21)));
			festival.setPreviousVisitors(toPlainNumber(value(values, 26)));
			festival.setDedicatedOrganization(value(values, 31));
			festival.setAffiliation(value(values, 34));
			festival.setDepartment(value(values, 35));
			festival.setContact(value(values, 38));
			festival.setNote(value(values, 39));
			return festival;
		}

		private LocalDate createDate(Map<Integer, String> values, int yearColumn, int monthColumn,
				int dayColumn) {
			int year = parseInteger(value(values, yearColumn));
			int month = parseInteger(value(values, monthColumn));
			int day = parseInteger(value(values, dayColumn));
			if (year == 0 || month == 0 || day == 0) {
				return null;
			}
			try {
				return LocalDate.of(year, month, day);
			} catch (DateTimeException e) {
				return null;
			}
		}

		private String value(Map<Integer, String> values, int index) {
			String value = values.get(index);
			return value == null ? "" : value.trim();
		}

		public List<FestivalDto> getFestivals() {
			return festivals;
		}
	}

	private static int columnIndex(String cellReference) {
		if (cellReference == null || cellReference.isEmpty()) {
			return -1;
		}
		int result = 0;
		int i = 0;
		while (i < cellReference.length() && Character.isLetter(cellReference.charAt(i))) {
			result = result * 26 + (Character.toUpperCase(cellReference.charAt(i)) - 'A' + 1);
			i++;
		}
		return result - 1;
	}

	private static int parseInteger(String value) {
		if (value == null || value.trim().isEmpty()) {
			return 0;
		}
		try {
			return new BigDecimal(value.trim()).intValue();
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String toPlainNumber(String value) {
		if (value == null || value.trim().isEmpty()) {
			return "";
		}
		try {
			return new BigDecimal(value.trim()).stripTrailingZeros().toPlainString();
		} catch (NumberFormatException e) {
			return value.trim();
		}
	}
}
