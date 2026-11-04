package life.ctrl.model;

import java.util.*;
import life.ctrl.bytescript.ByteScript;
import life.ctrl.content.LifeContent;

public final class Coding {
  private Coding() {}

  public record Report(boolean compiled, boolean passed, String message, List<String> cases) {}

  public static final class Revision {
    public String source, label;

    public Revision() {}

    public Revision(String source, String label) {
      this.source = source;
      this.label = label;
    }
  }

  public static String ledgerId(GameState s, String job) {
    return s.day() + ":" + job;
  }

  public static boolean paidToday(GameState s, String job) {
    return s.paidJobs.contains(ledgerId(s, job));
  }

  public static String accept(GameState s, LifeContent.Job j, int algorithm) {
    return accept(s, j, algorithm, false);
  }

  public static String accept(GameState s, LifeContent.Job j, int algorithm, boolean rush) {
    return accept(s, j, algorithm, rush, false);
  }

  /** Use tactile typing in the interactive IDE; direct model callers retain older semantics. */
  public static String accept(GameState s, LifeContent.Job j, int algorithm, boolean rush, boolean tactile) {
    if (!s.activeJob.isEmpty() && s.jobStage > 0 && s.jobStage < 5)
      return "Текущий проект сохранён. Продолжи его или отложи через историю версий.";
    String blocked = Career.blocked(s, j);
    if (!blocked.isEmpty()) return blocked;
    if (algorithm < 0 || algorithm >= j.initialSources.length) return "Выбери вариант из задания.";
    s.activeJob = j.id;
    s.activeLedger = ledgerId(s, j.id);
    s.algorithm = algorithm;
    s.codeSource = j.initialSources[algorithm];
    s.typedCount = tactile ? 0 : j.kind.equals("feature") ? 0 : s.codeSource.length();
    s.jobStage = 2;
    s.programPreviewed = false;
    s.rushJob = rush && !j.once;
    s.rushDeadline =
        s.rushJob
            ? s.rushPromises.computeIfAbsent(
                ledgerId(s, j.id), key -> s.minutes() + Career.duration(s, j) + 90)
            : -1;
    s.revisions.clear();
    s.customCases.clear();
    s.revisions.add(new Revision(s.codeSource, "Исходная версия · " + s.clock()));
    return "Проект открыт. Прочитай требование и проверь граничные случаи.";
  }

  public static void type(GameState s) {
    if (s.jobStage == 2) s.typedCount = Math.min(s.codeSource.length(), s.typedCount + 5);
  }

  public static void completeTyping(GameState s) {
    s.typedCount = s.codeSource.length();
  }

