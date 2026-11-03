package life.ctrl.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.*;
import com.badlogic.gdx.utils.Disposable;
import life.ctrl.model.Settings;

public final class AudioScene implements Disposable {
  private Music music, rain;
  private Sound click, step, door, success;

  public AudioScene() {
    if (Boolean.getBoolean("ctrl.noAudio")) return;
    try {
      music = Gdx.audio.newMusic(Gdx.files.internal("audio/evening.wav"));
      rain = Gdx.audio.newMusic(Gdx.files.internal("audio/rain.wav"));
      click = Gdx.audio.newSound(Gdx.files.internal("audio/click.wav"));
      step = Gdx.audio.newSound(Gdx.files.internal("audio/step.wav"));
      door = Gdx.audio.newSound(Gdx.files.internal("audio/door.wav"));
      success = Gdx.audio.newSound(Gdx.files.internal("audio/success.wav"));
      music.setLooping(true);
      rain.setLooping(true);
      music.play();
      rain.play();
    } catch (RuntimeException e) {
      Gdx.app.log("Audio", "Аудиоустройство недоступно; игра продолжит работу.");
      dispose();
    }
  }

  public boolean ready() {
    return music != null
        && rain != null
        && click != null
        && step != null
        && door != null
        && success != null;
  }

  public void update(Settings s, boolean outside, boolean focused) {
    if (music != null) music.setVolume(focused ? s.music * .65f : 0);
    if (rain != null) rain.setVolume(focused ? s.sfx * (outside ? .9f : .18f) : 0);
  }

  public void door(Settings s) {
    if (door != null) door.play(s.sfx * .7f);
  }

  public void success(Settings s) {
    if (success != null) success.play(s.sfx * .6f);
  }

  public void click(Settings s) {
    if (click != null) click.play(s.sfx * .5f);
  }

  public void step(Settings s) {
    if (step != null) step.play(s.sfx * .3f);
  }

  public void dispose() {
    if (door != null) {
      door.dispose();
      door = null;
    }
    if (success != null) {
      success.dispose();
      success = null;
    }
    if (music != null) {
      music.dispose();
      music = null;
    }
    if (rain != null) {
      rain.dispose();
      rain = null;
    }
    if (click != null) {
      click.dispose();
      click = null;
    }
    if (step != null) {
      step.dispose();
      step = null;
    }
  }
}
