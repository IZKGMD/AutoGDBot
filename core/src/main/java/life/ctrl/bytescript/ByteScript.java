package life.ctrl.bytescript;

import java.util.*;

/** A bounded expression language, never evaluated by Java, a shell or an external service. */
public final class ByteScript {
  public enum Kind {
    SYNTAX,
    SEMANTIC
  }

  public static final class Error extends RuntimeException {
    public final Kind kind;
    public final int line, column;

    public Error(Kind kind, String message, int line, int column) {
      super(message);
      this.kind = kind;
      this.line = line;
      this.column = column;
    }
  }

  public record Program(String name, List<String> parameters, Expr expression) {
    public int operations(double... args) {
      run(args);
      Map<String, Double> values = new HashMap<>();
      for (int i = 0; i < args.length; i++) values.put(parameters.get(i), args[i]);
      return count(expression, values);
    }

    private static int count(Expr e, Map<String, Double> v) {
      if (e instanceof Binary b) return 1 + count(b.left, v) + count(b.right, v);
      if (e instanceof Select t)
        return 1 + count(t.condition, v) + count(t.condition.eval(v) != 0 ? t.yes : t.no, v);
      return 0;
    }

    public double run(double... args) {
      if (args.length != parameters.size()) throw new IllegalArgumentException("Число аргументов");
      Map<String, Double> values = new HashMap<>();
      for (int i = 0; i < args.length; i++) values.put(parameters.get(i), args[i]);
      double result = expression.eval(values);
      if (!Double.isFinite(result))
        throw new ArithmeticException("Деление на ноль или слишком большое число");
      return result;
    }
  }

  public sealed interface Expr permits Number, Variable, Binary, Select {
    double eval(Map<String, Double> values);
  }

  public record Number(double value) implements Expr {
    public double eval(Map<String, Double> v) {
      return value;
    }
  }

  public record Variable(String name) implements Expr {
    public double eval(Map<String, Double> v) {
      return v.get(name);
    }
  }

  public record Binary(String op, Expr left, Expr right) implements Expr {
    public double eval(Map<String, Double> v) {
      double a = left.eval(v), b = right.eval(v);
      return switch (op) {
        case "+" -> a + b;
        case "-" -> a - b;
        case "*" -> a * b;
        case "/" -> a / b;
        case ">=" -> a >= b ? 1 : 0;
        case "<=" -> a <= b ? 1 : 0;
        case ">" -> a > b ? 1 : 0;
        case "<" -> a < b ? 1 : 0;
        case "==" -> a == b ? 1 : 0;
        default -> throw new IllegalStateException(op);
      };
    }
  }

  public record Select(Expr condition, Expr yes, Expr no) implements Expr {
    public double eval(Map<String, Double> v) {
      return condition.eval(v) != 0 ? yes.eval(v) : no.eval(v);
    }
  }

  private record Token(String text, int line, int column) {}

  private final List<Token> tokens = new ArrayList<>();
  private final Set<String> names = new HashSet<>();
  private int at;

