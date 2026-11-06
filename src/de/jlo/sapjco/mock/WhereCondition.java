package de.jlo.sapjco.mock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parser and evaluator for the subset of the ABAP Open SQL WHERE syntax used in the OPTIONS of RFC_READ_TABLE:
 * comparisons (= EQ, &lt;&gt; NE, &lt; LT, &gt; GT, &lt;= LE, &gt;= GE), [NOT] LIKE, [NOT] IN (...), [NOT] BETWEEN ... AND ...,
 * IS [NOT] NULL, AND, OR, NOT and parentheses.
 * Values of SAP database fields are never NULL (they are initial), so IS NULL is always false.
 */
public class WhereCondition {

	public static class ParseException extends Exception {
		private static final long serialVersionUID = 1L;
		ParseException(String message) {
			super(message);
		}
	}

	interface Node {
		boolean eval(Map<String, String> row);
	}

	private static class Token {
		final String type; // IDENT, STRING, NUMBER, OP, LPAREN, RPAREN, COMMA
		final String text;
		Token(String type, String text) {
			this.type = type;
			this.text = text;
		}
		boolean isKeyword(String keyword) {
			return "IDENT".equals(type) && text.equalsIgnoreCase(keyword);
		}
		@Override
		public String toString() {
			return text;
		}
	}

	private final MockTable table;
	private final List<Token> tokens;
	private int pos = 0;

	private WhereCondition(MockTable table, List<Token> tokens) {
		this.table = table;
		this.tokens = tokens;
	}

	/**
	 * Parses the condition
	 * @param condition the WHERE condition without the keyword WHERE, null or empty means no restriction
	 * @param table used to validate the fields
	 * @return node to evaluate per row
	 */
	public static Node parse(String condition, MockTable table) throws ParseException {
		if (condition == null || condition.trim().isEmpty()) {
			return row -> true;
		}
		WhereCondition p = new WhereCondition(table, tokenize(condition));
		Node node = p.parseOr();
		if (p.pos < p.tokens.size()) {
			throw new ParseException("Unexpected token '" + p.tokens.get(p.pos) + "'");
		}
		return node;
	}

