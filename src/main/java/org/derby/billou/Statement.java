package org.derby.billou;


import java.util.List;

/**
 * The different type of statements
 * in our language
 */
abstract class Statement {
	interface Visitor<R> {
		R visitBlockStmt(Block stmt);
		R visitClassStmt(Class stmt);
		R visitExpressionStmt(Expression stmt);
		R visitFunctionStmt(Function stmt);
		R visitIfStmt(If stmt);
		R visitPrintStmt(Print stmt);
		R visitReturnStmt(Return stmt);
		R visitVarStmt(Var stmt);
		R visitWhileStmt(While stmt);
	}

	static class Block extends Statement {
		Block(List<Statement> statements) {
			this.statements = statements;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visitBlockStmt(this);
		}

		final List<Statement> statements;
	}

	static class Class extends Statement {
		Class(Token name,
				org.derby.billou.Expression.Variable superclass,
				List<Statement.Function> methods) {
			this.name = name;
			this.superclass = superclass;
			this.methods = methods;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visitClassStmt(this);
		}

		final Token name;
		final org.derby.billou.Expression.Variable superclass;
		final List<Statement.Function> methods;
	}

	static class Expression extends Statement {
		Expression(org.derby.billou.Expression expression) {
			this.expression = expression;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visitExpressionStmt(this);
		}

		final org.derby.billou.Expression expression;
	}

	static class Function extends Statement {
		Function(Token name, List<Token> params, List<Statement> body) {
			this.name = name;
			this.params = params;
			this.body = body;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visitFunctionStmt(this);
		}

		final Token name;
		final List<Token> params;
		final List<Statement> body;
	}

	static class If extends Statement {
		If(org.derby.billou.Expression condition, Statement thenBranch, Statement elseBranch) {
			this.condition = condition;
			this.thenBranch = thenBranch;
			this.elseBranch = elseBranch;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visitIfStmt(this);
		}

		final org.derby.billou.Expression condition;
		final Statement thenBranch;
		final Statement elseBranch;
	}

	static class Print extends Statement {
		Print(org.derby.billou.Expression expression) {
			this.expression = expression;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visitPrintStmt(this);
		}

		final org.derby.billou.Expression expression;
	}

	static class Return extends Statement {
		Return(Token keyword, org.derby.billou.Expression value) {
			this.keyword = keyword;
			this.value = value;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visitReturnStmt(this);
		}

		final Token keyword;
		final org.derby.billou.Expression value;
	}

	static class Var extends Statement {
		Var(Token name, org.derby.billou.Expression initializer) {
			this.name = name;
			this.initializer = initializer;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visitVarStmt(this);
		}

		final Token name;
		final org.derby.billou.Expression initializer;
	}

	static class While extends Statement {
		While(org.derby.billou.Expression condition, Statement body) {
			this.condition = condition;
			this.body = body;
		}

		@Override
		<R> R accept(Visitor<R> visitor) {
			return visitor.visitWhileStmt(this);
		}

		final org.derby.billou.Expression condition;
		final Statement body;
	}

	abstract <R> R accept(Visitor<R> visitor);
}