  private ByteScript(String source) {
    if (source.length() > 4096) throw new Error(Kind.SYNTAX, "Программа слишком длинная", 1, 1);
    int line = 1, col = 1;
    for (int i = 0; i < source.length(); ) {
      char c = source.charAt(i);
      if (Character.isWhitespace(c)) {
        if (c == '\n') {
          line++;
          col = 1;
        } else col++;
        i++;
        continue;
      }
      int begin = i, startCol = col;
      if (Character.isLetter(c) || c == '_') {
        while (i < source.length()
            && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_')) i++;
      } else if (Character.isDigit(c)) {
        while (i < source.length()
            && (Character.isDigit(source.charAt(i)) || source.charAt(i) == '.')) i++;
      } else if ("(){};,?:+-*/<>=".indexOf(c) >= 0) {
        i++;
        if (i < source.length() && source.charAt(i) == '=' && "<>= ".indexOf(c) >= 0) i++;
      } else throw new Error(Kind.SYNTAX, "Неожиданный символ «" + c + "»", line, col);
      String text = source.substring(begin, i);
      tokens.add(new Token(text, line, startCol));
      col += text.length();
    }
    if (tokens.size() > 512) throw new Error(Kind.SYNTAX, "Слишком много операций", line, col);
    tokens.add(new Token("<end>", line, col));
  }

  public static Program compile(String source) {
    return new ByteScript(source).parse();
  }

  private Program parse() {
    expect("fn");
    String name = identifier();
    expect("(");
    List<String> params = new ArrayList<>();
    if (!peek(")")) {
      do {
        String p = identifier();
        if (!names.add(p)) fail(Kind.SEMANTIC, "Повтор аргумента: " + p);
        params.add(p);
      } while (take(","));
    }
    expect(")");
    expect("{");
    expect("return");
    Expr expr = select(0);
    expect(";");
    expect("}");
    expect("<end>");
    return new Program(name, List.copyOf(params), expr);
  }

  private Expr select(int depth) {
    if (depth > 48) fail(Kind.SYNTAX, "Слишком глубокое выражение");
    Expr c = compare(depth + 1);
    if (take("?")) {
      Expr y = select(depth + 1);
      expect(":");
      return new Select(c, y, select(depth + 1));
    }
    return c;
  }

  private Expr compare(int d) {
    Expr a = add(d);
    while (Set.of(">=", "<=", ">", "<", "==").contains(current().text)) {
      String op = next().text;
      a = new Binary(op, a, add(d));
    }
    return a;
  }

  private Expr add(int d) {
    Expr a = multiply(d);
    while (peek("+") || peek("-")) {
      String op = next().text;
      a = new Binary(op, a, multiply(d));
    }
    return a;
  }

  private Expr multiply(int d) {
    Expr a = atom(d);
    while (peek("*") || peek("/")) {
      String op = next().text;
      a = new Binary(op, a, atom(d));
    }
    return a;
  }

  private Expr atom(int d) {
    if (d > 48) fail(Kind.SYNTAX, "Слишком глубокое выражение");
    if (take("(")) {
      Expr e = select(d + 1);
      expect(")");
      return e;
    }
    if (take("-")) return new Binary("-", new Number(0), atom(d + 1));
    Token token = next();
    try {
      if (Character.isDigit(token.text.charAt(0)))
        return new Number(Double.parseDouble(token.text));
    } catch (NumberFormatException e) {
      fail(Kind.SYNTAX, "Неверное число");
    }
    if (!token.text.matches("[A-Za-z_][A-Za-z0-9_]*"))
      throw new Error(Kind.SYNTAX, "Ожидалось число или аргумент", token.line, token.column);
    if (!names.contains(token.text))
      throw new Error(
          Kind.SEMANTIC,
          "Неизвестное имя «" + token.text + "». Сверь его с аргументами функции.",
          token.line,
          token.column);
    return new Variable(token.text);
  }

  private String identifier() {
    Token t = next();
    if (!t.text.matches("[A-Za-z_][A-Za-z0-9_]*"))
      throw new Error(Kind.SYNTAX, "Ожидалось имя", t.line, t.column);
    return t.text;
  }

  private Token current() {
    return tokens.get(Math.min(at, tokens.size() - 1));
  }

  private Token next() {
    Token t = current();
    at++;
    return t;
  }

  private boolean peek(String s) {
    return current().text.equals(s);
  }

  private boolean take(String s) {
    if (peek(s)) {
      at++;
      return true;
    }
    return false;
  }

  private void expect(String s) {
    if (!take(s)) fail(Kind.SYNTAX, "Ожидалось «" + s + "», получено «" + current().text + "»");
  }

  private void fail(Kind k, String message) {
    Token t = current();
    throw new Error(k, message, t.line, t.column);
  }
}
