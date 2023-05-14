package org.derby.billou;

/**
 * This class defines errors that occur at
 * run time
 */
class RuntimeError extends RuntimeException {
	final Token token;

	RuntimeError(Token token, String message) {
		super(message);
		this.token = token;
	}
}
