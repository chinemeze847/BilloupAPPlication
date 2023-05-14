package org.derby.billou;

import static org.derby.billou.TokenType.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *It carries out lexical analysis phase
 */
class Scanner {

	//This stores The keywords in th source if any
	private static final Map<String, TokenType> keywords;

	static {
		keywords = new HashMap<>();
		keywords.put("and",    AND);
		keywords.put("class",  CLASS);
		keywords.put("else",   ELSE);
		keywords.put("false",  FALSE);
		keywords.put("for",    FOR);
		keywords.put("fun",    FUN);
		keywords.put("if",     IF);
		keywords.put("nil",    NIL);
		keywords.put("or",     OR);
		keywords.put("print",  PRINT);
		keywords.put("return", RETURN);
		keywords.put("super",  SUPER);
		keywords.put("this",   THIS);
		keywords.put("true",   TRUE);
		keywords.put("var",    VAR);
		keywords.put("while",  WHILE);
	}
	//< keyword-map
	private final String source;

	//This will store the tokens
	private final List<Token> tokens = new ArrayList<>();

	//The start of the line
	private int start = 0;

	//The current token
	private int current = 0;
	private int line = 1;
	//< scan-state

	Scanner(String source) {
		this.source = source;
	}
	//> scan-tokens
	List<Token> scanTokens() {
		while (!isAtEnd()) {
			// We are at the beginning of the next lexeme.
			start = current;
			scanToken();
		}

		tokens.add(new Token(EOF, "", null, line));
		return tokens;
	}
	//< scan-tokens
	//> scan-token
	private void scanToken() {
		//passes the current lexeme and moves the current to next lexeme
		char c = move();
		switch (c) {
		case '(': addToken(LEFT_PAREN); break;
		case ')': addToken(RIGHT_PAREN); break;
		case '{': addToken(LEFT_BRACE); break;
		case '}': addToken(RIGHT_BRACE); break;
		case ',': addToken(COMMA); break;
		case '.': addToken(DOT); break;
		case '-': addToken(MINUS); break;
		case '+': addToken(PLUS); break;
		case ';': addToken(SEMICOLON); break;
		case '*': addToken(STAR); break; // [slash]

		// two-char-tokens
		case '!':
			addToken(match('=') ? BANG_EQUAL : BANG);
			break;
		case '=':
			addToken(match('=') ? EQUAL_EQUAL : EQUAL);
			break;
		case '<':
			addToken(match('=') ? LESS_EQUAL : LESS);
			break;
		case '>':
			addToken(match('=') ? GREATER_EQUAL : GREATER);
			break;
		case '/':
			if (match('/')) {
				// A comment goes until the end of the line.
				//checks if it's a comment
				while (peek() != '\n' && !isAtEnd()) move();
			} else {
				addToken(SLASH);
			}
			break;

		case ' ':
		case '\r':
		case '\t':
			// Ignore whitespace.
			break;

		case '\n':
			line++;
			break;
		//< whitespace
		//> string-start

		case '"': string(); break;
		//< string-start
		//> char-error

		default:

			if (isDigit(c)) {
				numeral();
			} else if (isAlpha(c)) {
				identifier();
			} else {
				Billou.error(line, "Unexpected character.");
			}
			break;
		}
	}

	/**
	 * checks if it's an identifier
	 */
	private void identifier() {
		while (isAlphaNumeric(peek())) move();

		String text = source.substring(start, current);
		TokenType type = keywords.get(text);
		if (type == null) type = IDENTIFIER;
		addToken(type);

	}

	/**
	 * checks if it's a number
	 */
	private void numeral() {

		//picks all the digit in that token
		while (isDigit(peek()))
			move();

		// Look for a fractional part.
		//For handling boolean values
		if (peek() == '.' && isDigit(peekNext())) {
			// Consume the "."
			move();

			while (isDigit(peek()))
				move();
		}

		addToken(NUMBER,
				Double.parseDouble(source.substring(start, current)));
	}

	private void string() {
		while (peek() != '"' && !isAtEnd()) {
			if (peek() == '\n') line++;
			move();
		}

		if (isAtEnd()) {
			Billou.error(line, "Unterminated string.");
			return;
		}

		// The closing ".
		move();

		// Trim the surrounding quotes.
		String val = source.substring(start + 1, current - 1);
		addToken(STRING, val);
	}

	/**
	 *
	 * @param expected
	 * @return boolean
	 */
	private boolean match(char expected) {
		//returns false if we have reached the end of the line
		if (isAtEnd()) return false;

		//returns false if expected character doesn't match current character
		if (source.charAt(current) != expected) return false;

		//moves current by one
		current++;

		//returns true if they match
		return true;
	}

	/**
	 * This method picks the current character
	 * @return the charater at the current position of scanning
	 */
	private char peek() {
		if (isAtEnd()) return '\0';
		return source.charAt(current);
	}

	/**
	 *
	 * @return the next character in the sequence
	 */
	private char peekNext() {
		if (current + 1 >= source.length()) return '\0';
		return source.charAt(current + 1);
	}

	/**
	 * checks if c is alphanumeric
	 * @param c
	 * @return
	 */
	private boolean isAlpha(char c) {
		return (c >= 'a' && c <= 'z') ||
				(c >= 'A' && c <= 'Z') ||
				c == '_';
	}

	/**
	 * returns if its alphanumeric
	 * @param c
	 * @return
	 */
	private boolean isAlphaNumeric(char c) {
		return isAlpha(c) || isDigit(c);
	}

	/**
	 * checks if its a digit
	 * @param c
	 * @return
	 */
	private boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	/**
	 * Checks if we are at the end of the line
	 * @return
	 */
	private boolean isAtEnd() {
		return current >= source.length();
	}

	/**
	 * moves to next token
	 * @return current token
	 */
	private char move() {
		return source.charAt(current++);
	}

	/**
	 * it adds a token
	 * @param type
	 */
	private void addToken(TokenType type) {
		addToken(type, null);
	}

	/**
	 * This does the actual adding of a token
	 * @param type
	 * @param literal
	 */
	private void addToken(TokenType type, Object literal) {
		String text = source.substring(start, current);
		tokens.add(new Token(type, text, literal, line));
	}
}