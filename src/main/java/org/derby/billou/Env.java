package org.derby.billou;

import java.util.*;

/**
 * This class acts like the memory of the
 * compiler
 */
class Env {

	final Env enclosing;
	private final Map<String, Object> values = new HashMap<>();
	Env() {
		enclosing = null;
	}

	Env(Env enclosing) {
		this.enclosing = enclosing;
	}

	Object get(Token name) {
		if (values.containsKey(name.lexeme)) {
			return values.get(name.lexeme);
		}

		if (enclosing != null) return enclosing.get(name);

		throw new RuntimeError(name,
				"Undefined variable '" + name.lexeme + "'.");
	}

	void assign(Token name, Object value) {
		if (values.containsKey(name.lexeme)) {
			values.put(name.lexeme, value);
			return;
		}

		if (enclosing != null) {
			enclosing.assign(name, value);
			return;
		}
		throw new RuntimeError(name,
				"Undefined variable '" + name.lexeme + "'.");
	}
	void define(String name, Object value) {
		values.put(name, value);
	}
	Env ancestor(int distance) {
		Env env = this;
		for (int i = 0; i < distance; i++) {
			env = env.enclosing; // [coupled]
		}

		return env;
	}

	Object getAt(int distance, String name) {
		return ancestor(distance).values.get(name);
	}

	void assignAt(int distance, Token name, Object value) {
		ancestor(distance).values.put(name.lexeme, value);
	}

	@Override
	public String toString() {
		String result = values.toString();
		if (enclosing != null) {
			result += " -> " + enclosing.toString();
		}

		return result;
	}
}