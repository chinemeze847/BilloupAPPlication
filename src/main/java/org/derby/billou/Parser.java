package org.derby.billou;

import static org.derby.billou.TokenType.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class handles syntax analysis phase
 * Each method in this class produces a syntax tree
 * for the rule and returns it to its caller
 *
 * checks if the source code follows the
 * grammatical rules of the programming language.
 * by creating a parse tree or abstract syntax tree (AST) of the source code
 *
 * it uses the Recursive descent algorithm to produce
 * this syntax tree
 */
class Parser {
	private static class ParseError extends RuntimeException {}
	private final List<Token> tokens;
	private int current = 0;

	Parser(List<Token> tokens) {
		this.tokens = tokens;
	}
	List<Statement> parse() {
		List<Statement> statements = new ArrayList<>();
		while (!isAtEnd()) {

			//> parse-declaration
			statements.add(declaration());
			//< parse-declaration
		}

		return statements; // [parse-error-handling]
	}

	private Expression expression() {

		//> Statements and State expression
		return assignment();
		//< Statements and State expression
	}

	private Statement declaration() {
		try {
			//> Classes match-class
			if (match(CLASS)) return classDeclaration();
			//< Classes match-class
			//> Functions match-fun
			if (match(FUN)) return function("function");
			//< Functions match-fun
			if (match(VAR)) return varDeclaration();

			return statement();
		} catch (ParseError error) {
			synchronize();
			return null;
		}
	}
	private Statement classDeclaration() {
		Token name = consume(IDENTIFIER, "Expect class name.");
		//> Inheritance parse-superclass

		Expression.Variable superclass = null;
		if (match(LESS)) {
			consume(IDENTIFIER, "Expect superclass name.");
			superclass = new Expression.Variable(previous());
		}

		//< Inheritance parse-superclass
		consume(LEFT_BRACE, "Expect '{' before class body.");

		List<Statement.Function> methods = new ArrayList<>();
		while (!check(RIGHT_BRACE) && !isAtEnd()) {
			methods.add(function("method"));
		}

		consume(RIGHT_BRACE, "Expect '}' after class body.");

		return new Statement.Class(name, superclass, methods);

	}

	private Statement statement() {

		if (match(FOR)) return forStatement();

		if (match(IF)) return ifStatement();

		if (match(PRINT)) return printStatement();

		if (match(RETURN)) return returnStatement();

		if (match(WHILE)) return whileStatement();

		if (match(LEFT_BRACE)) return new Statement.Block(block());


		return expressionStatement();
	}

	private Statement forStatement() {
		consume(LEFT_PAREN, "Expect '(' after 'for'.");

		//> for-initializer
		Statement initializer;
		if (match(SEMICOLON)) {
			initializer = null;
		} else if (match(VAR)) {
			initializer = varDeclaration();
		} else {
			initializer = expressionStatement();
		}

		Expression condition = null;
		if (!check(SEMICOLON)) {
			condition = expression();
		}
		consume(SEMICOLON, "Expect ';' after loop condition.");
		//< for-condition
		//> for-increment

		Expression increment = null;
		if (!check(RIGHT_PAREN)) {
			increment = expression();
		}
		consume(RIGHT_PAREN, "Expect ')' after for clauses.");
		//< for-increment
		//> for-body
		Statement body = statement();

		//> for-desugar-increment
		if (increment != null) {
			body = new Statement.Block(
					Arrays.asList(
							body,
							new Statement.Expression(increment)));
		}

		if (condition == null) condition = new Expression.Literal(true);
		body = new Statement.While(condition, body);

		if (initializer != null) {
			body = new Statement.Block(Arrays.asList(initializer, body));
		}

		return body;

	}

	private Statement ifStatement() {
		consume(LEFT_PAREN, "Expect '(' after 'if'.");
		Expression condition = expression();
		consume(RIGHT_PAREN, "Expect ')' after if condition."); // [parens]

		Statement thenBranch = statement();
		Statement elseBranch = null;
		if (match(ELSE)) {
			elseBranch = statement();
		}

		return new Statement.If(condition, thenBranch, elseBranch);
	}

