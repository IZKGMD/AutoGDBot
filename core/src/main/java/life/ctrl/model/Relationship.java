package life.ctrl.model;

/** Durable relationship history. Affection cannot substitute for trust or consent. */
public final class Relationship {
  public boolean met;
  public int affinity, trust, dates, storyChapter, sharedNights;
  public int lastTalkDay = -1,
      lastGiftDay = -1,
      lastDateDay = -1,
      lastNightDay = -1,
      committedDay = -1;
  public String lastTopic = "", memory = "Вы ещё не знакомы.";
}
