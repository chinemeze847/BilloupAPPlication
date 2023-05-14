package org.derby.billou;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * This is our Driver class or main class that
 * runs our entire program
 */
public class Billou {
	static boolean hadError = false;
	static boolean hadRuntimeError = false;

	private static final Interpreter interpreter = new Interpreter();

	/**
	 * This is where the entire program starts from
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		if (args.length > 1) {//if the command line arg is greater than one
			                  //exit with status of 79
			System.out.println("Usage: Billou [script]");
			System.exit(79);
		} else if (args.length == 1) { //if command line arg is 1 run the file
			runFile(args[0]);
		} else {
			runPrompt(); //else just prompt the user to enter a value
		}
	}

	/**
	 * This reads the file from the commandline
	 * @param path
	 * @throws IOException
	 */
	private static void runFile(String path) throws IOException {
		//picks the file from a specified path and converts it to streams of bytes
		byte[] bytes = Files.readAllBytes(Paths.get(path));

		//calls the run function which in turn runs the file
		run(new String(bytes, Charset.defaultCharset()));

		// Indicate an error in the exit code.
		if (hadError) System.exit(65);
		if (hadRuntimeError) System.exit(70);
	}

	/**
	 * This function prompts the user to enter an
	 * expression to be evaluated
	 * @throws IOException
	 */
	private static void runPrompt() throws IOException {
		//it provides an input stream where user can enter something
		InputStreamReader input = new InputStreamReader(System.in);
		BufferedReader reader = new BufferedReader(input);

		//This for loop will run till the end of the file
		for (;;) {
			System.out.print(">>> ");

			//This reads each line of the file
			String line = reader.readLine();
			if (line == null) break;
			run(line);

			// If the user makes a mistake, it shouldn’t kill their entire session.
			hadError = false;
		}
	}

	/**
	 * This method runs the file in question
	 * @param source is the data or expression to be evaluated
	 */
	private static void run(String source) {

		//The scanner object will be used to scan the source
		Scanner scanner = new Scanner(source);

		//It then breaks it to respective tokens
		List<Token> tokens = scanner.scanTokens();

		//This is an object of a parser
		Parser parser = new Parser(tokens);

		//produces a syntax tree
		List<Statement> statements = parser.parse();

		// Stop if there was a syntax error.
		if (hadError) return;

		//Interpret the syntax tree
		interpreter.interpret(statements);

	}

	/**
	 * This method handles errors
	 * @param line represents the line the error occured
	 * @param message the user would see when the error occurs
	 */
	static void error(int line, String message) {
		report(line, "", message);
	}

	/**
	 * this reports the error
	 * @param line
	 * @param where the place in the line
	 * @param message is the actual report
	 */
	private static void report(int line, String where,
			String message) {
		System.err.println(
				"[line " + line + "] Error" + where + ": " + message);
		hadError = true;
	}

	/**
	 *
	 * @param token
	 * @param message
	 */
	static void error(Token token, String message) {
		if (token.type == TokenType.EOF) {
			report(token.line, " at end", message);
		} else {
			report(token.line, " at '" + token.lexeme + "'", message);
		}
	}

	//Parsing Expressions token-error
	//Evaluating Expressions runtime-error-method
	static void runtimeError(RuntimeError error) {
		System.err.println(error.getMessage() +
				"\n[line " + error.token.line + "]");
		hadRuntimeError = true;
	}
}