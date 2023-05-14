package org.derby.billou;


import java.util.List;
import java.util.Map;

class BillouClass implements BillouCallable {

	final String name;
	final BillouClass superclass;

	private final Map<String, BillouFunction> methods;

	BillouClass(String name, BillouClass superclass,
			Map<String, BillouFunction> methods) {
		this.superclass = superclass;

		this.name = name;
		this.methods = methods;
	}

	BillouFunction findMethod(String name) {
		if (methods.containsKey(name)) {
			return methods.get(name);
		}
		if (superclass != null) {
			return superclass.findMethod(name);
		}

		return null;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public Object call(Interpreter interpreter,
			List<Object> arguments) {
		BillouInstance instance = new BillouInstance(this);
		BillouFunction initializer = findMethod("init");
		if (initializer != null) {
			initializer.bind(instance).call(interpreter, arguments);
		}

		return instance;
	}

	@Override
	public int arity() {
		BillouFunction initializer = findMethod("init");
		if (initializer == null) return 0;
		return initializer.arity();

	}
}

