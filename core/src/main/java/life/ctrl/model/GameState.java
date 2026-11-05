package life.ctrl.model;

import java.util.ArrayList;
import java.util.HashMap;

/** Only durable values: no graphics, input state, or transient UI. */
public final class GameState {
  public String name = "Саша", map = "room";
  public float x = 240, y = 190;
  public int direction = 0, outfit = 0;
  public int money = 10000, rentDebt = 0, rentThroughDay = 3;
  public double health = 85, hunger = 42, energy = 62, mood = 45, gameMinutes = 17 * 60 + 30;
  public boolean introSeen, ateFirstMeal, firstDateFinished, chapterOneFinished;
  public String trackedGirl = "mira",
      partnerId = "",
      plannedGirl = "",
      plannedVenue = "",
      activeJob = "",
      codeSource = "",
      activeLedger = "";
  public int plannedDay = -1, typedCount = 0, jobStage = 0, algorithm = 0, goalChoice = 0;
  public boolean programPreviewed;
  public int lastCourierMinute = -10000, lastFreeMealDay = -1;
  public ArrayList<String> paidJobs = new ArrayList<>();
  public ArrayList<Economy.Entry> transactions = new ArrayList<>();
  public HashMap<String, Integer> items = new HashMap<>();
  public HashMap<String, Relationship> relationships = new HashMap<>();
  public PinuxSession pinux = new PinuxSession();
  public DateStory.Session date = new DateStory.Session();
  public int codingXp, completedOrders, rushDeadline = -1, projectIncomeDay = -1;
  public boolean rushJob;
  public String workPace = "STEADY";
  public double workFatigue;
  public ArrayList<String> shipped = new ArrayList<>(),
      upgrades = new ArrayList<>(),
      customCases = new ArrayList<>();
  public ArrayList<Coding.Revision> revisions = new ArrayList<>();
  public HashMap<String, Integer> rushPromises = new HashMap<>();
  public String lastClientMessage = "", workNotice = "";
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
    return (int) gameMinutes;
  }

  public int day() {
    return 1 + minutes() / 1440;
  }

  public int count(String id) {
    return items.getOrDefault(id, 0);
  }

  public Relationship relation(String id) {
    return relationships.computeIfAbsent(id, k -> new Relationship());
  }

  public String clock() {
    int m = minutes() % 1440;
    return "%02d:%02d".formatted(m / 60, m % 60);
  }

  public String objective() {
    if (paidJobs.isEmpty())
      return inspectedPc
          ? "Закончи первый заказ в KeyCode. Оплата: 650 ₽."
          : "Подойди к столу и включи компьютер [E].";
    if (!ateFirstMeal) return "Зайди в «Ложку» и поешь у стойки [E].";
    if (!plannedGirl.isEmpty()) return "Свидание назначено. Доберись до места встречи.";
    if (!firstDateFinished) return "Познакомься с Мирой: разговор → приглашение на свидание.";
    if (energy < 30) return "Вернись в комнату и выспись [E у кровати].";
    return switch (goalChoice) {
      case 1 -> "Заработай кодингом: новые заказы на домашнем ПК.";
      case 2 -> "Проведи время с девушкой: контакты [R].";
      default -> "Твоя жизнь: заказы, знакомства, еда и новые дни.";
    };
  }
}