	private Statement printStatement() {
		Expression value = expression();
		consume(SEMICOLON, "Expect ';' after value.");
		return new Statement.Print(value);
	}
	private Statement returnStatement() {
		Token keyword = previous();
		Expression value = null;
		if (!check(SEMICOLON)) {
			value = expression();
		}

		consume(SEMICOLON, "Expect ';' after return value.");
		return new Statement.Return(keyword, value);
	}

	private Statement varDeclaration() {
		Token name = consume(IDENTIFIER, "Expect variable name.");

		Expression initializer = null;
		if (match(EQUAL)) {
			initializer = expression();
		}

		consume(SEMICOLON, "Expect ';' after variable declaration.");
		return new Statement.Var(name, initializer);
	}

	private Statement whileStatement() {
		consume(LEFT_PAREN, "Expect '(' after 'while'.");
		Expression condition = expression();
		consume(RIGHT_PAREN, "Expect ')' after condition.");
		Statement body = statement();

		return new Statement.While(condition, body);
	}

	private Statement expressionStatement() {
		Expression expression = expression();
		consume(SEMICOLON, "Expect ';' after expression.");
		return new Statement.Expression(expression);
	}

	private Statement.Function function(String kind) {
		Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
		//> parse-parameters
		consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
		List<Token> parameters = new ArrayList<>();
		if (!check(RIGHT_PAREN)) {
			do {
				if (parameters.size() >= 255) {
					error(peek(), "Can't have more than 255 parameters.");
				}

				parameters.add(
						consume(IDENTIFIER, "Expect parameter name."));
			} while (match(COMMA));
		}
		consume(RIGHT_PAREN, "Expect ')' after parameters.");
		//< parse-parameters
		//> parse-body

		consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
		List<Statement> body = block();
		return new Statement.Function(name, parameters, body);
		//< parse-body
	}

	private List<Statement> block() {
		List<Statement> statements = new ArrayList<>();

		while (!check(RIGHT_BRACE) && !isAtEnd()) {
			statements.add(declaration());
		}

		consume(RIGHT_BRACE, "Expect '}' after block.");
		return statements;
	}
	//< Statements and State block
	//> Statements and State parse-assignment
	private Expression assignment() {

		Expression expression = or();
		//< Control Flow or-in-assignment

		if (match(EQUAL)) {
			Token equals = previous();
			Expression value = assignment();

			if (expression instanceof Expression.Variable) {
				Token name = ((Expression.Variable) expression).name;
				return new Expression.Assign(name, value);
				//> Classes assign-set
			} else if (expression instanceof Expression.Get) {
				Expression.Get get = (Expression.Get) expression;
				return new Expression.Set(get.object, get.name, value);
				//< Classes assign-set
			}

			error(equals, "Invalid assignment target."); // [no-throw]
		}

		return expression;
	}

	private Expression or() {
		Expression expression = and();

		while (match(OR)) {
			Token operator = previous();
			Expression right = and();
			expression = new Expression.Logical(expression, operator, right);
		}

		return expression;
	}
	private Expression and() {
		Expression expression = equality();

		while (match(AND)) {
			Token operator = previous();
			Expression right = equality();
			expression = new Expression.Logical(expression, operator, right);
		}

		return expression;
	}

	private Expression equality() {
		Expression expression = comparison();

		while (match(BANG_EQUAL, EQUAL_EQUAL)) {
			Token operator = previous();
			Expression right = comparison();
			expression = new Expression.Binary(expression, operator, right);
		}

		return expression;
	}
	//< equality
	//> comparison
	private Expression comparison() {
		Expression expression = term();

		while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
			Token operator = previous();
			Expression right = term();
			expression = new Expression.Binary(expression, operator, right);
		}

