package life.ctrl.model;

import life.ctrl.content.LifeContent;

/** Economy and needs have a single mutation point, including explicit time costs. */
public final class Life {
  private Life() {}

  public static void advance(GameState s, double minutes) {
    if (!Double.isFinite(minutes) || minutes < 0 || minutes > 1440)
      throw new IllegalArgumentException("Время");
    s.gameMinutes += minutes;
    s.hunger = Math.max(0, s.hunger - minutes * .065);
    s.energy = Math.max(0, s.energy - minutes * .045);
    if (s.hunger < 10) s.health = Math.max(15, s.health - minutes * .012);
    Economy.rent(s);
  }

  public static String buy(GameState s, LifeContent.Item item) {
    if (s.money < item.price)
      return "Не хватает денег. Подработка у доски во дворе доступна всегда.";
    Economy.spend(s, item.price, "Покупка: " + item.name);
    s.items.merge(item.id, 1, Integer::sum);
    return "Куплено: " + item.name + ". [I] — съесть или посмотреть подарки.";
  }

  public static String eat(GameState s, LifeContent.Item item) {
    if (!item.kind.equals("food") || s.count(item.id) < 1) return "Сначала купи еду у стойки.";
    consume(s, item.id);
    s.hunger = Math.min(100, s.hunger + item.hunger);
    s.energy = Math.min(100, s.energy + item.energy);
    s.health = Math.min(100, s.health + 3);
    s.ateFirstMeal = true;
    advance(s, 10);
    return "Ты поел(а). Сытость +" + item.hunger + ", энергия +" + item.energy + ".";
  }

  public static void consume(GameState s, String item) {
    int n = s.count(item);
    if (n < 1) throw new IllegalStateException("Нет предмета");
    if (n == 1) s.items.remove(item);
    else s.items.put(item, n - 1);
  }

  public static void sleep(GameState s) {
    int wait = (480 - s.minutes() % 1440 + 1440) % 1440;
    if (wait == 0) wait = 1440;
    advance(s, wait);
    s.energy = 96;
    s.health = Math.min(100, s.health + 12);
    s.mood = Math.min(100, s.mood + 8);
  }

  public static String courier(GameState s) {
    if (s.minutes() - s.lastCourierMinute < 120)
      return "Следующая доставка будет через "
          + (120 - (s.minutes() - s.lastCourierMinute))
          + " мин. Можно отдохнуть на лавке.";
    s.lastCourierMinute = s.minutes();
    advance(s, 35);
    Economy.earn(s, 240, "Доставка посылки");
    s.energy = Math.max(0, s.energy - 6);
    s.hunger = Math.max(0, s.hunger - 3);
    return "Ты доставил(а) посылку в соседний дом: +240 ₽, прошло 35 минут.";
  }

  public static String freeMeal(GameState s) {
    if (s.lastFreeMealDay == s.day())
      return "Сегодня ты уже получил(а) бесплатный суп. Завтра будет новая порция.";
    if (s.money >= 250) return "Бесплатный суп оставляют тем, у кого меньше 250 ₽.";
    s.lastFreeMealDay = s.day();
    s.hunger = Math.min(100, s.hunger + 45);
    s.ateFirstMeal = true;
    return "Горячий суп за счёт заведения. Здесь помогают в трудные дни.";
  }
}
