import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;

public class MLang {

	// 12:27-
	public static void main(String[] args) throws IOException {
		String code = "int main() {\n" +
				"\t\tint foo = 42 + 1;\n" +
				"\t\tint bar = 3 + 101 * foo / 2 + 1;\n" +
				"\t\tputs(bar, 2 + 1 * 4, 3 > 3);\n" +
				"\n" +
				"\t\twhile (foo > 0) {\n" +
				"\t\t\tfoo = foo - 1;\n" +
				"\t\t\tputs(foo);\n" +
				"\t\t\tif (foo > 5) {\n" +
				"\t\t\t\tputs(foo);\n" +
				"\t\t\t}\n" +
				"\t\t}\n" +
				"}";

		Path inputFile = Paths.get("prog.m");
		if (Files.exists(inputFile)) {
			String filecode = new String(Files.readAllBytes(inputFile), StandardCharsets.UTF_8);
			execute(filecode);
		} else {
			System.out.println(code);
			execute(code);
		}
	}

	private static void execute(String code) {
		MScanner mScanner = new MScanner(code);
		MParser1 parser = new MParser1(mScanner);
		Prog prog = parser.parseProg();

		MInterpreter interpreter = new MInterpreter();
		interpreter.run(prog);
	}

	public static class MSingleVarDecl implements MNode {

		private final MToken paramType;
		private final MToken paramName;

		public MSingleVarDecl(MToken paramType, MToken paramName) {
			this.paramType = paramType;
			this.paramName = paramName;
		}

		@Override
		public <T> T accept(MVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}
	public static class MFunction implements MNode {
		private final MToken returnType;
		private final MToken name;
		private final List<MSingleVarDecl> params;
		private final List<MStatement> body;

		public MFunction(MToken returnType, MToken name, List<MSingleVarDecl> params, List<MStatement> body) {
			this.returnType = returnType;
			this.name = name;
			this.params = params;
			this.body = body;
		}

