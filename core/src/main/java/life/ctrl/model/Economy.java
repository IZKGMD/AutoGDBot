package life.ctrl.model;

public final class Economy {
  private Economy() {}

  public static final int WEEKLY_RENT = 3000;

  public static final class Entry {
    public int day, amount, balance;
    public String reason;

    public Entry() {}

    public Entry(GameState s, int amount, String reason) {
      day = s.day();
      this.amount = amount;
      balance = s.money;
      this.reason = reason;
    }
  }

  public static boolean spend(GameState s, int amount, String reason) {
    if (amount < 0) throw new IllegalArgumentException("Отрицательная цена");
    if (s.money < amount) return false;
    if (amount == 0) return true;
    s.money -= amount;
    record(s, -amount, reason);
    return true;
  }

  public static void earn(GameState s, int amount, String reason) {
    if (amount < 0 || amount > 1000000 || s.money > Integer.MAX_VALUE - amount)
      throw new IllegalArgumentException("Сумма");
    s.money += amount;
    record(s, amount, reason);
  }

  private static void record(GameState s, int amount, String reason) {
    s.transactions.add(new Entry(s, amount, reason));
    if (s.transactions.size() > 100) s.transactions.remove(0);
  }

  public static void rent(GameState s) {
    while (s.day() > s.rentThroughDay) {
      int paid = Math.min(WEEKLY_RENT, s.money);
      spend(s, paid, "Комната: неделя аренды");
      s.rentDebt += WEEKLY_RENT - paid;
      s.rentThroughDay += 7;
    }
  }

  public static String repay(GameState s) {
    int amount = Math.min(s.rentDebt, s.money);
    if (amount == 0) return "Сейчас нечего погашать.";
    spend(s, amount, "Погашение долга за комнату");
    s.rentDebt -= amount;
    return "Долг уменьшен на " + amount + " ₽. Осталось: " + s.rentDebt + " ₽.";
  }
}
