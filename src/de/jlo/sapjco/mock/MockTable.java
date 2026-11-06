package de.jlo.sapjco.mock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A table of the mock system, read from a CSV file (separator ; quote ").
 * The header line contains the fields as NAME[:LENGTH[:TYPE[:TEXT]]].
 * Without length the field gets the length of its longest value, the default type is C (character).
 * Empty lines and lines starting with # are ignored.
 */
public class MockTable {

	public static class Field {
		final String name;
		final int length;
		final String type;
		final String text;

		Field(String name, int length, String type, String text) {
			this.name = name;
			this.length = length;
			this.type = type;
			this.text = text;
		}

		public String getName() {
			return name;
		}

		public int getLength() {
			return length;
		}

		public String getType() {
			return type;
		}

		public String getText() {
			return text;
		}

		boolean isNumeric() {
			return "I".equals(type) || "P".equals(type) || "F".equals(type) || "b".equals(type) || "s".equals(type) || "8".equals(type);
		}
	}

	private final String name;
	private final List<Field> fields = new ArrayList<>();
	private final Map<String, Field> fieldMap = new LinkedHashMap<>();
	private final List<Map<String, String>> rows = new ArrayList<>();

	private MockTable(String name) {
		this.name = name;
	}

	public static MockTable load(String name, File file) throws IOException {
		MockTable table = new MockTable(name);
		List<String[]> header = new ArrayList<>();
		List<List<String>> values = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
			String line;
			int lineNumber = 0;
			while ((line = r.readLine()) != null) {
				lineNumber++;
				if (lineNumber == 1 && line.startsWith("﻿")) {
					line = line.substring(1); // BOM
				}
				if (line.trim().isEmpty() || line.startsWith("#")) {
					continue;
				}
				List<String> parts = splitCsvLine(line, file, lineNumber);
				if (header.isEmpty()) {
					for (String p : parts) {
						header.add(p.trim().split(":", 4));
					}
				} else {
					if (parts.size() != header.size()) {
						throw new IOException(file.getName() + " line " + lineNumber + ": expected " + header.size() + " values but got " + parts.size());
					}
					values.add(parts);
				}
			}
		}
		if (header.isEmpty()) {
			throw new IOException(file.getName() + ": header line with the field names is missing");
		}
		for (int i = 0; i < header.size(); i++) {
			String[] h = header.get(i);
			String fieldName = h[0].trim().toUpperCase();
			int maxLength = 1;
			for (List<String> row : values) {
				maxLength = Math.max(maxLength, row.get(i).length());
			}
			int length = maxLength;
			if (h.length > 1 && h[1].trim().isEmpty() == false) {
				length = Integer.parseInt(h[1].trim());
				if (length < maxLength) {
					throw new IOException(file.getName() + ": field " + fieldName + " has the length " + length + " but contains a value with " + maxLength + " characters");
				}
			}
			String type = h.length > 2 && h[2].trim().isEmpty() == false ? h[2].trim() : "C";
			String text = h.length > 3 ? h[3].trim() : fieldName;
			Field f = new Field(fieldName, length, type, text);
			table.fields.add(f);
			table.fieldMap.put(fieldName, f);
		}
		for (List<String> v : values) {
			Map<String, String> row = new LinkedHashMap<>();
			for (int i = 0; i < table.fields.size(); i++) {
				row.put(table.fields.get(i).name, v.get(i));
			}
			table.rows.add(row);
		}
		return table;
	}

	static List<String> splitCsvLine(String line, File file, int lineNumber) throws IOException {
		List<String> parts = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (quoted) {
				if (c == '"') {
					if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
						sb.append('"');
						i++;
					} else {
						quoted = false;
					}
				} else {
					sb.append(c);
				}
			} else if (c == '"' && sb.length() == 0) {
				// only a value starting with a quote is quoted, otherwise the quote is part of the value
				quoted = true;
			} else if (c == ';') {
				parts.add(sb.toString());
				sb.setLength(0);
			} else {
				sb.append(c);
			}
		}
		if (quoted) {
			throw new IOException(file.getName() + " line " + lineNumber + ": unterminated quote");
		}
		parts.add(sb.toString());
		return parts;
	}

	public String getName() {
		return name;
	}

	public List<Field> getFields() {
		return fields;
	}

	public Field getField(String fieldName) {
		return fieldMap.get(fieldName.toUpperCase());
	}

	public List<Map<String, String>> getRows() {
		return rows;
	}

}
