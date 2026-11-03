package life.ctrl.model;

import java.util.*;
import life.ctrl.bytescript.ByteScript;
import life.ctrl.content.LifeContent;

public final class Coding {
  private Coding() {}

  public record Report(boolean compiled, boolean passed, String message, List<String> cases) {}

  public static String ledgerId(GameState s, String job) {
    return s.day() + ":" + job;
  }

  public static boolean paidToday(GameState s, String job) {
    return s.paidJobs.contains(ledgerId(s, job));
  }

  public static String accept(GameState s, LifeContent.Job job, int algorithm) {
    if (!s.activeJob.isEmpty() && s.jobStage > 0 && s.jobStage < 5 && !s.activeJob.equals(job.id))
      return "Сначала закончи текущий проект или явно закрой его.";
    if (paidToday(s, job.id)) return "Этот заказ уже оплачен сегодня. Завтра появится новый.";
    s.activeJob = job.id;
    s.activeLedger = ledgerId(s, job.id);
    s.algorithm = algorithm;
    s.codeSource = job.source;
    if (job.id.equals("total")) {
      s.codeSource =
          s.codeSource.replace(
              "price * count",
              algorithm == 0 ? "prce * count" : algorithm == 1 ? "price + count" : "price - count");
    } else if (job.id.equals("discount") && algorithm != 0)
      s.codeSource =
          algorithm == 1 ? s.codeSource.replace(">=", ">") : "fn discount(sum) { return sum; }";
    else if (job.id.equals("delivery") && algorithm != 0)
      s.codeSource =
          algorithm == 1
              ? s.codeSource.replace("<=", "<")
              : "fn delivery(distance) { return 100; }";
    s.typedCount = 0;
    s.jobStage = 2;
    s.programPreviewed = false;
    return "Проект открыт. Нажимай любые буквы: редактор напечатает исходник за тебя.";
  }

  public static void type(GameState s) {
    if (s.jobStage == 2) s.typedCount = Math.min(s.codeSource.length(), s.typedCount + 5);
  }

  public static void completeTyping(GameState s) {
    s.typedCount = s.codeSource.length();
  }

  public static Report test(GameState s, LifeContent.Job job) {
    if (!s.activeJob.equals(job.id))
      return new Report(false, false, "Этот заказ не открыт в редакторе.", List.of());
    if (s.typedCount < s.codeSource.length())
      return new Report(
          false, false, "Сначала допечатай исходник. Быстрые реакции не нужны.", List.of());
    try {
      var p = ByteScript.compile(s.codeSource);
      if (!p.name().equals(job.function))
        return new Report(true, false, "Клиент ожидает функцию " + job.function, List.of());
      List<String> results = new ArrayList<>();
      boolean passed = true;
      for (var t : job.tests) {
        double actual = p.run(t.args);
        boolean ok = Math.abs(actual - t.expected) < .001;
        passed &= ok;
        results.add(
            Arrays.toString(t.args)
                + " → "
                + Math.round(actual * 100) / 100.0
                + " / нужно "
                + t.expected
                + (ok ? "  OK" : "  ОШИБКА"));
      }
      if (passed) s.jobStage = 3;
      else s.jobStage = 2;
      return new Report(
          true,
          passed,
          passed
              ? "Все тесты пройдены. Открой работающий предпросмотр."
              : "Код компилируется, но результат неверен. Проверь правило клиента.",
          List.copyOf(results));
    } catch (ByteScript.Error e) {
      s.jobStage = 2;
      return new Report(
          false,
          false,
          (e.kind == ByteScript.Kind.SYNTAX ? "Синтаксис" : "Неизвестное имя")
              + ", строка "
              + e.line
              + ": "
              + e.getMessage(),
          List.of());
    } catch (ArithmeticException | IllegalArgumentException e) {
      s.jobStage = 2;
      return new Report(true, false, e.getMessage(), List.of());
    }
  }

  public static void repair(GameState s, LifeContent.Job job, int choice) {
    if (s.codeSource.contains("prce"))
      s.codeSource = s.codeSource.replace("prce", choice == 0 ? "price" : "count");
    else if (job.id.equals("total")) {
      s.codeSource =
          "fn total(price, count) {\n  return price " + (choice == 0 ? "*" : "+") + " count;\n}";
    } else if (job.id.equals("discount"))
      s.codeSource = choice == 0 ? job.source : job.source.replace(">=", ">");
    else s.codeSource = choice == 0 ? job.source : job.source.replace("<=", "<");
    s.typedCount = s.codeSource.length();
    s.jobStage = 2;
    s.programPreviewed = false;
  }

  public static double preview(GameState s, LifeContent.Job job, double input) {
    if (!test(s, job).passed) throw new IllegalStateException("Сначала исправь программу");
    s.programPreviewed = true;
    return ByteScript.compile(s.codeSource)
        .run(job.id.equals("total") ? new double[] {30, input} : new double[] {input});
  }

  public static String deliver(GameState s, LifeContent.Job job) {
    if (!s.activeJob.equals(job.id) || !s.activeLedger.endsWith(":" + job.id))
      return "Сначала открой нужный заказ.";
    if (s.activeLedger.isEmpty() || s.paidJobs.contains(s.activeLedger))
      return "Оплата уже получена.";
    if (!s.programPreviewed || !test(s, job).passed)
      return "Нужны пройденные тесты и предпросмотр.";
    s.paidJobs.add(s.activeLedger);
    Economy.earn(s, job.pay, "Заказ: " + job.title);
    s.jobStage = 5;
    Life.advance(s, job.minutes);
    s.mood = Math.min(100, s.mood + 8);
    return "Заказ принят клиентом: +"
        + job.pay
        + " ₽. Прошло "
        + job.minutes
        + " мин. Теперь можно поесть и выйти в город.";
  }
}
