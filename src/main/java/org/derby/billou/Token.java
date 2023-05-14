package org.derby.billou;

/**
 * This contains sequence of characters
 */
public class Token {
	final TokenType type; // the type of token
	final String lexeme; // the actual char in the line
	final Object literal;  //number or string
	final int line; // the line in which the token is found

	/**
	 * Constructs a token object
	 * @param type
	 * @param lexeme
	 * @param literal
	 * @param line
	 */
	Token(TokenType type, String lexeme, Object literal, int line) {
		this.type = type;
		this.lexeme = lexeme;
		this.literal = literal;
		this.line = line;
	}

	public String toString() {
		return type + " " + lexeme + " " + literal;
	}
}