	private static List<Token> tokenize(String s) throws ParseException {
		List<Token> list = new ArrayList<>();
		int i = 0;
		while (i < s.length()) {
			char c = s.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
			} else if (c == '\'' || c == '`') {
				char quote = c;
				StringBuilder sb = new StringBuilder();
				i++;
				boolean closed = false;
				while (i < s.length()) {
					char x = s.charAt(i);
					if (x == quote) {
						if (i + 1 < s.length() && s.charAt(i + 1) == quote) {
							sb.append(quote);
							i += 2;
							continue;
						}
						closed = true;
						i++;
						break;
					}
					sb.append(x);
					i++;
				}
				if (closed == false) {
					throw new ParseException("Unterminated literal: " + quote + sb);
				}
				list.add(new Token("STRING", sb.toString()));
			} else if (c == '(') {
				list.add(new Token("LPAREN", "("));
				i++;
			} else if (c == ')') {
				list.add(new Token("RPAREN", ")"));
				i++;
			} else if (c == ',') {
				list.add(new Token("COMMA", ","));
				i++;
			} else if (c == '=' || c == '<' || c == '>') {
				String two = i + 1 < s.length() ? s.substring(i, i + 2) : "";
				if (two.equals("<=") || two.equals(">=") || two.equals("<>") || two.equals("><") || two.equals("=<") || two.equals("=>")) {
					list.add(new Token("OP", two));
					i += 2;
				} else {
					list.add(new Token("OP", String.valueOf(c)));
					i++;
				}
			} else if (Character.isDigit(c) || (c == '-' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1)))) {
				int start = i;
				i++;
				while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
					i++;
				}
				list.add(new Token("NUMBER", s.substring(start, i)));
			} else if (Character.isLetter(c) || c == '_' || c == '/') {
				int start = i;
				while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || "_/~".indexOf(s.charAt(i)) >= 0)) {
					i++;
				}
				list.add(new Token("IDENT", s.substring(start, i)));
			} else {
				throw new ParseException("Invalid character '" + c + "' at position " + i);
			}
		}
		return list;
	}

	private Token peek() {
		return pos < tokens.size() ? tokens.get(pos) : null;
	}

	private Token next() throws ParseException {
		if (pos >= tokens.size()) {
			throw new ParseException("Unexpected end of condition");
		}
		return tokens.get(pos++);
	}

	private boolean acceptKeyword(String keyword) {
		Token t = peek();
		if (t != null && t.isKeyword(keyword)) {
			pos++;
			return true;
		}
		return false;
	}

	private void expect(String type) throws ParseException {
		Token t = next();
		if (t.type.equals(type) == false) {
			throw new ParseException("Expected " + type + " but got '" + t + "'");
		}
	}

	private Node parseOr() throws ParseException {
		Node left = parseAnd();
		while (acceptKeyword("OR")) {
			Node l = left;
			Node r = parseAnd();
			left = row -> l.eval(row) || r.eval(row);
		}
		return left;
	}

	private Node parseAnd() throws ParseException {
		Node left = parseNot();
		while (acceptKeyword("AND")) {
			Node l = left;
			Node r = parseNot();
			left = row -> l.eval(row) && r.eval(row);
		}
		return left;
	}

	private Node parseNot() throws ParseException {
		if (acceptKeyword("NOT")) {
			Node n = parseNot();
			return row -> n.eval(row) == false;
		}
		Token t = peek();
		if (t != null && t.type.equals("LPAREN")) {
			pos++;
			Node n = parseOr();
			expect("RPAREN");
			return n;
		}
		return parsePredicate();
	}

	private MockTable.Field parseField() throws ParseException {
		Token t = next();
		if (t.type.equals("IDENT") == false) {
			throw new ParseException("Expected a field name but got '" + t + "'");
		}
		String name = t.text;
		int tilde = name.indexOf('~');
		if (tilde >= 0) {
			name = name.substring(tilde + 1);
		}
		MockTable.Field f = table.getField(name);
		if (f == null) {
			throw new ParseException("Field " + name.toUpperCase() + " does not exist in table " + table.getName());
		}
		return f;
	}

	private String parseLiteral() throws ParseException {
		Token t = next();
		if (t.type.equals("STRING") || t.type.equals("NUMBER")) {
			return t.text;
		}
		throw new ParseException("Expected a literal but got '" + t + "'");
	}

	private Node parsePredicate() throws ParseException {
		MockTable.Field field = parseField();
		String fieldName = field.name;
		boolean numeric = field.isNumeric();
		if (acceptKeyword("IS")) {
			boolean not = acceptKeyword("NOT");
			if (acceptKeyword("NULL") == false) {
				throw new ParseException("Expected NULL after IS");
			}
			return row -> not;
		}
		boolean not = acceptKeyword("NOT");
		if (acceptKeyword("LIKE")) {
			Pattern pattern = likeToRegex(parseLiteral());
			if (acceptKeyword("ESCAPE")) {
				parseLiteral(); // escape characters are not supported, ignore
			}
			return row -> pattern.matcher(rtrim(row.get(fieldName))).matches() != not;
		}
		if (acceptKeyword("IN")) {
			expect("LPAREN");
			List<String> values = new ArrayList<>();
			values.add(parseLiteral());
			while (peek() != null && peek().type.equals("COMMA")) {
				pos++;
				values.add(parseLiteral());
			}
			expect("RPAREN");
			return row -> {
				String v = row.get(fieldName);
				for (String x : values) {
					if (compare(v, x, numeric) == 0) {
						return not == false;
					}
				}
				return not;
			};
		}
		if (acceptKeyword("BETWEEN")) {
			String low = parseLiteral();
			if (acceptKeyword("AND") == false) {
				throw new ParseException("Expected AND in BETWEEN");
			}
			String high = parseLiteral();
			return row -> {
				String v = row.get(fieldName);
				boolean in = compare(v, low, numeric) >= 0 && compare(v, high, numeric) <= 0;
				return in != not;
			};
		}
		if (not) {
			throw new ParseException("Expected LIKE, IN or BETWEEN after NOT");
		}
		String op = parseOperator();
		Token t = peek();
		if (t != null && t.type.equals("IDENT")) {
			// comparison with another field
			String otherName = parseField().name;
			return row -> check(op, compare(row.get(fieldName), row.get(otherName), numeric));
		}
		String literal = parseLiteral();
		return row -> check(op, compare(row.get(fieldName), literal, numeric));
	}

	private String parseOperator() throws ParseException {
		Token t = next();
		String op = t.text.toUpperCase();
		if (t.type.equals("OP") || t.type.equals("IDENT")) {
			switch (op) {
			case "=": case "EQ": return "=";
			case "<>": case "><": case "NE": return "<>";
			case "<": case "LT": return "<";
			case ">": case "GT": return ">";
			case "<=": case "=<": case "LE": return "<=";
			case ">=": case "=>": case "GE": return ">=";
			default:
			}
		}
		throw new ParseException("Expected a comparison operator but got '" + t + "'");
	}

	private static boolean check(String op, int cmp) {
		switch (op) {
		case "=": return cmp == 0;
		case "<>": return cmp != 0;
		case "<": return cmp < 0;
		case ">": return cmp > 0;
		case "<=": return cmp <= 0;
		default: return cmp >= 0;
		}
	}

	/**
	 * Compares like ABAP: character fields without trailing spaces, numeric fields by value
	 */
	static int compare(String value, String other, boolean numeric) {
		String v = rtrim(value);
		String o = rtrim(other);
		if (numeric) {
			try {
				BigDecimal a = v.trim().isEmpty() ? BigDecimal.ZERO : new BigDecimal(v.trim());
				BigDecimal b = o.trim().isEmpty() ? BigDecimal.ZERO : new BigDecimal(o.trim());
				return a.compareTo(b);
			} catch (NumberFormatException e) {
				// fall back to the character comparison
			}
		}
		return v.compareTo(o);
	}

	static String rtrim(String s) {
		if (s == null) {
			return "";
		}
		int end = s.length();
		while (end > 0 && s.charAt(end - 1) == ' ') {
			end--;
		}
		return s.substring(0, end);
	}

	static Pattern likeToRegex(String like) {
		StringBuilder sb = new StringBuilder();
		for (char c : rtrim(like).toCharArray()) {
			if (c == '%') {
				sb.append(".*");
			} else if (c == '_') {
				sb.append('.');
			} else {
				sb.append(Pattern.quote(String.valueOf(c)));
			}
		}
		return Pattern.compile(sb.toString(), Pattern.DOTALL);
	}

}
