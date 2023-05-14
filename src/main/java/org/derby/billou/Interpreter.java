package org.derby.billou;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates Expressions and Statements
 */
class Interpreter implements Expression.Visitor<Object>,
		Statement.Visitor<Void> {
	final Env globals = new Env();
	private Env env = globals;

	private final Map<Expression, Integer> locals = new HashMap<>();

	Interpreter() {
		globals.define("clock", new BillouCallable() {
			@Override
			public int arity() { return 0; }

			@Override
			public Object call(Interpreter interpreter,
					List<Object> arguments) {
				return (double)System.currentTimeMillis() / 1000.0;
			}

			@Override
			public String toString() { return "<native fn>"; }
		});
	}

	//> Statements and State interpret
	void interpret(List<Statement> statements) {
		try {
			for (Statement statement : statements) {
				execute(statement);
			}
		} catch (RuntimeError error) {
			Billou.runtimeError(error);
		}
	}

	private Object evaluate(Expression expression) {
		return expression.accept(this);
	}

	private void execute(Statement statement) {
		statement.accept(this);
	}

	void resolve(Expression expression, int depth) {
		locals.put(expression, depth);
	}

	void executeBlock(List<Statement> statements,
			Env env) {
		Env previous = this.env;
		try {
			this.env = env;

			for (Statement statement : statements) {
				execute(statement);
			}
		} finally {
			this.env = previous;
		}
	}

	@Override
	public Void visitBlockStmt(Statement.Block stmt) {
		executeBlock(stmt.statements, new Env(env));
		return null;
	}

	@Override
	public Void visitClassStmt(Statement.Class stmt) {
		//> Inheritance interpret-superclass
		Object superclass = null;
		if (stmt.superclass != null) {
			superclass = evaluate(stmt.superclass);
			if (!(superclass instanceof BillouClass)) {
				throw new RuntimeError(stmt.superclass.name,
						"Superclass must be a class.");
			}
		}

		//< Inheritance interpret-superclass
		env.define(stmt.name.lexeme, null);
		//> Inheritance begin-superclass-environment

		if (stmt.superclass != null) {
			env = new Env(env);
			env.define("super", superclass);
		}


		Map<String, BillouFunction> methods = new HashMap<>();
		for (Statement.Function method : stmt.methods) {


			//> interpreter-method-initializer
			BillouFunction function = new BillouFunction(method, env,
					method.name.lexeme.equals("init"));
			//< interpreter-method-initializer
			methods.put(method.name.lexeme, function);
		}

		//> Inheritance interpreter-construct-class
		BillouClass klass = new BillouClass(stmt.name.lexeme,
				(BillouClass)superclass, methods);
		//> end-superclass-environment

		if (superclass != null) {
			env = env.enclosing;
		}

		env.assign(stmt.name, klass);
		return null;
	}

	@Override
	public Void visitExpressionStmt(Statement.Expression stmt) {
		evaluate(stmt.expression);
		return null;
	}

	@Override
	public Void visitFunctionStmt(Statement.Function stmt) {

		//construct class
		BillouFunction function = new BillouFunction(stmt, env,
				false);
		//< Classes construct-function
		env.define(stmt.name.lexeme, function);
		return null;
	}

	@Override
	public Void visitIfStmt(Statement.If stmt) {
		if (isTruthy(evaluate(stmt.condition))) {
			execute(stmt.thenBranch);
		} else if (stmt.elseBranch != null) {
			execute(stmt.elseBranch);
		}
		return null;
	}

	@Override
	public Void visitPrintStmt(Statement.Print stmt) {
		Object value = evaluate(stmt.expression);
		System.out.println(stringify(value));
		return null;
	}

	@Override
	public Void visitReturnStmt(Statement.Return stmt) {
		Object value = null;
		if (stmt.value != null) value = evaluate(stmt.value);

		throw new ReturnStatement(value);
	}

	@Override
	public Void visitVarStmt(Statement.Var stmt) {
		Object value = null;
		if (stmt.initializer != null) {
			value = evaluate(stmt.initializer);
		}

		env.define(stmt.name.lexeme, value);
		return null;
	}
	//< Statements and State visit-var
	//> Control Flow visit-while
	@Override
	public Void visitWhileStmt(Statement.While stmt) {
		while (isTruthy(evaluate(stmt.condition))) {
			execute(stmt.body);
		}
		return null;
	}
	//< Control Flow visit-while
	//> Statements and State visit-assign
	@Override
	public Object visitAssignExpr(Expression.Assign expr) {
		Object value = evaluate(expr.value);
/* Statements and State visit-assign < Resolving and Binding resolved-assign
    environment.assign(expr.name, value);
*/
		//> Resolving and Binding resolved-assign

		Integer distance = locals.get(expr);
		if (distance != null) {
			env.assignAt(distance, expr.name, value);
		} else {
			globals.assign(expr.name, value);
		}

		//< Resolving and Binding resolved-assign
		return value;
	}
	//< Statements and State visit-assign
	//> visit-binary
	@Override
	public Object visitBinaryExpr(Expression.Binary expr) {
		Object left = evaluate(expr.left);
		Object right = evaluate(expr.right); // [left]

		switch (expr.operator.type) {
		//> binary-equality
		case BANG_EQUAL: return !isEqual(left, right);
		case EQUAL_EQUAL: return isEqual(left, right);
		//< binary-equality
		//> binary-comparison
		case GREATER:
			//> check-greater-operand
			checkNumberOperands(expr.operator, left, right);
			//< check-greater-operand
			return (double)left > (double)right;
		case GREATER_EQUAL:
			//> check-greater-equal-operand
			checkNumberOperands(expr.operator, left, right);
			//< check-greater-equal-operand
			return (double)left >= (double)right;
		case LESS:
			//> check-less-operand
			checkNumberOperands(expr.operator, left, right);
			//< check-less-operand
			return (double)left < (double)right;
		case LESS_EQUAL:
			//> check-less-equal-operand
			checkNumberOperands(expr.operator, left, right);
			//< check-less-equal-operand
			return (double)left <= (double)right;
		//< binary-comparison
		case MINUS:
			//> check-minus-operand
			checkNumberOperands(expr.operator, left, right);
			//< check-minus-operand
			return (double)left - (double)right;
		//> binary-plus
		case PLUS:
			if (left instanceof Double && right instanceof Double) {
				return (double)left + (double)right;
			} // [plus]

			if (left instanceof String && right instanceof String) {
				return (String)left + (String)right;
			}

/* Evaluating Expressions binary-plus < Evaluating Expressions string-wrong-type
        break;
*/
			//> string-wrong-type
			throw new RuntimeError(expr.operator,
					"Operands must be two numbers or two strings.");
			//< string-wrong-type
			//< binary-plus
		case SLASH:
			//> check-slash-operand
			checkNumberOperands(expr.operator, left, right);
			//< check-slash-operand
			return (double)left / (double)right;
		case STAR:
			//> check-star-operand
			checkNumberOperands(expr.operator, left, right);
			//< check-star-operand
			return (double)left * (double)right;
		}

		// Unreachable.
		return null;
	}
	//< visit-binary
	//> Functions visit-call
	@Override
	public Object visitCallExpr(Expression.Call expr) {
		Object callee = evaluate(expr.callee);

		List<Object> arguments = new ArrayList<>();
		for (Expression argument : expr.arguments) { // [in-order]
			arguments.add(evaluate(argument));
		}

		//> check-is-callable
		if (!(callee instanceof BillouCallable)) {
			throw new RuntimeError(expr.paren,
					"Can only call functions and classes.");
		}

		//< check-is-callable
		BillouCallable function = (BillouCallable)callee;
		//> check-arity
		if (arguments.size() != function.arity()) {
			throw new RuntimeError(expr.paren, "Expected " +
					function.arity() + " arguments but got " +
					arguments.size() + ".");
		}

		//< check-arity
		return function.call(this, arguments);
	}
	//< Functions visit-call
	//> Classes interpreter-visit-get
	@Override
	public Object visitGetExpr(Expression.Get expr) {
		Object object = evaluate(expr.object);
		if (object instanceof BillouInstance) {
			return ((BillouInstance) object).get(expr.name);
		}

		throw new RuntimeError(expr.name,
				"Only instances have properties.");
	}
	//< Classes interpreter-visit-get
	//> visit-grouping
	@Override
	public Object visitGroupingExpr(Expression.Grouping expr) {
		return evaluate(expr.expression);
	}
	//< visit-grouping
	//> visit-literal
	@Override
	public Object visitLiteralExpr(Expression.Literal expr) {
		return expr.value;
	}
	//< visit-literal
	//> Control Flow visit-logical
	@Override
	public Object visitLogicalExpr(Expression.Logical expr) {
		Object left = evaluate(expr.left);

		if (expr.operator.type == TokenType.OR) {
			if (isTruthy(left)) return left;
		} else {
			if (!isTruthy(left)) return left;
		}

		return evaluate(expr.right);
	}
	//< Control Flow visit-logical
	//> Classes interpreter-visit-set
	@Override
	public Object visitSetExpr(Expression.Set expr) {
		Object object = evaluate(expr.object);

		if (!(object instanceof BillouInstance)) { // [order]
			throw new RuntimeError(expr.name,
					"Only instances have fields.");
		}

		Object value = evaluate(expr.value);
		((BillouInstance)object).set(expr.name, value);
		return value;
	}
	//< Classes interpreter-visit-set
	//> Inheritance interpreter-visit-super
	@Override
	public Object visitSuperExpr(Expression.Super expr) {
		int distance = locals.get(expr);
		BillouClass superclass = (BillouClass) env.getAt(
				distance, "super");
		//> super-find-this

		BillouInstance object = (BillouInstance) env.getAt(
				distance - 1, "this");
		//< super-find-this
		//> super-find-method

		BillouFunction method = superclass.findMethod(expr.method.lexeme);
		//> super-no-method

		if (method == null) {
			throw new RuntimeError(expr.method,
					"Undefined property '" + expr.method.lexeme + "'.");
		}

		//< super-no-method
		return method.bind(object);
		//< super-find-method
	}
	//< Inheritance interpreter-visit-super
	//> Classes interpreter-visit-this
	@Override
	public Object visitThisExpr(Expression.This expr) {
		return lookUpVariable(expr.keyword, expr);
	}
	//< Classes interpreter-visit-this
	//> visit-unary
	@Override
	public Object visitUnaryExpr(Expression.Unary expr) {
		Object right = evaluate(expr.right);

		switch (expr.operator.type) {
		//> unary-bang
		case BANG:
			return !isTruthy(right);
		//< unary-bang
		case MINUS:
			//> check-unary-operand
			checkNumberOperand(expr.operator, right);
			//< check-unary-operand
			return -(double)right;
		}

		// Unreachable.
		return null;
	}
	//< visit-unary
	//> Statements and State visit-variable
	@Override
	public Object visitVariableExpr(Expression.Variable expr) {
/* Statements and State visit-variable < Resolving and Binding call-look-up-variable
    return environment.get(expr.name);
*/
		//> Resolving and Binding call-look-up-variable
		return lookUpVariable(expr.name, expr);
		//< Resolving and Binding call-look-up-variable
	}
	//> Resolving and Binding look-up-variable
	private Object lookUpVariable(Token name, Expression expression) {
		Integer distance = locals.get(expression);
		if (distance != null) {
			return env.getAt(distance, name.lexeme);
		} else {
			return globals.get(name);
		}
	}
	//< Resolving and Binding look-up-variable
	//< Statements and State visit-variable
	//> check-operand
	private void checkNumberOperand(Token operator, Object operand) {
		if (operand instanceof Double) return;
		throw new RuntimeError(operator, "Operand must be a number.");
	}
	//< check-operand
	//> check-operands
	private void checkNumberOperands(Token operator,
			Object left, Object right) {
		if (left instanceof Double && right instanceof Double) return;
		// [operand]
		throw new RuntimeError(operator, "Operands must be numbers.");
	}
	//< check-operands
	//> is-truthy
	private boolean isTruthy(Object object) {
		if (object == null) return false;
		if (object instanceof Boolean) return (boolean)object;
		return true;
	}
	//< is-truthy
	//> is-equal
	private boolean isEqual(Object a, Object b) {
		if (a == null && b == null) return true;
		if (a == null) return false;

		return a.equals(b);
	}
	//< is-equal
	//> stringify
	private String stringify(Object object) {
		if (object == null) return "nil";

		if (object instanceof Double) {
			String text = object.toString();
			if (text.endsWith(".0")) {
				text = text.substring(0, text.length() - 2);
			}
			return text;
		}

		return object.toString();
	}
	//< stringify
}