  public static Report test(GameState s, LifeContent.Job j) {
    if (!s.activeJob.equals(j.id))
      return new Report(false, false, "Этот заказ не открыт в редакторе.", List.of());
    if (s.typedCount < s.codeSource.length())
      return new Report(
          false, false, "Сначала допечатай исходник. Быстрые реакции не нужны.", List.of());
    try {
      var p = ByteScript.compile(s.codeSource);
      if (!p.name().equals(j.function))
        return new Report(true, false, "Клиент ожидает функцию " + j.function, List.of());
      List<String> results = new ArrayList<>();
      boolean passed = true;
      int maxOps = 0;
      for (var t : j.tests) {
        double actual = p.run(t.args);
        boolean ok = Math.abs(actual - t.expected) < .001;
        passed &= ok;
        maxOps = Math.max(maxOps, p.operations(t.args));
        results.add(
            Arrays.toString(t.args)
                + " → "
                + format(actual)
                + " / нужно "
                + format(t.expected)
                + (ok ? "  OK" : "  ОШИБКА"));
      }
      boolean fast = j.operationBudget == 0 || maxOps <= j.operationBudget;
      passed &= fast;
      s.jobStage = passed ? 3 : 2;
      return new Report(
          true,
          passed,
          !fast
              ? "Ответ верный, но операций "
                  + maxOps
                  + ". Бюджет: "
                  + j.operationBudget
                  + ". Открой профилировщик."
              : passed
                  ? "Все тесты пройдены. Проверь предпросмотр и отправляй."
                  : "Результат не совпал. Сравни вход, ответ и требование.",
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

  public static String format(double x) {
    return x == Math.rint(x)
        ? String.valueOf((long) x)
        : String.valueOf(Math.round(x * 100.0) / 100.0);
  }

  public static void repair(GameState s, LifeContent.Job j, int choice) {
    repair(s, j, choice, false);
  }

  public static void repair(GameState s, LifeContent.Job j, int choice, boolean tactile) {
    if (!s.activeJob.equals(j.id) || s.jobStage == 5 || choice < 0 || choice >= j.solutions.length)
      return;
    s.codeSource = j.solutions[choice];
    // The IDE requires a second typing phase after a patch is chosen.
    s.typedCount = tactile ? 0 : s.codeSource.length();
    s.jobStage = 2;
    s.programPreviewed = false;
  }

  public static String commit(GameState s) {
    if (s.jobStage < 2 || s.jobStage == 5) return "Сначала открой проект.";
    if (s.revisions.stream().anyMatch(r -> r.source.equals(s.codeSource)))
      return "Эта версия уже сохранена. Коммит фиксирует изменение, а не повторный клик.";
    s.revisions.add(new Revision(s.codeSource, "Коммит " + s.revisions.size() + " · " + s.clock()));
    if (s.revisions.size() > 8) s.revisions.remove(1);
    return "Коммит сохранён. Теперь можно экспериментировать и вернуться к нему.";
  }

  public static String restore(GameState s, int index) {
    if (index < 0 || index >= s.revisions.size() || s.jobStage == 5) return "Версия недоступна.";
    s.codeSource = s.revisions.get(index).source;
    s.typedCount = s.codeSource.length();
    s.jobStage = 2;
    s.programPreviewed = false;
    return "Версия восстановлена. Прогони тесты заново.";
  }

  public static String abandon(GameState s) {
    s.activeJob = "";
    s.activeLedger = "";
    s.codeSource = "";
    s.typedCount = 0;
    s.jobStage = 0;
    s.programPreviewed = false;
    s.revisions.clear();
    s.customCases.clear();
    s.rushJob = false;
    return "Заказ отложен без штрафа. Можно выбрать другой.";
  }

  public static String probe(GameState s, LifeContent.Job j, double[] inputs) {
    if (!s.activeJob.equals(j.id) || s.typedCount < s.codeSource.length())
      return "Сначала допечатай исходник.";
    if (inputs.length != j.seed.length
        || Arrays.stream(inputs).anyMatch(v -> !Double.isFinite(v) || Math.abs(v) > 10000))
      return "Ввод: конечные числа от −10000 до 10000.";
    try {
      var p = ByteScript.compile(s.codeSource);
      double actual = p.run(inputs), expected = ByteScript.compile(j.source).run(inputs);
      String key = Arrays.toString(inputs);
      boolean novel = Arrays.stream(j.tests).noneMatch(t -> Arrays.equals(t.args, inputs));
      if (novel && !s.customCases.contains(key) && s.customCases.size() < 20)
        s.customCases.add(key);
      return "Ответ "
          + format(actual)
          + " · нужно "
          + format(expected)
          + " · операций "
          + p.operations(inputs)
          + (Math.abs(actual - expected) < .001 ? " · OK" : " · ОШИБКА");
    } catch (RuntimeException e) {
      return "Запуск не удался: " + e.getMessage();
    }
  }

  public static double preview(GameState s, LifeContent.Job j, double input) {
    if (!test(s, j).passed) throw new IllegalStateException("Сначала исправь программу");
    double[] args = j.id.equals("total") ? new double[] {30, input} : j.seed.clone();
    args[args.length - 1] = input;
    s.programPreviewed = true;
    return ByteScript.compile(s.codeSource).run(args);
  }

  public static String deliver(GameState s, LifeContent.Job j) {
    if (!s.activeJob.equals(j.id) || !s.activeLedger.endsWith(":" + j.id))
      return "Сначала открой нужный заказ.";
    if (s.jobStage == 5
        || s.paidJobs.contains(s.activeLedger)
        || (j.once && s.shipped.contains(j.id))) return "Эта работа уже сдана.";
    if (!s.programPreviewed || !test(s, j).passed) return "Нужны пройденные тесты и предпросмотр.";
    if (!j.once && Career.paidCountToday(s) >= 3)
      return "Сегодня уже оплачены три заказа. Продолжи завтра; исходник сохранён.";
    if (s.money < j.cost)
      return "Для запуска нужно " + j.cost + " ₽. Код сохранён, можно вернуться после подработки.";
    int time = Career.duration(s, j), bonus = Career.bonus(s, j);
    boolean late = s.rushJob && s.minutes() + time > s.rushDeadline;
    Economy.spend(s, j.cost, "Свой бот: " + j.title);
    if (!j.once) {
      s.paidJobs.add(ledgerId(s, j.id));
      if (!s.activeLedger.equals(ledgerId(s, j.id))) s.paidJobs.add(s.activeLedger);
      Economy.earn(s, j.pay + bonus, "Заказ: " + j.title);
      s.completedOrders++;
    }
    if (!s.shipped.contains(j.id)) s.shipped.add(j.id);
    s.codingXp += j.xp;
    s.jobStage = 5;
    Life.advance(s, time);
    s.energy = Math.max(0, s.energy - (s.upgrades.contains("chair") ? 2 : 6));
    s.mood = Math.min(100, s.mood + 8);
    s.lastClientMessage = j.feedback;
    return (j.once
            ? "Этап запущен. Расход: " + j.cost + " ₽."
            : "Клиент принял работу: +"
                + (j.pay + bonus)
                + " ₽"
                + (bonus > 0 ? " (бонус " + bonus + " ₽)" : "")
                + ".")
        + " Прошло "
        + time
        + " мин. Опыт +"
        + j.xp
        + ".\n\n"
        + j.feedback
        + (late ? "\n\nСрочный бонус истёк; базовая оплата сохранена." : "");
  }
}
