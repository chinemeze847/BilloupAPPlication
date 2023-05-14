package org.derby.billou;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expression.Visitor<Void>, Statement.Visitor<Void> {
	private final Interpreter interpreter;
	//> scopes-field
	private final Stack<Map<String, Boolean>> scopes = new Stack<>();

	private FunctionType currentFunction = FunctionType.NONE;


	Resolver(Interpreter interpreter) {
		this.interpreter = interpreter;
	}

	private enum FunctionType {
		NONE,
		FUNCTION,
		INITIALIZER,
		METHOD

	}

	private enum ClassType {
		NONE,
		CLASS,
		SUBCLASS
	}

	private ClassType currentClass = ClassType.NONE;

	void resolve(List<Statement> statements) {
		for (Statement statement : statements) {
			resolve(statement);
		}
	}

	@Override
	public Void visitBlockStmt(Statement.Block stmt) {
		beginScope();
		resolve(stmt.statements);
		endScope();
		return null;
	}
	//< visit-block-stmt
	//> Classes resolver-visit-class
	@Override
	public Void visitClassStmt(Statement.Class stmt) {

		ClassType enclosingClass = currentClass;
		currentClass = ClassType.CLASS;

	    //declares a statement
		declare(stmt.name);

		//defines a statement
		define(stmt.name);



		if (stmt.superclass != null &&
				stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
			Billou.error(stmt.superclass.name,
					"A class should not inherit from itself.");
		}


		if (stmt.superclass != null) {
			currentClass = ClassType.SUBCLASS;
			resolve(stmt.superclass);
		}


		if (stmt.superclass != null) {
			beginScope();
			scopes.peek().put("super", true);
		}

		beginScope();
		scopes.peek().put("this", true);


		for (Statement.Function method : stmt.methods) {
			FunctionType declaration = FunctionType.METHOD;
			//> resolver-initializer-type
			if (method.name.lexeme.equals("init")) {
				declaration = FunctionType.INITIALIZER;
			}

			//< resolver-initializer-type
			resolveFunction(method, declaration); // [local]
		}

		//> resolver-end-this-scope
		endScope();

		if (stmt.superclass != null) endScope();

		currentClass = enclosingClass;
		return null;
	}

	@Override
	public Void visitExpressionStmt(Statement.Expression stmt) {
		resolve(stmt.expression);
		return null;
	}

	@Override
	public Void visitFunctionStmt(Statement.Function stmt) {
		declare(stmt.name);
		define(stmt.name);

		resolveFunction(stmt, FunctionType.FUNCTION);

		return null;
	}
	@Override
	public Void visitIfStmt(Statement.If stmt) {
		resolve(stmt.condition);
		resolve(stmt.thenBranch);
		if (stmt.elseBranch != null) resolve(stmt.elseBranch);
		return null;
	}

	@Override
	public Void visitPrintStmt(Statement.Print stmt) {
		resolve(stmt.expression);
		return null;
	}

	@Override
	public Void visitReturnStmt(Statement.Return stmt) {
		//> return-from-top
		if (currentFunction == FunctionType.NONE) {
			Billou.error(stmt.keyword, "Can't return from top-level code.");
		}

		//< return-from-top
		if (stmt.value != null) {
			//> Classes return-in-initializer
			if (currentFunction == FunctionType.INITIALIZER) {
				Billou.error(stmt.keyword,
						"Can't return a value from an initializer.");
			}
			resolve(stmt.value);
		}

		return null;
	}

	@Override
	public Void visitVarStmt(Statement.Var stmt) {
		declare(stmt.name);
		if (stmt.initializer != null) {
			resolve(stmt.initializer);
		}
		define(stmt.name);
		return null;
	}

	@Override
	public Void visitWhileStmt(Statement.While stmt) {
		resolve(stmt.condition);
		resolve(stmt.body);
		return null;
	}

	@Override
	public Void visitAssignExpr(Expression.Assign expr) {
		resolve(expr.value);
		resolveLocal(expr, expr.name);
		return null;
	}

	@Override
	public Void visitBinaryExpr(Expression.Binary expr) {
		resolve(expr.left);
		resolve(expr.right);
		return null;
	}

	@Override
	public Void visitCallExpr(Expression.Call expr) {
		resolve(expr.callee);

		for (Expression argument : expr.arguments) {
			resolve(argument);
		}

		return null;
	}

	@Override
	public Void visitGetExpr(Expression.Get expr) {
		resolve(expr.object);
		return null;
	}

	@Override
	public Void visitGroupingExpr(Expression.Grouping expr) {
		resolve(expr.expression);
		return null;
	}

	@Override
	public Void visitLiteralExpr(Expression.Literal expr) {
		return null;
	}

	@Override
	public Void visitLogicalExpr(Expression.Logical expr) {
		resolve(expr.left);
		resolve(expr.right);
		return null;
	}

	@Override
	public Void visitSetExpr(Expression.Set expr) {
		resolve(expr.value);
		resolve(expr.object);
		return null;
	}

	@Override
	public Void visitSuperExpr(Expression.Super expr) {
		//> invalid-super
		if (currentClass == ClassType.NONE) {
			Billou.error(expr.keyword,
					"Can't use 'super' outside of a class.");
		} else if (currentClass != ClassType.SUBCLASS) {
			Billou.error(expr.keyword,
					"Can't use 'super' in a class with no superclass.");
		}

		//< invalid-super
		resolveLocal(expr, expr.keyword);
		return null;
	}

	@Override
	public Void visitThisExpr(Expression.This expr) {
		//> this-outside-of-class
		if (currentClass == ClassType.NONE) {
			Billou.error(expr.keyword,
					"Can't use 'this' outside of a class.");
			return null;
		}

		//< this-outside-of-class
		resolveLocal(expr, expr.keyword);
		return null;
	}

	@Override
	public Void visitUnaryExpr(Expression.Unary expr) {
		resolve(expr.right);
		return null;
	}

	@Override
	public Void visitVariableExpr(Expression.Variable expr) {
		if (!scopes.isEmpty() &&
				scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
			Billou.error(expr.name,
					"Can't read local variable in its own initializer.");
		}

		resolveLocal(expr, expr.name);
		return null;
	}

	private void resolve(Statement statement) {
		statement.accept(this);
	}

	private void resolve(Expression expression) {
		expression.accept(this);
	}

	private void resolveFunction(
			Statement.Function function, FunctionType type) {
		FunctionType enclosingFunction = currentFunction;
		currentFunction = type;


		beginScope();
		for (Token param : function.params) {
			declare(param);
			define(param);
		}
		resolve(function.body);
		endScope();
		//> restore-current-function
		currentFunction = enclosingFunction;
		//< restore-current-function
	}

	private void beginScope() {
		scopes.push(new HashMap<String, Boolean>());
	}

	private void endScope() {
		scopes.pop();
	}

	private void declare(Token name) {
		if (scopes.isEmpty()) return;

		Map<String, Boolean> scope = scopes.peek();
		//> duplicate-variable
		if (scope.containsKey(name.lexeme)) {
			Billou.error(name,
					"Already a variable with this name in this scope.");
		}

		//< duplicate-variable
		scope.put(name.lexeme, false);
	}

	private void define(Token name) {
		if (scopes.isEmpty()) return;
		scopes.peek().put(name.lexeme, true);
	}

	private void resolveLocal(Expression expression, Token name) {
		for (int i = scopes.size() - 1; i >= 0; i--) {
			if (scopes.get(i).containsKey(name.lexeme)) {
				interpreter.resolve(expression, scopes.size() - 1 - i);
				return;
			}
		}
	}

}
