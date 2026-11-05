package life.ctrl.save;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import life.ctrl.content.Content;
import life.ctrl.model.*;

/** Validated, atomic single-slot saves with a previous-good backup. */
public final class SaveStore {
  public static final int VERSION = 9;

  public static final class Envelope {
    public int version = VERSION;
    public GameState state;

    public Envelope() {}

    public Envelope(GameState s) {
      state = s;
    }
  }

  public record Loaded(GameState state, boolean fromBackup) {}

  private final Path directory;
  private final Content content;

  public SaveStore(Path directory, Content content) {
    this.directory = directory;
    this.content = content;
  }

  public static Path defaultDirectory() {
    String override = System.getProperty("ctrl.dataDir");
    if (override != null) return Path.of(override);
    String base =
        System.getProperty("os.name").startsWith("Windows")
            ? System.getenv("LOCALAPPDATA")
            : System.getenv("XDG_DATA_HOME");
    if (base == null || base.isBlank())
      base = Path.of(System.getProperty("user.home"), ".local", "share").toString();
    return Path.of(base, "CtrlLife");
  }

  private Json json() {
    Json j = new Json();
    j.setOutputType(JsonWriter.OutputType.json);
    j.setUsePrototypes(false);
    return j;
  }

  public boolean exists() {
    return Files.isRegularFile(directory.resolve("save.json"))
        || Files.isRegularFile(directory.resolve("save.backup.json"));
  }

  private GameState read(Path file) throws IOException {
    if (Files.size(file) > 524288) throw new IOException("Файл сохранения слишком большой");
    try {
      Envelope e = json().fromJson(Envelope.class, Files.readString(file, StandardCharsets.UTF_8));
      if (e == null || (e.version < 1 || e.version > VERSION) || e.state == null)
        throw new IllegalArgumentException("Версия сохранения не поддерживается");
      if (e.version == 1) {
        e.state.gameMinutes = 17 * 60 + 30 + e.state.elapsed / 2;
        var m = content.map(e.state.map);
        // The cutaway room and furniture changed. Old positions get a safe entrance.
        if (e.state.map.equals("room")) {
          e.state.x = m.spawnX;
          e.state.y = m.spawnY;
        }
        try {
          new World(content, e.state);
        } catch (IllegalArgumentException ex) {
          e.state.x = m.spawnX;
          e.state.y = m.spawnY;
        }
      }
      if (e.version < 3) {
        e.state.completedOrders = e.state.paidJobs.size();
        e.state.codingXp = e.state.completedOrders;
        for (var ledger : e.state.paidJobs) {
          String id = ledger.substring(ledger.indexOf(':') + 1);
          if (!e.state.shipped.contains(id)) e.state.shipped.add(id);
        }
      }
      if (e.version < 4) {
        var map = content.map(e.state.map);
        for (var prop : map.objects) {
          if (prop.id.startsWith("v4_")
              && prop.solid != null
              && World.intersects(e.state.x, e.state.y, prop.solid)) {
            e.state.x = map.spawnX;
            e.state.y = map.spawnY;
            break;
          }
        }
      }
      if (e.version < 6) {
        for (var r : e.state.relationships.values())
          if (r != null && r.met && r.memories != null && r.memories.isEmpty() && r.memory != null)
            r.memories.add(new Relationship.Memory(e.state.day(), "До обновления", r.memory));
      }
      if (e.version < 8) e.state.customCases.clear(); // Old probes were not guaranteed to pass.
      LifeProgress.record(e.state);
      new World(content, e.state);
      return e.state;
    } catch (RuntimeException ex) {
      throw new IOException("Повреждённое или несовместимое сохранение", ex);
    }
  }

  public Loaded load() throws IOException {
    try {
      return new Loaded(read(directory.resolve("save.json")), false);
    } catch (IOException main) {
      try {
        return new Loaded(read(directory.resolve("save.backup.json")), true);
      } catch (IOException backup) {
        throw new IOException("Не удалось прочитать сохранение и резервную копию", main);
      }
    }
  }

  /** Preserve the previous story separately from the rolling autosave backup. */
  public Path archiveCurrent() throws IOException {
    if (!exists()) return null;
    Path archive = directory.resolve("history").resolve(java.util.UUID.randomUUID().toString());
    Files.createDirectories(archive);
    for (String name : new String[] {"save.json", "save.backup.json"}) {
      Path source = directory.resolve(name);
      if (Files.isRegularFile(source)) Files.copy(source, archive.resolve(name));
    }
    return archive;
  }

  public void save(GameState state) throws IOException {
    new World(content, state);
    LifeProgress.record(state);
    Files.createDirectories(directory);
    Path main = directory.resolve("save.json"),
        temp = directory.resolve("save.tmp.json"),
        backup = directory.resolve("save.backup.json");
    Files.writeString(temp, json().prettyPrint(new Envelope(state)), StandardCharsets.UTF_8);
    read(temp);
    if (Files.isRegularFile(main)) {
      boolean valid = true;
      try {
        read(main);
      } catch (IOException ignored) {
        valid = false;
      }
      if (valid) Files.copy(main, backup, StandardCopyOption.REPLACE_EXISTING);
    }
    replace(temp, main);
  }

  public static void replace(Path temp, Path file) throws IOException {
    try {
      Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException ex) {
      Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  public Path directory() {
    return directory;
  }
}
