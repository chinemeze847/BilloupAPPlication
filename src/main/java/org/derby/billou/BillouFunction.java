package org.derby.billou;

import java.util.List;

class BillouFunction implements BillouCallable {
	private final Statement.Function declaration;
	private final Env closure;

	private final boolean isInitializer;

	BillouFunction(Statement.Function declaration, Env closure,
			boolean isInitializer) {
		this.isInitializer = isInitializer;

		this.closure = closure;

		this.declaration = declaration;
	}

	BillouFunction bind(BillouInstance instance) {
		Env env = new Env(closure);
		env.define("this", instance);

		return new BillouFunction(declaration, env,
				isInitializer);
	}

	@Override
	public String toString() {
		return "<fn " + declaration.name.lexeme + ">";
	}

	@Override
	public int arity() {
		return declaration.params.size();
	}

	@Override
	public Object call(Interpreter interpreter,
			List<Object> arguments) {

		Env env = new Env(closure);

		for (int i = 0; i < declaration.params.size(); i++) {
			env.define(declaration.params.get(i).lexeme,
					arguments.get(i));
		}

		try {
			interpreter.executeBlock(declaration.body, env);
		} catch (ReturnStatement returnStatementValue) {

			if (isInitializer) return closure.getAt(0, "this");

			return returnStatementValue.value;
		}

		if (isInitializer) return closure.getAt(0, "this");

		return null;
	}

}
