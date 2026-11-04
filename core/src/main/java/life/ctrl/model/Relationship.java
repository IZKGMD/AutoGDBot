package life.ctrl.model;

/** Durable relationship history. Affection cannot substitute for trust or consent. */
public final class Relationship {
  public boolean met;
  public int arcStep, lastArcDay = -1, lastActivityDay = -1, promiseDue = -1;
  public String promise = "", brokenPromise = "";
  public java.util.ArrayList<String> decisions = new java.util.ArrayList<>();
  public java.util.HashMap<String, Integer> activities = new java.util.HashMap<>();
  public java.util.ArrayList<Memory> memories = new java.util.ArrayList<>();

  public static final class Memory {
    public int day;
    public String title, text;

    public Memory() {}

    public Memory(int day, String title, String text) {
      this.day = day;
      this.title = title;
      this.text = text;
    }
  }

  public int affinity, trust, dates, storyChapter, sharedNights;
  public int lastTalkDay = -1,
      lastGiftDay = -1,
      lastDateDay = -1,
      lastNightDay = -1,
      committedDay = -1;
  public String lastTopic = "", memory = "Вы ещё не знакомы.";
}
