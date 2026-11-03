package life.ctrl.model;

import java.util.ArrayList;

/** Only durable values: no graphics, input state, or transient UI. */
public final class GameState {
  public String name = "Саша", map = "room";
  public float x = 240, y = 176;
  public int direction = 0, outfit = 0;
  public double elapsed = 0;
  public boolean inspectedPc,
      metMira,
      metArkady,
      metLev,
      askedLetter,
      hasLetter,
      deliveredLetter,
      wateredPlant,
      unpacked,
      signedMailbox,
      readBoard;
  public ArrayList<String> visited = new ArrayList<>();

  public GameState() {
    visited.add("room");
  }

  public int minutes() {
    return 18 * 60 + 40 + (int) (elapsed / 2);
  }

  public String clock() {
    int m = minutes() % 1440;
    return "%02d:%02d".formatted(m / 60, m % 60);
  }

  public String objective() {
    if (deliveredLetter) return "Вернись домой. В этом городе тебя уже знают.";
    if (hasLetter) return "Отнеси письмо Мире в подъезд.";
    if (askedLetter) return "Найди письмо под лавкой у магазина, справа.";
    if (!metMira) return "Осмотрись дома и познакомься с соседкой.";
    return "Поговори с Аркадием и прогуляйся по улице.";
  }
}