		@Override
		public <T> T accept(MVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	public static class Prog implements MNode {

		private final List<MFunction> functions;

		public Prog(List<MFunction> functions) {
			this.functions = functions;
		}

		@Override
		public <T> T accept(MVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	public static class MInterpreter {
		private Map<String, Integer> typeSizes = new HashMap<>();
		private byte[] mem = new byte[4 * 1024];
		private Map<String, Integer> locations = new HashMap<>();
		private final int[] topStack = {0};

		public MInterpreter() {
			typeSizes.put("int", 4);
		}

		public void run(Prog prog) {
			for (MFunction function : prog.functions) {
				if (function.name.val.equals("main")) {
					run(function);
				}
			}
		}

		private void run(MFunction function) {
			run(function.body);
		}

		private void run(List<MStatement> block) {
			for (MStatement stmt : block) {
				stmt.accept(new MVisitor<Void>() {
					@Override
					public Void visit(MDeclarationStmt mDeclarationStmt) {
						String name = mDeclarationStmt.varName.val;
						locations.put(name, topStack[0]);
						if (mDeclarationStmt.expr != null) {
							int result = evalInt(mDeclarationStmt.expr, mem, locations);
							setMemInt(name, result);
						}
						topStack[0] += typeSizes.get(mDeclarationStmt.type.val);

						return super.visit(mDeclarationStmt);
					}

					@Override
					public Void visit(MExpressionStmt mExpressionStmt) {
						evalInt(mExpressionStmt.expr, mem, locations);

						return super.visit(mExpressionStmt);
					}

					@Override
					public Void visit(MWhileStatement whileStatement) {
						while (evalInt(whileStatement.condition, mem, locations) != 0) {
							run(whileStatement.block);
						}
						return super.visit(whileStatement);
					}

					@Override
					public Void visit(MIfStatement ifStatement) {
						if (evalInt(ifStatement.condition, mem, locations) != 0) {
							run(ifStatement.block);
						}
						return super.visit(ifStatement);
					}
				});
			}
		}

		private void setMemInt(String name, int result) {
			Integer startAdrs = locations.get(name);
			if (startAdrs == null) throw new AssertionError("unkown variable: " + name);
			for (int i = 0; i < 4; i++) {
				mem[startAdrs + i] = (byte) ((result >>> i * 8) & 0xFF);
			}
		}

		private int evalInt(MExpr expr, byte[] mem, Map<String, Integer> locations) {
			return expr.accept(new MVisitor<Integer>() {
				@Override
				public Integer visit(MLiteralExpr literalExpr) {
					return Integer.parseInt(literalExpr.token.val);
				}

				@Override
				public Integer visit(MVariableExpr mVariableExpr) {
					int loc = locations.get(mVariableExpr.token.val);
					int val = 0;
					for (int i = 0; i < 4; i++) {
						val |= mem[loc + i] << i * 8;
					}
					return val;
				}

				@Override
				public Integer visit(MMethodCallExpr mMethodCallExpr) {
					String methodName = mMethodCallExpr.name.val;
					if (methodName.equals("puts")) {
						System.out.println(evalInt(mMethodCallExpr.args.get(0), mem, locations));
						return 0;
					} else {
						throw new AssertionError(methodName);
					}
				}

				@Override
				public Integer visit(MBinaryExpr binaryExpr) {
					String op = binaryExpr.op.val;
					Integer leftVal = binaryExpr.left.accept(this);
					Integer rightVal = binaryExpr.right.accept(this);
					switch (op) {
						case "+":
							return leftVal + rightVal;
						case "-":
							return leftVal - rightVal;
						case "*":
							return leftVal * rightVal;
						case "/":
							return leftVal / rightVal;
						case "<":
							return leftVal < rightVal ? 1 : 0;
						case ">":
							return leftVal > rightVal ? 1 : 0;
						case "==":
							return leftVal == rightVal ? 1 : 0;
						case "=":
							MVariableExpr var = (MVariableExpr) binaryExpr.left;
							setMemInt(var.token.val, rightVal);
							return rightVal;
						default:
							throw new AssertionError(op);
					}
				}
			});
		}
	}

	public static ImmutableSet<String> keywords = ImmutableSet.of("while", "if", "for", "do", "return");
	public static Map<String, Integer> prios = new HashMap<>();
	static {
		prios.put("=", 1);
		prios.put("==", 2);
		prios.put("<", 2);
		prios.put(">", 2);
		prios.put("+", 3);
		prios.put("-", 3);
		prios.put("*", 4);
		prios.put("/", 4);
	}
	public enum MTokenType {
		IDENTIFIER,
		KEYWORD,
		NUMBER,
		OPERATOR,
		LPAREN,
		RPAREN,
		LCURLY,
		RCURLY,
		EOF,
		COMMA,
		SEMI
	}
	public interface MNode {
		<T> T accept(MVisitor<T> visitor);
	}
	public abstract static class MStatement implements MNode {
	}
	public static class MParser1 {

		private final MScanner scanner;

		public MParser1(MScanner scanner) {
			this.scanner = scanner;
		}

		public Prog parseProg() {
			List<MFunction> list = new LinkedList<>();
			while (scanner.peek().tokenType != MTokenType.EOF) {
				list.add(parseFunction());
			}
			return new Prog(list);
		}

		private MFunction parseFunction() {
			MToken returnType = expect(MTokenType.IDENTIFIER);
			MToken name = expect(MTokenType.IDENTIFIER);
			expect(MTokenType.LPAREN);
			List<MSingleVarDecl> params = new LinkedList<>();
			while (scanner.peek().tokenType != MTokenType.RPAREN) {
				MToken paramType = expect(MTokenType.IDENTIFIER);
				MToken paramName = expect(MTokenType.IDENTIFIER);
				params.add(new MSingleVarDecl(paramType, paramName));
				if (scanner.peek().tokenType != MTokenType.RPAREN)
					expect(MTokenType.COMMA);
			}
			expect(MTokenType.RPAREN);

			List<MStatement> body = parseBlock();

			return new MFunction(returnType, name, params, body);
		}

		private MStatement parseStatement() {
			if (scanner.peek(0).tokenType == MTokenType.IDENTIFIER &&
					scanner.peek(1).tokenType == MTokenType.IDENTIFIER) {
				return parseDeclaration();
			} else if (scanner.peek().tokenType == MTokenType.KEYWORD) {
				switch (scanner.peek().val) {
					case "while":
						return parseWhileStmt();
					case "if":
						return parseIfStmt();
					case "return":
						return parseReturnStmt();
					default:
						throw new AssertionError(scanner.peek());
				}
			} else {
				return parseExpressionStmt();
			}
		}

		private MStatement parseReturnStmt() {
			expectKeyword("return");

			MExpr expr = parseExpression();
			expect(MTokenType.SEMI);

			return new MReturnStatement(expr);
		}

		private MStatement parseIfStmt() {
			expectKeyword("if");

			expect(MTokenType.LPAREN);
			MExpr condition = parseExpression();
			expect(MTokenType.RPAREN);
			List<MStatement> block = parseBlock();
			return new MIfStatement(condition, block);
		}

		private MStatement parseWhileStmt() {
			expectKeyword("while");

			expect(MTokenType.LPAREN);
			MExpr condition = parseExpression();
			expect(MTokenType.RPAREN);
			List<MStatement> block = parseBlock();
			return new MWhileStatement(condition, block);
		}

		private void expectKeyword(String kw) {
			MToken keyword = expect(MTokenType.KEYWORD);
			if (!keyword.val.equals(kw)) throw new AssertionError("expected " + kw + " but was " + keyword);
		}

		private List<MStatement> parseBlock() {
			expect(MTokenType.LCURLY);
			List<MStatement> stmts = new LinkedList<>();
			while (scanner.peek().tokenType != MTokenType.RCURLY) {
				stmts.add(parseStatement());
			}
			expect(MTokenType.RCURLY);
			return stmts;
		}

		private MStatement parseExpressionStmt() {
			MExpr expr = parseExpression();
			expect(MTokenType.SEMI);
			return new MExpressionStmt(expr);
		}

		private MStatement parseDeclaration() {
			MToken type = scanner.next();
			MToken varName = scanner.next();
			MExpr expr = null;
			if (scanner.peek().tokenType == MTokenType.OPERATOR &&
					scanner.peek().val.equals("=")) {
				scanner.next();

				expr = parseExpression();
			}
			expect(MTokenType.SEMI);

			return new MDeclarationStmt(type, varName, expr);
		}

		private MToken expect(MTokenType type) {
			MToken token = scanner.next();
			if (token.tokenType != type) {
				throw new AssertionError(String.format("expected %s, but was %s", type, token.tokenType));
			}
			return token;
		}

		private MExpr parseExpression() {
			return parseExpression1(0);
		}

		private MExpr parseExpression1(int prio) {
			MExpr left = parseTokenExpr();

			while (scanner.peek().tokenType == MTokenType.OPERATOR &&
					prios.get(scanner.peek().val) > prio) {
				MToken opToken = scanner.next();
				left = new MBinaryExpr(left, opToken, parseExpression1(prios.get(opToken.val)));
			}
			return left;
		}

		private MExpr parseTokenExpr() {
			MToken token = scanner.next();
			switch (token.tokenType) {
				case NUMBER:
					return new MLiteralExpr(token);
				case IDENTIFIER:
					if (scanner.peek().tokenType == MTokenType.LPAREN) {
						return parseMethodCall(token);
					} else {
						return new MVariableExpr(token);
					}
			}
			throw new AssertionError(token);
		}

		private MMethodCallExpr parseMethodCall(MToken token) {
			expect(MTokenType.LPAREN);
			List<MExpr> args = new LinkedList<>();
			while (scanner.peek().tokenType != MTokenType.RPAREN) {
				args.add(parseExpression());
				if (scanner.peek().tokenType != MTokenType.RPAREN) {
					expect(MTokenType.COMMA);
				}
			}
			expect(MTokenType.RPAREN);
			return new MMethodCallExpr(token, args);
		}
	}

	public static class MReturnStatement extends MStatement {

		private final MExpr expr;

		public MReturnStatement(MExpr expr) {
			this.expr = expr;
		}

		@Override
		public <T> T accept(MVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}
	public static class MIfStatement extends MStatement {
		private final MExpr condition;
		private final List<MStatement> block;

		public MIfStatement(MExpr condition, List<MStatement> block) {
			this.condition = condition;
			this.block = block;
		}

		@Override
		public String toString() {
			return String.format("if (%s)\n{%s}", condition, block.stream()
					.map(stmt -> stmt.toString())
					.collect(Collectors.joining("\n")));
		}

		@Override
		public <T> T accept(MVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}
	public static class MWhileStatement extends MStatement {
		private final MExpr condition;
		private final List<MStatement> block;

		public MWhileStatement(MExpr condition, List<MStatement> block) {
			this.condition = condition;
			this.block = block;
		}

		@Override
		public String toString() {
			return String.format("while (%s)\n{%s}", condition, block.stream()
					.map(stmt -> stmt.toString())
					.collect(Collectors.joining("\n")));
		}

		@Override
		public <T> T accept(MVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}
	public static class MExpressionStmt extends MStatement {
		private final MExpr expr;

		public MExpressionStmt(MExpr expr) {
			this.expr = expr;
		}

		@Override
		public String toString() {
			return String.format("Expr{%s}", expr);
		}

		@Override
		public <T> T accept(MVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}
	public static class MDeclarationStmt extends MStatement {
		private final MToken type;
		private final MToken varName;
		private final MExpr expr;
		public MDeclarationStmt(MToken type, MToken varName, MExpr expr) {
			this.type = type;
			this.varName = varName;
			this.expr = expr;
		}

		@Override
		public String toString() {
			return String.format("Declaration{%s: %s, expr=%s}", varName.val, type.val, expr);
		}

		@Override
		public <T> T accept(MVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}
	public static class MToken {
		private final MTokenType tokenType;
		private final String val;
		public MToken(MTokenType tokenType, String val) {
			this.tokenType = tokenType;
			this.val = val;
		}
		public MToken(MTokenType tokenType) {
			this.tokenType = tokenType;
			this.val = null;
		}

		@Override
		public String toString() {
			return "MToken{" +
					"tokenType=" + tokenType +
					", val='" + val + '\'' +
					'}';
		}
	}
	public static class MScanner {

		private final String s;
		private int pos = 0;
		private final Map<Integer, MTokenType> specials = new HashMap<>();
		private List<MToken> cur = new LinkedList<>();

		public MScanner(String s) {
			this.s = s;
			specials.put((int) ';', MTokenType.SEMI);
			specials.put((int) ',', MTokenType.COMMA);
			specials.put((int) '(', MTokenType.LPAREN);
			specials.put((int) ')', MTokenType.RPAREN);
			specials.put((int) '{', MTokenType.LCURLY);
			specials.put((int) '}', MTokenType.RCURLY);
		}

		public MToken next() {
			if (!cur.isEmpty()) {
				return cur.remove(0);
			}
			return next1();
		}

		private MToken next1() {
			skipWS();
			int c = peekChar();
			if (isNum(c)) {
				return scanNumber();
			} else if (isAlphaNum(c)) {
				return scanIdentifierOrKeyword();
			} else if (specials.get(c) != null) {
				scanChar();
				return new MToken(specials.get(c));
			} else if (c == -1) {
				return new MToken(MTokenType.EOF);
			} else {
				return scanOp();
			}
		}

		private MToken scanOp() {
			String ops = "+-*/><=";
			int start = pos;
			while (true) {
				int c = peekChar();
				if (c == -1) break;
				if (ops.indexOf((char) c) == -1) break;
				scanChar();
			}
			if (start == pos) throw new AssertionError();
			return new MToken(MTokenType.OPERATOR, s.substring(start, pos));
		}

		private int peekChar() {
			if (pos == s.length()) return -1;
			return s.charAt(pos);
		}

		private int scanChar() {
			if (pos == s.length()) return -1;
			return s.charAt(pos++);
		}

		private MToken scanNumber() {
			int start = pos;
			while (isNum(peekChar())) scanChar();
			return new MToken(MTokenType.NUMBER, s.substring(start, pos));
		}

		private void skipWS() {
			while (true) {
				switch (peekChar()) {
					case ' ':
					case '\t':
					case '\r':
					case '\n':
						scanChar();
						break;
					default:
						return;
				}
			}
		}

		private MToken scanIdentifierOrKeyword() {
			int start = pos;
			while (isAlphaNum(peekChar())) scanChar();
			String val = s.substring(start, pos);
			if (keywords.contains(val)) {
				return new MToken(MTokenType.KEYWORD, val);
			} else {
				return new MToken(MTokenType.IDENTIFIER, val);
			}
		}

		private boolean isNum(int c) {
			return '0' <= c && c <= '9';
		}

		private boolean isAlphaNum(int c) {
			return isNum(c) || 'a' <= c && c <= 'z' || 'A' <= c && c <= 'Z';
		}

		public MToken peek() {
			if (cur.isEmpty()) {
				cur.add(next1());
			}
			return cur.get(0);
		}

		public MToken peek(int lookAhead) {
			if (lookAhead > 1) {
				throw new AssertionError();
			}
			while (cur.size() <= lookAhead) {
				cur.add(next1());
			}
			return cur.get(lookAhead);
		}
	}

	public static class MVisitor<T> {
		public T visit(MLiteralExpr literalExpr) {
			return null;
		}
		public T visit(MBinaryExpr binaryExpr) {
			return null;
		}
		public T visit(MVariableExpr mVariableExpr) {
			return null;
		}
		public T visit(MMethodCallExpr mMethodCallExpr) {
			return null;
		}
		public T visit(MIfStatement mIfStatement) {
			return null;
		}
		public T visit(MWhileStatement mWhileStatement) {
			return null;
		}
		public T visit(MExpressionStmt mExpressionStmt) {
			return null;
		}
		public T visit(MDeclarationStmt mDeclarationStmt) {
			return null;
		}
		public T visit(Prog prog) {
			return null;
		}
		public T visit(MFunction mFunction) {
			return null;
		}
		public T visit(MSingleVarDecl mSingleVarDecl) {
			return null;
		}
		public T visit(MReturnStatement mReturnStatement) {
			return null;
		}
	}

	public interface MExpr extends MNode {
	}
	private static class MMethodCallExpr implements MExpr {

		private final MToken name;
		private final List<MExpr> args;

		public MMethodCallExpr(MToken name, List<MExpr> args) {
			this.name = name;
			this.args = args;
		}

		@Override
		public <T> T accept(MVisitor<T> visitor) {
			return visitor.visit(this);
		}

		@Override
		public String toString() {
			return String.format("%s(%s)", name.val, args);
		}
	}
	private static class MVariableExpr implements MExpr {
		private final MToken token;

		public MVariableExpr(MToken token) {
			this.token = token;
		}

		@Override
		public <T> T accept(MVisitor<T> visitor) {
			return visitor.visit(this);
		}

		@Override
		public String toString() {
			return String.format("<%s>", token.val);
		}
	}
	private static class MLiteralExpr implements MExpr {
		private final MToken token;
		public MLiteralExpr(MToken token) {
			this.token = token;
		}
		@Override
		public String toString() {
			return String.format("<%s>", token.val);
		}

		@Override
		public <T> T accept(MVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}
	private static class MBinaryExpr implements MExpr {
		private final MExpr left;
		private final MToken op;
		private final MExpr right;
		public MBinaryExpr(MExpr left, MToken op, MExpr right) {
			this.left = left;
			this.op = op;
			this.right = right;
		}
		@Override
		public String toString() {
			return String.format("{%s %s %s}", left, op.val, right);
		}

		@Override
		public <T> T accept(MVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}
}
