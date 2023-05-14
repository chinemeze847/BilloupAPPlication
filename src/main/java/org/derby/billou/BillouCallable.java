package org.derby.billou;


import java.util.List;

interface BillouCallable {
	int arity();
	Object call(Interpreter interpreter, List<Object> arguments);
}