		return expression;
	}

	private Expression term() {
		Expression expression = factor();

		while (match(MINUS, PLUS)) {
			Token operator = previous();
			Expression right = factor();
			expression = new Expression.Binary(expression, operator, right);
		}

		return expression;
	}

	private Expression factor() {
		Expression expression = unary();

		while (match(SLASH, STAR)) {
			Token operator = previous();
			Expression right = unary();
			expression = new Expression.Binary(expression, operator, right);
		}

		return expression;
	}

	private Expression unary() {
		if (match(BANG, MINUS)) {
			Token operator = previous();
			Expression right = unary();
			return new Expression.Unary(operator, right);
		}

		return call();

	}

	private Expression finishCall(Expression callee) {
		List<Expression> arguments = new ArrayList<>();
		if (!check(RIGHT_PAREN)) {
			do {
				//> check-max-arity
				if (arguments.size() >= 255) {
					error(peek(), "Can't have more than 255 arguments.");
				}
				//< check-max-arity
				arguments.add(expression());
			} while (match(COMMA));
		}

		Token paren = consume(RIGHT_PAREN,
				"Expect ')' after arguments.");

		return new Expression.Call(callee, paren, arguments);
	}
	//< Functions finish-call
	//> Functions call
	private Expression call() {
		Expression expression = primary();

		while (true) { // [while-true]
			if (match(LEFT_PAREN)) {
				expression = finishCall(expression);
				//> Classes parse-property
			} else if (match(DOT)) {
				Token name = consume(IDENTIFIER,
						"Expect property name after '.'.");
				expression = new Expression.Get(expression, name);
				//< Classes parse-property
			} else {
				break;
			}
		}

		return expression;
	}
	//< Functions call
	//> primary
	private Expression primary() {
		if (match(FALSE)) return new Expression.Literal(false);
		if (match(TRUE)) return new Expression.Literal(true);
		if (match(NIL)) return new Expression.Literal(null);

		if (match(NUMBER, STRING)) {
			return new Expression.Literal(previous().literal);
		}
		//> Inheritance parse-super

		if (match(SUPER)) {
			Token keyword = previous();
			consume(DOT, "Expect '.' after 'super'.");
			Token method = consume(IDENTIFIER,
					"Expect superclass method name.");
			return new Expression.Super(keyword, method);
		}


		if (match(THIS)) return new Expression.This(previous());


		if (match(IDENTIFIER)) {
			return new Expression.Variable(previous());
		}
		//< Statements and State parse-identifier

		if (match(LEFT_PAREN)) {
			Expression expression = expression();
			consume(RIGHT_PAREN, "Expect ')' after expression.");
			return new Expression.Grouping(expression);
		}
		//> primary-error

		throw error(peek(), "Expect expression.");
		//< primary-error
	}
	//< primary
	//> match
	private boolean match(TokenType... types) {
		for (TokenType type : types) {
			if (check(type)) {
				move();
				return true;
			}
		}

		return false;
	}
	//< match
	//> consume
	private Token consume(TokenType type, String message) {
		if (check(type)) return move();

		throw error(peek(), message);
	}
	//< consume
	//> check
	private boolean check(TokenType type) {
		if (isAtEnd()) return false;
		return peek().type == type;
	}
	//< check
	//> advance
	private Token move() {
		if (!isAtEnd()) current++;
		return previous();
	}

	private boolean isAtEnd() {
		return peek().type == EOF;
	}

	private Token peek() {
		return tokens.get(current);
	}

	private Token previous() {
		return tokens.get(current - 1);
	}

	private ParseError error(Token token, String message) {
		Billou.error(token, message);
		return new ParseError();
	}

	/**
	 * Discards tokens until we’re right at the beginning of the next statement.
	 */
	private void synchronize() {
		move();

		while (!isAtEnd()) {
			if (previous().type == SEMICOLON) return;

			switch (peek().type) {
			case CLASS:
			case FUN:
			case VAR:
			case FOR:
			case IF:
			case WHILE:
			case PRINT:
			case RETURN:
				return;
			}

			move();
		}
	}

}
