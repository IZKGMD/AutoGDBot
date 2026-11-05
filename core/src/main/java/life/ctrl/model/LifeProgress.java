package life.ctrl.model;

import java.util.List;

/**
 * The first chapter has a visible destination; career and relationship conclusions are
 * alternatives.
 */
public final class LifeProgress {
  private LifeProgress() {}

  public record Step(String title, String hint, boolean done) {}

  public static List<Step> steps(GameState s) {
    boolean met = s.relationships.values().stream().anyMatch(r -> r.met);
    boolean story = s.relationships.values().stream().anyMatch(r -> r.arcStep == 3);
    boolean career = Career.tier(s) == 2 && s.upgrades.containsAll(List.of("ssd", "chair"));
    var result =
        List.of(
            new Step(
                "Первый заработок",
                "ПК → Контракты. Прочитай ТЗ, проверь код и сдай заказ.",
                s.completedOrders > 0),
            new Step(
                "Позаботиться о себе",
                "Купи еду у стойки «Ложки» и съешь её через карманы [I].",
                s.ateFirstMeal),
            new Step(
                "Стать своим в районе",
                "[R] — контакты. Подойди к Мире, Асе или Нике и познакомься.",
                met),
            new Step(
                "Первый вечер вместе",
                "Пригласи девушку в сквер: это бесплатно. Пройди все три момента встречи.",
                s.firstDateFinished),
            new Step(
                "Свой работающий продукт",
                "ПК → Свой бот. Запусти объявления, напоминания и поддержку: по 300 ₽ за этап.",
                Career.projectStage(s) == 3),
            new Step(
                "Уверенно стоять на ногах",
                "Доживи до дня 8 без долга за комнату. Оставь 3440 ₽ на следующую аренду и еду.",
                s.day() >= 8 && s.rentDebt == 0 && s.money >= 3440),
            new Step(
                "Выбрать свою историю",
                "Заверши личную историю одной из девушек ИЛИ получи 8 опыта и установи SSD с"
                    + " креслом.",
                story || career));
    return s.chapterOneFinished
        ? result.stream().map(x -> new Step(x.title(), x.hint(), true)).toList()
        : result;
  }

  public static int count(GameState s) {
    return (int) steps(s).stream().filter(Step::done).count();
  }

  public static boolean ready(GameState s) {
    return steps(s).stream().allMatch(Step::done);
  }

  public static void record(GameState s) {
    if (ready(s)) s.chapterOneFinished = true;
  }

  public static Step next(GameState s) {
    return steps(s).stream().filter(x -> !x.done()).findFirst().orElse(null);
  }
}
