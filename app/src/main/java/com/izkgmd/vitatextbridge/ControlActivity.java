package com.izkgmd.vitatextbridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import org.json.JSONObject;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.HttpURLConnection;
import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Companion dashboard for LinuxOnVita. Commands run over SSH on the base host;
 * packages and X11 programs always run inside Alpine via alpine-chroot -c. */
public final class ControlActivity extends Activity {
    private static final int BG=Color.rgb(8,13,25), PANEL=Color.rgb(18,28,46);
    private static final int BORDER=Color.rgb(46,67,95), WHITE=Color.rgb(232,241,252);
    private static final int MUTED=Color.rgb(146,170,199), CYAN=Color.rgb(105,208,243);
    private static final int ACTION=Color.rgb(112,183,255);
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService screenWorker=Executors.newSingleThreadExecutor();
    private volatile boolean screenWatching=false;
    private volatile long screenGeneration=0L;
    private ImageView remoteScreenPreview;
    private TextView remoteScreenStatus;
    private EditText screenViewerToken;
    private Bitmap remoteScreenBitmap;
    private String host,username,password; private int port;
    private LinearLayout content, root, tabs; private TextView status,output;
    private Button[] tabButtons;
    private String activeTab="Пакеты";
    private boolean busy;
    private ImageView preview; private Bitmap currentImage;
    private EditText packageSearch, videoUrl;
    private EditText bridgeUrl, bridgeToken;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        host=getIntent().getStringExtra("host");
        username=getIntent().getStringExtra("user");
        password=getIntent().getStringExtra("password");
        port=getIntent().getIntExtra("port",22);
        if(host==null||host.isEmpty()||username==null) { finish(); return; }
        if(password==null) password="";
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUi(); showTab("Мост");
    }

    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color, int border, int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius));
        if(border!=0)d.setStroke(dp(1),border); return d;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private LinearLayout column(){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);return p;}
    private void add(LinearLayout p,View v,int marginTop){
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(-1,-2);
        a.topMargin=dp(marginTop);p.addView(v,a);
    }
    private LinearLayout card(){
        LinearLayout p=column();p.setPadding(dp(15),dp(15),dp(15),dp(16));
        p.setBackground(bg(PANEL,BORDER,17));return p;
    }
    private Button button(String label,boolean major){
        Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setAllCaps(false);
        b.setTextColor(major?BG:WHITE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(bg(major?ACTION:Color.rgb(39,57,82),0,12));b.setMinHeight(dp(50));return b;
    }
    private EditText field(String placeholder,boolean many){
        EditText e=new EditText(this);e.setSingleLine(!many);e.setTextSize(14);e.setTextColor(WHITE);
        e.setHintTextColor(MUTED);e.setHint(placeholder);
        e.setInputType(many?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(bg(BG,BORDER,10));
        if(many){e.setMinLines(3);e.setGravity(Gravity.TOP|Gravity.START);}return e;
    }
    private void heading(LinearLayout p,String title,String subtitle){
        add(p,text(title,18,WHITE,true),0);add(p,text(subtitle,12,MUTED,false),6);
    }
    private void buildUi(){
        ScrollView scroller=new ScrollView(this);scroller.setFillViewport(true);scroller.setBackgroundColor(BG);
        root=column();root.setPadding(dp(14),dp(18),dp(14),dp(35));scroller.addView(root);
        TextView title=text("VITA / CONTROL",26,WHITE,true);title.setLetterSpacing(.075f);root.addView(title);
        add(root,text("Пульт LinuxOnVita  •  "+host+":"+port,12,MUTED,false),4);
        status=text("●  Готово к подключению",12,CYAN,true);add(root,status,13);

        HorizontalScrollView nav=new HorizontalScrollView(this);nav.setHorizontalScrollBarEnabled(false);
        tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);
        String[] names={"Мост","Турбо","Игры","Пакеты","Экран","YouTube","Эффекты","Система"};
        tabButtons=new Button[names.length];
        for(int i=0;i<names.length;i++){
            final String n=names[i];Button b=button(n,false);tabButtons[i]=b;
            b.setOnClickListener(v->showTab(n));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(49));
            lp.rightMargin=dp(6);tabs.addView(b,lp);
        }
        nav.addView(tabs);add(root,nav,15);
        content=column();add(root,content,14);
        LinearLayout log=card();add(root,log,18);
        add(log,text("ЖУРНАЛ ДЕЙСТВИЙ",15,WHITE,true),0);
        output=text("Подключение к "+host+" готово к проверке.\n",11,Color.rgb(183,218,244),false);
        output.setTypeface(Typeface.MONOSPACE);output.setTextIsSelectable(true);
        output.setPadding(dp(9),dp(10),dp(9),dp(10));output.setBackground(bg(BG,0,10));
        add(log,output,9);
        Button clear=button("Очистить журнал",false);
        clear.setOnClickListener(v->output.setText(""));add(log,clear,9);
        add(root,text("VitaControl 2.7 • VitaLink • Remote Screen",11,MUTED,false),15);
        setContentView(scroller);
    }
    private void showTab(String name){
        if(!"Экран".equals(name))stopScreenWatch();
        activeTab=name; content.removeAllViews();
        String[] names={"Мост","Турбо","Игры","Пакеты","Экран","YouTube","Эффекты","Система"};
        for(int i=0;i<names.length;i++) tabButtons[i].setBackground(bg(names[i].equals(name)?Color.rgb(39,88,115):Color.rgb(39,57,82),names[i].equals(name)?CYAN:0,12));
        switch(name){
            case "Мост":bridgeUi();break;
            case "Турбо":turboUi();break;
            case "Игры":gamesUi();break;
            case "Пакеты":packagesUi();break;
            case "Экран":screenUi();break;
            case "YouTube":youtubeUi();break;
            case "Эффекты":effectsUi();break;
            case "Система":systemUi();break;
        }
    }
    private static String quote(String value){return "'"+value.replace("'","'\\''")+"'";}
    private static String alpine(String command){return "alpine-chroot -c "+quote(command);}
    private static String x11(String command){return alpine("export DISPLAY=:0; "+command);}
    private void log(String s){
        String now=output.getText().toString();if(now.length()>22000)now=now.substring(now.length()-15000);
        output.setText(now+s+"\n");
    }
    private boolean trust(String question)throws InterruptedException{
        CountDownLatch gate=new CountDownLatch(1);AtomicBoolean ok=new AtomicBoolean(false);
        runOnUiThread(()->{
            if(isFinishing()||isDestroyed()){gate.countDown();return;}
            new AlertDialog.Builder(this).setTitle("SSH-ключ устройства")
                .setMessage("Подтверди отпечаток SSH-ключа Виты:\n\n"+question)
                .setPositiveButton("Доверять",(d,w)->{ok.set(true);gate.countDown();})
                .setNegativeButton("Отмена",(d,w)->gate.countDown())
                .setOnCancelListener(d->gate.countDown()).show();
        });
        return gate.await(90,TimeUnit.SECONDS)&&ok.get();
    }
    private SshClient client(){
        return new SshClient(host,port,username,password,
            new File(getFilesDir(),"known_hosts"),new File(getFilesDir(),"id_rsa"),this::trust);
    }
    private void task(String label,String command,int timeout,Consumer<String> done){
        if(busy){log("Занято: заверши предыдущее действие.");return;}
        busy=true;status.setText("●  Выполняется: "+label);
        worker.execute(()->{
            String result;
            try{result=client().execute(command,timeout);}
            catch(Exception ex){result="ОШИБКА: "+ex.getClass().getSimpleName()+": "+ex.getMessage();}
            String finalResult=result;
            runOnUiThread(()->{
                if(isDestroyed()||isFinishing())return;
                busy=false;status.setText("●  Готово");
                if(done!=null)done.accept(finalResult);
                else log("▶ "+label+"\n"+finalResult);
            });
        });
    }
    private void confirm(String title,String message,Runnable action){
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton("Выполнить",(d,w)->action.run())
            .setNegativeButton("Отмена",null).show();
    }
    private static boolean validPackage(String s){return s!=null&&s.matches("[A-Za-z0-9][A-Za-z0-9._+@-]{0,62}");}

    private static final String[] GAME_FILES={
        "neon_drift.py", "orbit_defender.py", "aurora_visuals.py", "panda3d_probe.py"
    };
    private static final String GAME_PATH="/root/vita-games/";
    private void gamesUi(){
        LinearLayout c=card();add(content,c,0);
        heading(c,"ИГРОВОЙ ЦЕНТР","Автоматически откроется Sakura на Vita с подробным журналом каждого этапа установки.");
        add(c,text("Установка: исходники → /root/vita-games, зависимости → apk в Alpine. На рабочем столе появятся ярлыки. Вита должна быть подключена к Wi-Fi.",12,MUTED,false),12);
        Button install=button("↓  УСТАНОВИТЬ ВСЕ ИГРЫ НА ВИТУ",true);
        add(c,install,14);
        install.setOnClickListener(v->confirm("Установить игры на PS Vita?",
            "Из APK будут переданы 4 Python-файла, затем Alpine установит python3, py3-pygame и python3-tkinter. Понадобится интернет и свободное место в alpine.img. Продолжить?",
            this::installGamePack));
        Button verify=button("Проверить установку",false);add(c,verify,9);
        verify.setOnClickListener(v->task("Проверка игр",alpine(
            "echo '=== Файлы ==='; ls -lh /root/vita-games/*.py 2>&1; "
            +"echo '=== Python ==='; python3 --version 2>&1; "
            +"echo '=== Pygame и Tkinter ==='; python3 -c 'import pygame; import tkinter; print(\"OK\")' 2>&1"),
            25000,null));
        gameCard("NEON DRIFT","Pygame / SDL2","Неоновая аркада, препятствия, очки, управление движением.","neon_drift.py");
        gameCard("ORBIT DEFENDER","Tkinter / X11","Защита орбиты: космическая аркада с выстрелами и противниками.","orbit_defender.py");
        gameCard("VITA AURORA","Pygame / SDL2","Анимированное сияние и звёзды — вместо xeyes и унылых часов.","aurora_visuals.py");
        gameCard("PANDA3D PROBE","Panda3D / 3D","Эксперимент: нужна отдельная сборка Panda3D под ARMv7 и OpenGL. Запуск не гарантирован.","panda3d_probe.py");
        add(content,text("После запуска смотри результат на экране Виты. Если окно не появилось, нажми «Журнал запуска» — там будут ошибки Python/X11.",12,MUTED,false),12);
    }
    private void gameCard(String title,String engine,String description,String file){
        LinearLayout c=card();add(content,c,11);
        add(c,text(title,16,CYAN,true),0);
        add(c,text(engine+" • "+description,12,MUTED,false),5);
        Button launch=button("▶  Запустить на PS Vita",true);add(c,launch,11);
        launch.setOnClickListener(v->launchGame(file));
        Button logs=button("Журнал запуска",false);add(c,logs,6);
        logs.setOnClickListener(v->task("Лог "+file,
            alpine("tail -n 55 "+LIVE_LOG+" 2>&1"),15000,null));
    }
    private byte[] assetGame(String filename)throws Exception{
        try(InputStream raw=getAssets().open("vita_games.zip");ZipInputStream zip=new ZipInputStream(raw)){
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                if(!entry.isDirectory()&&filename.equals(entry.getName())){
                    ByteArrayOutputStream result=new ByteArrayOutputStream();
                    byte[] buffer=new byte[4096];int n;
                    while((n=zip.read(buffer))!=-1){
                        if(result.size()+n>40000)throw new IllegalStateException("Слишком большой файл игры");
                        result.write(buffer,0,n);
                    }
                    return result.toByteArray();
                }
                zip.closeEntry();
            }
        }
        throw new IllegalStateException("В APK отсутствует "+filename);
    }
    private static String checksum(byte[] data)throws Exception{
        byte[] sha=MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder out=new StringBuilder(64);
        for(byte b:sha)out.append(String.format(Locale.US,"%02x",b&255));
        return out.toString();
    }
    private static boolean success(String result){return result.trim().endsWith("[exit 0]");}

    // The viewer lives in the Alpine container and follows real command output.
    private static final String LIVE_LOG="/root/vitacontrol/progress.log";
    private static final String LIVE_VIEWER="/root/vitacontrol/watch.sh";

    private String openProgressTerminal(SshClient ssh,String label,boolean clear) throws Exception {
        String sh="#!/bin/sh\n"
            +"printf '\\033[1;36mVITA CONTROL / LIVE PROGRESS\\033[0m\\n'\n"
            +"printf 'Real command output from Alpine Linux.\\n\\n'\n"
            +"exec tail -n +1 -f "+LIVE_LOG+"\n";
        String encoded=Base64.encodeToString(sh.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
        String prepare="set -eu; mkdir -p /root/vitacontrol; "
            +"printf '%s' '"+encoded+"' | base64 -d > "+LIVE_VIEWER+"; "
            +"chmod 755 "+LIVE_VIEWER+"; "
            +(clear?": > "+LIVE_LOG+"; ":"touch "+LIVE_LOG+"; ")
            +"printf '[%s] %s\\n' \"$(date '+%H:%M:%S')\" "+quote(label)+" >> "+LIVE_LOG;
        String setup=ssh.execute(alpine(prepare),20000);
        if(!success(setup))throw new IllegalStateException("Не удалось создать журнал: "+setup);
        String launch="export DISPLAY=:0 HOME=/root SHELL=/bin/sh; "
            +"if ! command -v sakura >/dev/null 2>&1; then echo 'NO_SAKURA'; "
            +"elif [ ! -S /tmp/.X11-unix/X0 ]; then echo 'NO_X11'; "
            +"else nohup sakura -t 'VitaControl: Progress' -e "+LIVE_VIEWER
            +" </dev/null >/root/vitacontrol/sakura-error.log 2>&1 & "
            +"echo 'SAKURA_STARTED'; fi";
        String result=ssh.execute(alpine(launch),20000);
        if(result.contains("NO_SAKURA"))return "Sakura не найдена; журнал сохранён: "+LIVE_LOG;
        if(result.contains("NO_X11"))return "Рабочий стол IceWM выключен; журнал сохранён: "+LIVE_LOG;
        if(!success(result))return "Открытие Sakura не подтверждено: "+result;
        return "Открыта Sakura на Вите. Живой журнал: "+LIVE_LOG;
    }

    private String journalCommand(SshClient ssh,String description,String cmd,int timeout)throws Exception {
        String wrapper="mkdir -p /root/vitacontrol; "
            +"printf '\\n[%s] ▶ %s\\n' \"$(date '+%H:%M:%S')\" "
            +quote(description)+" >> "+LIVE_LOG+"; "
            +"( "+cmd+" ) >> "+LIVE_LOG+" 2>&1; code=$?; "
            +"printf '[%s] %s [exit %s]\\n' \"$(date '+%H:%M:%S')\" "
            +quote(description)+" \"$code\" >> "+LIVE_LOG+"; "
            +"exit \"$code\"";
        return ssh.execute(alpine(wrapper),timeout);
    }

    private void taskWithTerminal(String title,String cmd,int timeout){
        if(busy){log("Сначала дождись завершения другой операции.");return;}
        busy=true;status.setText("●  "+title);
        worker.execute(()->{
            boolean ok=false;String message;
            try{
                SshClient ssh=client();
                progress(openProgressTerminal(ssh,title,true));
                String result=journalCommand(ssh,title,cmd,timeout);
                ok=success(result);
                String tail=ssh.execute(alpine("tail -n 25 "+LIVE_LOG),15000);
                message=(ok?"ГОТОВО: ":"ОШИБКА: ")+title+"\n"+tail;
            }catch(Exception e){message="Ошибка: "+e.getMessage();}
            final boolean done=ok;final String report=message;
            runOnUiThread(()->{
                if(isDestroyed()||isFinishing())return;
                busy=false;status.setText(done?"●  Успешно":"●  Ошибка");log(report);
            });
        });
    }

    private void viewLog(){
        if(busy){log("Занято: дождись завершения операции.");return;}
        busy=true;
        worker.execute(()->{
            String response;
            try{response=openProgressTerminal(client(),"VitaControl log",false);}
            catch(Exception e){response="Ошибка Sakura: "+e.getMessage();}
            final String result=response;
            runOnUiThread(()->{
                if(isDestroyed()||isFinishing())return;
                busy=false;status.setText("●  Журнал");log(result);
            });
        });
    }

    private void progress(String message){runOnUiThread(()->{
        if(!isFinishing()&&!isDestroyed()){status.setText("●  "+message);log("• "+message);}
    });}
    private void installGamePack(){
        if(busy){log("Сначала дождись завершения другого действия.");return;}
        busy=true;status.setText("●  Установка игр...");
        worker.execute(()->{
            String stage="Подготовка";String failure=null;
            try{
                SshClient ssh=client();
                progress(openProgressTerminal(ssh,"VitaGameLab / Установка игр",true));
                progress("Подключаемся к Linux и копируем встроенные игры");
                for(String name:GAME_FILES){
                    stage="Копирование "+name;
                    byte[] data=assetGame(name);
                    String encoded=Base64.encodeToString(data,Base64.NO_WRAP);
                    String digest=checksum(data);
                    String dest=GAME_PATH+name;
                    String script="set -eu; mkdir -p /root/vita-games; "
                        +"printf '%s' '"+encoded+"' | base64 -d > "+dest+".partial; "
                        +"test \"$(sha256sum "+dest+".partial | cut -d' ' -f1)\" = '"+digest+"'; "
                        +"mv "+dest+".partial "+dest+"; echo 'OK: "+name+"'";
                    String answer=journalCommand(ssh,"Передача "+name+" / "+data.length+
                        " байт / SHA256 "+digest.substring(0,12),script,45000);
                    if(!success(answer))throw new IllegalStateException(name+": "+answer);
                    progress("Передано: "+name);
                }
                stage="Обновление репозиториев";
                progress("Обновляем каталог пакетов Alpine");
                String update=journalCommand(ssh,"apk update — обновление списков пакетов","apk update",120000);
                if(!success(update))throw new IllegalStateException("apk update:\n"+update);
                stage="Установка Python / Pygame / Tkinter";
                progress("Устанавливаем Python + игровые движки");
                String deps=journalCommand(ssh,"apk add python3 py3-pygame python3-tkinter","apk add python3 py3-pygame python3-tkinter",240000);
                if(!success(deps))throw new IllegalStateException("apk add:\n"+deps);
                stage="Создание ярлыков";
                for(String name:GAME_FILES){
                    String title=name.replace(".py","").replace("_"," ").toUpperCase(Locale.ROOT);
                    String desktop="[Desktop Entry]\nType=Application\nName="+title
                        +"\nExec=/usr/bin/env DISPLAY=:0 /usr/bin/python3 "+GAME_PATH+name
                        +"\nTerminal=false\nCategories=Game;\n";
                    String b64=Base64.encodeToString(desktop.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
                    String dest="/root/Desktop/"+name.replace(".py",".desktop");
                    String cmd="mkdir -p /root/Desktop; printf '%s' '"+b64+"' | base64 -d > "
                        +dest+"; chmod 755 "+dest;
                    String result=journalCommand(ssh,"Создание ярлыка "+name,cmd,20000);
                    if(!success(result))throw new IllegalStateException(result);
                }
                String finished=journalCommand(ssh,"Установка завершена",
                    "echo '4 файла и ярлыки доступны на PS Vita.'",15000);
                if(!success(finished))throw new IllegalStateException(finished);
                progress("Готово: 4 проекта установлены, ярлыки добавлены");
            }catch(Exception e){failure="Этап «"+stage+"»: "+e.getMessage();}
            final String result=failure;
            runOnUiThread(()->{
                if(isDestroyed()||isFinishing())return;
                busy=false;status.setText(result==null?"●  Игры установлены":"●  Ошибка установки");
                if(result!=null)log("ОШИБКА УСТАНОВКИ:\n"+result+
                    "\nФайлы, уже переданные на Виту, останутся. Проверь Wi-Fi, место на диске и репозиторий community.");
                else log("Установка завершена. Открой раздел «Игры» и нажми «Запустить на PS Vita».");
            });
        });
    }
    private void launchGame(String filename){
        boolean supported=false;
        for(String name:GAME_FILES)if(name.equals(filename))supported=true;
        if(!supported){log("Неизвестный проект");return;}
        String probe="panda3d_probe.py".equals(filename)
            ?"python3 -c 'import panda3d' 2>/dev/null || { echo 'Panda3D не установлен или недоступен на ARMv7'; exit 127; }; "
            :"orbit_defender.py".equals(filename)
            ?"python3 -c 'import tkinter' 2>/dev/null || { echo 'Установи python3-tkinter'; exit 127; }; "
            :"python3 -c 'import pygame' 2>/dev/null || { echo 'Установи py3-pygame'; exit 127; }; ";
        String file=GAME_PATH+filename;
        String script="export DISPLAY=:0 SDL_VIDEODRIVER=x11 SDL_AUDIODRIVER=dummy; "
            +"test -f "+file+" || { echo 'Сначала установи игры из APK'; exit 127; }; "
            +probe+"nohup python3 "+file+" </dev/null >>"+LIVE_LOG+" 2>&1 & "
            +"echo 'Запущен: "+filename+" (PID '$!')'; sleep 1; "
            +"if ! kill -0 $! 2>/dev/null; then echo 'Процесс завершился; открой журнал запуска'; "
            +"tail -n 12 "+LIVE_LOG+"; fi";
        taskWithTerminal("Запуск "+filename,"export DISPLAY=:0 SDL_VIDEODRIVER=x11 SDL_AUDIODRIVER=dummy; "+script,25000);
    }

    private void packagesUi(){
        LinearLayout c=card();add(content,c,0);
        heading(c,"МАГАЗИН ПАКЕТОВ","При установке Sakura на Вите показывает реальный вывод apk в процессе выполнения.");
        packageSearch=field("Например: scrot, mpv, cmatrix",false);add(c,packageSearch,14);
        Button find=button("Найти пакет",false);
        find.setOnClickListener(v->{
            String query=packageSearch.getText().toString().trim();
            if(!validPackage(query)){packageSearch.setError("Только имя пакета (латиница, цифры, ._-+)");return;}
            task("Поиск "+query,alpine("apk search -v "+quote(query)+" | head -50"),45000,null);
        });add(c,find,9);
        Button installCustom=button("Установить введённый пакет",true);
        installCustom.setOnClickListener(v->install(packageSearch.getText().toString().trim(),false));add(c,installCustom,9);
        Button update=button("Обновить каталог пакетов",false);
        update.setOnClickListener(v->taskWithTerminal("apk update","apk update",120000));add(c,update,9);
        Button journal=button("Открыть Sakura с журналом",false);add(c,journal,9);
        journal.setOnClickListener(v->viewLog());
        add(content,text("ПОПУЛЯРНЫЕ ПАКЕТЫ",15,WHITE,true),17);
        item("YouTube Lite","mpv + yt-dlp","Видео через mpv; слабое железо, 144–240p, может тормозить.",true);
        item("Удалённый экран","scrot + xdotool","Снимки экрана и нажатия с телефона.",false);
        item("Анимация Matrix","cmatrix","Падающие символы в Sakura.",false);
        item("Анимированные глаза","xeyes","Лёгкий X11-эффект.",false);
        item("Неоновые часы","xclock","X11-виджет на рабочем столе.",false);
        item("Монитор ресурсов","btop","Проверка процессов, памяти и CPU.",false);
        item("Редактор текста","mousepad","Графический редактор.",false);
        item("Браузер","badwolf","Лёгкий браузер, обычно уже установлен.",false);
        item("Картинки","feh","Просмотр изображений и обоев.",false);
    }
    private void item(String title,String pkg,String description,boolean bundle){
        LinearLayout c=card();add(content,c,9);
        add(c,text(title,15,WHITE,true),0);
        add(c,text(pkg+" • "+description,12,MUTED,false),5);
        Button b=button("Установить",false);
        b.setOnClickListener(v->install(pkg,bundle));add(c,b,9);
    }
    private void install(String packageNames,boolean bundle){
        String[] packages=packageNames.trim().split("\\s+");
        if(packages.length==0||packages.length>8){log("Некорректный список пакетов.");return;}
        for(String p:packages)if(!validPackage(p)){log("Недопустимое имя пакета: "+p);return;}
        String names=String.join(" ",packages);
        String warning=bundle?"Установка mpv/yt-dlp может занять много памяти и места на карте памяти.\n\n":"";
        confirm("Установить "+names,warning+"Пакеты будут установлены в alpine.img. Продолжить?",()->
            taskWithTerminal("Установка "+names,"apk add "+names,240000));
    }
    private void screenUi(){
        remoteScreenUi();
        LinearLayout c=card();add(content,c,14);
        heading(c,"ЭКРАН PS VITA","Снимок графической сессии 960×544 и управление касаниями через xdotool.");
        Button shot=button("Показать экран Виты",true);add(c,shot,12);
        shot.setOnClickListener(v->capture());
        preview=new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackground(bg(BG,BORDER,10));
        preview.setAdjustViewBounds(true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(240));lp.topMargin=dp(12);c.addView(preview,lp);
        preview.setOnTouchListener((v,event)->{
            if(event.getAction()!=MotionEvent.ACTION_UP||currentImage==null)return true;
            float scale=Math.min(v.getWidth()/(float)currentImage.getWidth(),v.getHeight()/(float)currentImage.getHeight());
            float xoff=(v.getWidth()-currentImage.getWidth()*scale)/2f;
            float yoff=(v.getHeight()-currentImage.getHeight()*scale)/2f;
            float px=(event.getX()-xoff)/scale,py=(event.getY()-yoff)/scale;
            if(px<0||py<0||px>=currentImage.getWidth()||py>=currentImage.getHeight())return true;
            int x=Math.round(px),y=Math.round(py);
            task("Тап "+x+","+y,x11("xdotool mousemove "+x+" "+y+" click 1"),20000,s->{
                if(s.contains("[exit 0]")) capture(); else log(s);
            });
            return true;
        });
        add(c,text("Нажимай на снимок, чтобы кликать на Vita. Это последовательные снимки, не видеотрансляция.",12,MUTED,false),9);
        Button setup=button("Установить scrot + xdotool",false);add(c,setup,10);
        setup.setOnClickListener(v->install("scrot xdotool",false));
        LinearLayout controls=card();add(content,controls,14);
        heading(controls,"УДАЛЁННЫЕ КЛАВИШИ","Для IceWM, Sakura и графических приложений.");
        rowButtons(controls,new String[]{"Esc","Enter","Tab"},new String[]{"Escape","Return","Tab"});
        rowButtons(controls,new String[]{"↑","↓","←","→"},new String[]{"Up","Down","Left","Right"});
        rowButtons(controls,new String[]{"ЛКМ","ПКМ","Alt+Tab"},new String[]{"click 1","click 3","alt+Tab"});
        rowButtons(controls,new String[]{"Прокрутить ↑","Прокрутить ↓"},new String[]{"click 4","click 5"});
        Button diagn=button("Проверить X11 и утилиты",false);add(controls,diagn,9);
        diagn.setOnClickListener(v->task("X11",x11("printf 'DISPLAY=%s\\n' \"$DISPLAY\"; command -v xdotool; command -v scrot; xdotool getdisplaygeometry"),20000,null));
    }
    private void remoteScreenUi(){
        LinearLayout c=card();add(content,c,0);
        heading(c,"VITALINK / НАБЛЮДЕНИЕ","Обновляющийся экран PS Vita через HTTPS. SSH и локальная сеть для просмотра не нужны.");
        add(c,text("Сначала один раз разреши захват на самой Вите. Изображение передаётся только пока открыт просмотр и не публикуется в GitHub.",12,MUTED,false),11);
        Button prepare=button("1. Включить захват экрана на Вите",false);add(c,prepare,10);
        prepare.setOnClickListener(v->confirm("Включить удалённое наблюдение?",
            "Скачаем обновлённый агент VitaLink и установим scrot в Alpine. Кадры смогут передаваться только по запросу зрителя с отдельным ключом просмотра. Удалённые команды не включаются.",
            this::enableVitaScreen));
        screenViewerToken=field("screen_view_token с VPS",false);
        screenViewerToken.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        add(c,screenViewerToken,12);
        add(c,text("Это отдельный ключ только для экрана. Его можно получить на VPS; не используй agent_token и не отправляй ключ в GitHub.",12,MUTED,false),7);
        Button start=button("2. ▶ Смотреть через VPS",true);add(c,start,12);
        start.setOnClickListener(v->startScreenWatch());
        Button stop=button("■ Остановить просмотр",false);add(c,stop,7);
        stop.setOnClickListener(v->stopScreenWatch());
        remoteScreenPreview=new ImageView(this);
        remoteScreenPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        remoteScreenPreview.setBackground(bg(BG,BORDER,10));
        remoteScreenPreview.setAdjustViewBounds(true);
        LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(-1,dp(230));
        imageParams.topMargin=dp(12);c.addView(remoteScreenPreview,imageParams);
        remoteScreenStatus=text("Экран не запрошен",12,MUTED,false);
        add(c,remoteScreenStatus,9);
        add(c,text("Обновление примерно каждые 3–5 секунд. Выход из вкладки или приложения автоматически останавливает наблюдение.",12,MUTED,false),9);
        Button disable=button("Запретить удалённый захват на Вите",false);add(c,disable,10);
        disable.setOnClickListener(v->confirm("Полностью запретить захват?",
            "Удалённое наблюдение будет отключено в конфигурации агента VitaLink на PS Vita.",
            this::disableVitaScreen));
    }
    private void enableVitaScreen(){
        // Immutable source revision: the agent's capture action is opt-in only.
        String source="https://raw.githubusercontent.com/IZKGMD/AutoGDBot/"
            +"a83c0277581ddade87d6c6414e2fb59d9adf1c5f/vitalink/agent.py";
        String install="set -eu; umask 077; "
            +"test -f /root/vitalink/agent.json || { echo 'Сначала подключи VitaLink во вкладке Мост'; exit 2; }; "
            +"apk add --no-cache scrot python3 ca-certificates; "
            +"python3 -c "+quote("import urllib.request; urllib.request.urlretrieve('"+source+"','/root/vitalink/agent.py.new')")+"; "
            +"python3 -m py_compile /root/vitalink/agent.py.new; "
            +"cp /root/vitalink/agent.py /root/vitalink/agent.py.before-screen; "
            +"mv /root/vitalink/agent.py.new /root/vitalink/agent.py; "
            +"python3 -c "+quote("import json,os; p='/root/vitalink/agent.json'; c=json.load(open(p)); c['allow_screen']=True; open(p,'w').write(json.dumps(c,indent=2)+'\\n'); os.chmod(p,0o600)")+"; "
            +"echo 'Захват разрешён. Перезапускаю агент VitaLink.'";
        String command=alpine(install)
            +" && { pkill -f '^python3 /root/vitalink/agent.py$' 2>/dev/null || true; "
            +"pkill -f '^/usr/bin/python3 /root/vitalink/agent.py$' 2>/dev/null || true; "
            +alpine("nohup /usr/bin/python3 /root/vitalink/agent.py </dev/null >>/root/vitalink/agent.log 2>&1 & echo 'VitaLink обновлён; захват кадров разрешён' ")+"; }";
        task("VitaLink / включение кадров",command,240000,result->log(result));
    }
    private void disableVitaScreen(){
        stopScreenWatch();
        String update="set -eu; test -f /root/vitalink/agent.json; "
            +"python3 -c "+quote("import json,os; p='/root/vitalink/agent.json'; c=json.load(open(p)); c['allow_screen']=False; open(p,'w').write(json.dumps(c,indent=2)+'\\n'); os.chmod(p,0o600)");
        String command=alpine(update)+"; "
            +"pkill -f '^python3 /root/vitalink/agent.py$' 2>/dev/null || true; "
            +"pkill -f '^/usr/bin/python3 /root/vitalink/agent.py$' 2>/dev/null || true; "
            +alpine("nohup /usr/bin/python3 /root/vitalink/agent.py </dev/null >>/root/vitalink/agent.log 2>&1 & echo 'Удалённый захват кадров запрещён'");
        task("VitaLink / запрет кадров",command,60000,result->log(result));
    }
    private JSONObject screenRequest(String endpoint,String method,String token) throws Exception {
        URL url=new URL("https://muchogdps.space/vitalink/v1/screen/"+endpoint);
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();
        if(!(connection instanceof HttpsURLConnection))throw new IOException("HTTPS is required");
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization","Bearer "+token);
        connection.setRequestProperty("Cache-Control","no-store");
        try{
            int code=connection.getResponseCode();
            if(code==401)throw new IOException("Неверный ключ просмотра (HTTP 401)");
            if(code!=200)throw new IOException("VitaLink HTTP "+code);
            ByteArrayOutputStream data=new ByteArrayOutputStream();
            try(InputStream input=connection.getInputStream()){
                byte[] chunk=new byte[8192];int count;
                while((count=input.read(chunk))!=-1){
                    if(data.size()+count>260000)throw new IOException("Слишком большой ответ");
                    data.write(chunk,0,count);
                }
            }
            return new JSONObject(new String(data.toByteArray(),StandardCharsets.UTF_8));
        }finally{
            connection.disconnect();
        }
    }
    private void startScreenWatch(){
        if(screenWatching)return;
        if(screenViewerToken==null)return;
        final String token=screenViewerToken.getText().toString().trim();
        if(!token.matches("[A-Za-z0-9_-]{32,160}")){
            screenViewerToken.setError("Вставь screen_view_token с VPS");return;
        }
        screenViewerToken.setText("");
        screenWatching=true;
        final long generation=++screenGeneration;
        if(remoteScreenStatus!=null)remoteScreenStatus.setText("Подключаюсь к VitaLink…");
        screenWorker.execute(()->{
            int lastSeq=-1;
            try{
                while(screenWatching && generation==screenGeneration && !Thread.currentThread().isInterrupted()){
                    screenRequest("session","POST",token);
                    JSONObject frame=screenRequest("frame","GET",token);
                    if(frame.optBoolean("available",false) && frame.optInt("seq",-1)!=lastSeq){
                        lastSeq=frame.optInt("seq",-1);
                        String encoded=frame.getString("jpeg");
                        if(encoded.length()>230000)throw new IOException("Размер кадра превышен");
                        byte[] jpeg=Base64.decode(encoded,Base64.DEFAULT);
                        Bitmap img=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);
                        if(img==null)throw new IOException("Ошибка декодирования JPEG");
                        final int shown=lastSeq;
                        runOnUiThread(()->{
                            if(!screenWatching || generation!=screenGeneration || isFinishing() || isDestroyed()){
                                img.recycle();return;
                            }
                            Bitmap old=remoteScreenBitmap;
                            remoteScreenBitmap=img;
                            if(remoteScreenPreview!=null)remoteScreenPreview.setImageBitmap(img);
                            if(old!=null && old!=img && !old.isRecycled())old.recycle();
                            if(remoteScreenStatus!=null)remoteScreenStatus.setText("● Экран PS Vita · кадр #"+shown);
                        });
                    }else if(!frame.optBoolean("available",false)){
                        runOnUiThread(()->{
                            if(screenWatching && generation==screenGeneration && remoteScreenStatus!=null)
                                remoteScreenStatus.setText("Ожидание кадра… Проверь разрешение и scrot на Вите.");
                        });
                    }
                    Thread.sleep(2300);
                }
            }catch(Exception error){
                runOnUiThread(()->{
                    if(generation==screenGeneration){
                        screenWatching=false;
                        if(remoteScreenStatus!=null)remoteScreenStatus.setText("Ошибка VitaLink: "+error.getMessage());
                    }
                });
            }finally{
                try{screenRequest("stop","POST",token);}catch(Exception ignored){}
            }
        });
    }
    private void stopScreenWatch(){
        if(!screenWatching)return;
        screenWatching=false;
        ++screenGeneration;
        if(remoteScreenStatus!=null)remoteScreenStatus.setText("Просмотр остановлен");
    }

    private void rowButtons(LinearLayout p,String[] labels,String[] keys){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        for(int i=0;i<labels.length;i++){
            final String key=keys[i];Button b=button(labels[i],false);b.setTextSize(12);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(51),1);lp.rightMargin=dp(5);row.addView(b,lp);
            b.setOnClickListener(v->{
                String cmd=key.startsWith("click ")?"xdotool "+key:"xdotool key --clearmodifiers "+key;
                task("Ввод "+key,x11(cmd),20000,s->{if(!s.contains("[exit 0]"))log(s);});
            });
        }
        add(p,row,9);
    }
    private void capture(){
        if(busy)return;
        String snap="export DISPLAY=:0; if ! command -v scrot >/dev/null 2>&1; then echo 'NO_SCROT'; exit 127; fi; "
            +"scrot -z -q 35 /tmp/vitactrl-screen.jpg && "
            +"printf '__VITASHOT__'; base64 /tmp/vitactrl-screen.jpg; printf '__END__'";
        task("Снимок экрана",alpine(snap),30000,s->{
            int a=s.indexOf("__VITASHOT__"),b=s.indexOf("__END__",a+12);
            if(a<0||b<0){
                log("Снимок не получен: "+(s.length()>1600?s.substring(0,1600):s)
                    +"\nНужен пакет scrot и работающая X11-сессия.");return;
            }
            try{
                String data=s.substring(a+12,b).replaceAll("\\s+","");
                byte[] image=android.util.Base64.decode(data,android.util.Base64.DEFAULT);
                Bitmap bitmap=BitmapFactory.decodeByteArray(image,0,image.length);
                if(bitmap==null)throw new IllegalStateException("пустое изображение");
                if(currentImage!=null&&!currentImage.isRecycled())currentImage.recycle();
                currentImage=bitmap;if(preview!=null)preview.setImageBitmap(bitmap);
                log("Снимок получен: "+bitmap.getWidth()+"×"+bitmap.getHeight());
            }catch(Exception e){log("Ошибка обработки снимка: "+e.getMessage());}
        });
    }
    private void youtubeUi(){
        LinearLayout c=card();add(content,c,0);
        heading(c,"YOUTUBE LITE","Попытка воспроизведения на экране PS Vita через mpv + yt-dlp.");
        add(c,text("Вита использует программное декодирование. Начни с 144p/240p; часть роликов может не открыться из-за ограничений YouTube, формата видео или мощности CPU.",12,MUTED,false),12);
        Button install=button("Установить mpv + yt-dlp",false);add(c,install,13);
        install.setOnClickListener(v->install("mpv yt-dlp",true));
        videoUrl=field("https://www.youtube.com/watch?v=...",false);add(c,videoUrl,12);
        Button play=button("▶  Воспроизвести на Вите (240p)",true);add(c,play,10);
        play.setOnClickListener(v->playYoutube());
        Button browse=button("Открыть YouTube в Badwolf",false);add(c,browse,10);
        browse.setOnClickListener(v->task("YouTube в браузере",x11("nohup badwolf https://m.youtube.com </dev/null >/tmp/vita-badwolf.log 2>&1 & echo 'Браузер запущен'"),20000,null));
        Button stop=button("Остановить видео",false);add(c,stop,10);
        stop.setOnClickListener(v->task("Остановка видео",alpine(
            "p=$(cat /tmp/vita-control-mpv.pid 2>/dev/null || :); "
            +"if [ -n \"$p\" ] && [ -r \"/proc/$p/comm\" ] && [ \"$(cat /proc/$p/comm)\" = mpv ]; "
            +"then kill \"$p\"; echo 'mpv остановлен'; else echo 'Нет активного плеера приложения'; fi"),25000,null));
        add(c,text("Это экспериментальный YouTube-плеер, а не официальный клиент YouTube. Видеоролик воспроизводится на Вите, не на телефоне.",12,MUTED,false),12);
    }
    private void playYoutube(){
        String link=videoUrl.getText().toString().trim();
        try{
            Uri u=Uri.parse(link);
            if(!"https".equalsIgnoreCase(u.getScheme()))throw new Exception("Нужна HTTPS-ссылка.");
            String h=u.getHost();
            if(h==null)throw new Exception("Нет домена.");
            h=h.toLowerCase(Locale.ROOT);
            if(!(h.equals("youtube.com")||h.equals("www.youtube.com")||h.equals("m.youtube.com")
                ||h.equals("music.youtube.com")||h.equals("youtu.be")||h.equals("www.youtube-nocookie.com")))
                throw new Exception("Поддерживаются только ссылки YouTube.");
            if(link.length()>1500)throw new Exception("Слишком длинная ссылка.");
        }catch(Exception ex){videoUrl.setError(ex.getMessage());return;}
        String player="export DISPLAY=:0; if ! command -v mpv >/dev/null 2>&1 || ! command -v yt-dlp >/dev/null 2>&1; "
            +"then echo 'Сначала установи mpv и yt-dlp'; exit 127; fi; "
            +"nohup mpv --no-config --vo=x11 --hwdec=no --ytdl-format='best[height<=240]/worst' "
            +"-- "+quote(link)+" </dev/null >/tmp/vita-control-youtube.log 2>&1 & "
            +"echo $! >/tmp/vita-control-mpv.pid; echo 'Плеер запущен. Если видео не пошло, смотри журнал ошибок в /tmp/vita-control-youtube.log'";
        task("YouTube 240p",alpine(player),25000,null);
    }

    private void effectsUi(){
        LinearLayout c=card();add(content,c,0);
        heading(c,"АНИМАЦИИ ДЛЯ ЭКРАНА","Новые сцены с программной 2D-графикой, а не стандартные демки X11.");
        add(c,text("Vita Aurora: плавное северное сияние, неоновое небо и мерцающие звёзды. Исходник уже внутри APK.",12,MUTED,false),12);
        Button install=button("Установить игры и Aurora",false);add(c,install,12);
        install.setOnClickListener(v->confirm("Установить Aurora и игры?",
            "Скопировать 4 встроенных проекта и установить Python/Pygame/Tkinter в Alpine?",
            this::installGamePack));
        Button launch=button("✦  Запустить Aurora на Вите",true);add(c,launch,9);
        launch.setOnClickListener(v->launchGame("aurora_visuals.py"));
        Button game=button("▶  Открыть игровой центр",false);add(c,game,9);
        game.setOnClickListener(v->showTab("Игры"));
        add(c,text("Aurora работает на CPU без 3D-ускорения. FPS зависит от нагрузки на PS Vita.",12,MUTED,false),10);
    }


    // VitaLink is installed over the same SSH connection as the existing VitaControl.
    // Operator and device tokens are separate and never written into this repository.
    private void bridgeUi(){
        LinearLayout c=card();add(content,c,0);
        heading(c,"VITALINK / MUCHOVITA","Связь PS Vita ↔ VPS ↔ GitHub Actions. Вита сама подключается к VPS по HTTPS.");
        add(c,text("VitaLink на основном домене: https://muchogdps.space/vitalink. При подключении PS Vita не открывает SSH наружу.",12,MUTED,false),12);
        bridgeUrl=field("https://muchogdps.space/vitalink",false);
        bridgeUrl.setText("https://muchogdps.space/vitalink");
        add(c,bridgeUrl,14);
        bridgeToken=field("agent_token из /etc/vitalink/server.json",false);
        bridgeToken.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        add(c,bridgeToken,9);
        add(c,text("Вводи только agent_token, НЕ operator_token. Токен не сохраняется в настройках Android и не отправляется в GitHub.",12,MUTED,false),9);
        Button install=button("ПОДКЛЮЧИТЬ ВИТУ К VPS",true);add(c,install,13);
        install.setOnClickListener(v->installVitaLink());
        Button log=button("Проверить журнал VitaLink на Вите",false);add(c,log,9);
        log.setOnClickListener(v->task("VitaLink / журнал",alpine(
            "ls -l /root/vitalink/agent.py /root/vitalink/agent.json 2>&1; "
            +"tail -n 45 /root/vitalink/agent.log 2>&1"),20000,null));
        Button health=button("Проверить доступ к серверу",false);add(c,health,9);
        health.setOnClickListener(v->task("VitaLink / проверка HTTPS",alpine(
            "if ! command -v python3 >/dev/null; then echo 'Сначала установи Python'; exit 127; fi; "
            +"python3 -c "+quote("import urllib.request; print(urllib.request.urlopen('https://muchogdps.space/vitalink/health',timeout=12).read().decode())")),30000,null));
        add(c,text("Для заданий GitHub используется подписанный OIDC: секреты GitHub не нужны. Для просмотра кадров нужен отдельный screen_view_token.",12,MUTED,false),14);
    }
    private void installVitaLink(){
        String url=bridgeUrl.getText().toString().trim();
        String token=bridgeToken.getText().toString().trim();
        Uri u=Uri.parse(url);
        if(!"https".equalsIgnoreCase(u.getScheme()) ||
            !"muchogdps.space".equalsIgnoreCase(u.getHost()) ||
            u.getPort()!=-1  || !("/vitalink".equals(u.getPath()) || "/vitalink/".equals(u.getPath()))){
            bridgeUrl.setError("Нужен адрес https://muchogdps.space/vitalink");return;
        }
        if(token.length()<32 || token.length()>160 || !token.matches("[A-Za-z0-9_-]+")){
            bridgeToken.setError("Вставь agent_token из конфига VPS (не operator_token)");return;
        }
        confirm("Установить агент VitaLink?",
            "VitaControl установит Python и сертификаты в Alpine, скачает закреплённый исходник агента с GitHub и сохранит секрет только на Вите. "
            +"После запуска Вита начнёт опрашивать HTTPS-сервер. Удалённые shell-команды и установка пакетов по умолчанию запрещены.",
            ()->{
                try{
                    JSONObject config=new JSONObject();
                    config.put("url",url);config.put("token",token);
                    config.put("allow_shell",false);config.put("allow_install",false);
                    String b64=Base64.encodeToString(config.toString().getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
                    String source="https://raw.githubusercontent.com/IZKGMD/AutoGDBot/"
                        +"a83c0277581ddade87d6c6414e2fb59d9adf1c5f/vitalink/agent.py";
                    String download="import urllib.request; "
                        +"urllib.request.urlretrieve("+quotePython(source)+", '/root/vitalink/agent.py')";
                    String auto="\n# VITALINK_AUTOSTART\n"
                        +"if [ -f /root/vitalink/agent.json ]; then "
                        +"nohup /usr/bin/python3 /root/vitalink/agent.py "
                        +">>/root/vitalink/agent.log 2>&1 </dev/null & fi\n";
                    String auto64=Base64.encodeToString(auto.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
                    String cmd="set -eu; umask 077; "
                        +"apk add python3 ca-certificates; "
                        +"mkdir -p /root/vitalink; "
                        +"python3 -c "+quote(download)+"; "
                        +"printf '%s' "+quote(b64)+" | base64 -d > /root/vitalink/agent.json; "
                        +"chmod 600 /root/vitalink/agent.json; "
                        +"touch /root/.profile; "
                        +"if ! grep -q '# VITALINK_AUTOSTART' /root/.profile; then "
                        +"printf '%s' "+quote(auto64)+" | base64 -d >> /root/.profile; fi; "
                        +"nohup python3 /root/vitalink/agent.py </dev/null "
                        +">/root/vitalink/agent.log 2>&1 & "
                        +"echo 'VitaLink agent installed in Alpine. Polling VPS via HTTPS; log /root/vitalink/agent.log';";
                    taskWithTerminal("VitaLink: установка агента и запуск",cmd,240000);
                    bridgeToken.setText("");
                }catch(Exception e){log("Не удалось подготовить конфигурацию: "+e.getMessage());}
            });
    }
    private static String quotePython(String s){
        return "'"+s.replace("\\","\\\\").replace("'","\\'")+"'";
    }

    private void turboUi(){
        LinearLayout c=card();add(content,c,0);
        heading(c,"VITA TURBO","Управление штатными профилями процессора LinuxOnVita.");
        add(c,text("Поддерживаемые частоты: 111, 166, 222, 333 и 444 МГц. Не превышаем предел 444 МГц, не изменяем напряжение и не отключаем защитные механизмы.",12,MUTED,false),12);
        Button auto=button("⚡ АВТОТУРБО — адаптация к нагрузке",true);add(c,auto,14);
        auto.setOnClickListener(v->confirm("Активировать Автотурбо?",
            "Штатный режим ondemand сам повышает частоту при нагрузке и снижает в простое. После переключения Vita Turbo проверит CPU, RAM и ZRAM.",
            ()->turboTask("Автотурбо / адаптивный режим","ondemand")));
        Button boost=button("🚀 МАКСИМУМ — 444 МГц",false);add(c,boost,9);
        boost.setOnClickListener(v->confirm("Максимальный штатный CPU?",
            "vita-cpu max удерживает до 444 МГц. Возможны больший нагрев и расход батареи. Это максимум, предусмотренный LinuxOnVita, а не экстремальный разгон.",
            ()->turboTask("Максимальная производительность","max")));
        Button balanced=button("⚖ Сбалансированный — 333 МГц",false);add(c,balanced,9);
        balanced.setOnClickListener(v->confirm("Сбалансированный профиль?",
            "Переключить CPU на штатные 333 МГц?",
            ()->turboTask("Сбалансированный профиль","balanced")));
        Button revert=button("↩ Вернуть динамическую регулировку",false);add(c,revert,9);
        revert.setOnClickListener(v->turboTask("Динамическая регулировка","ondemand"));
        LinearLayout info=card();add(content,info,14);
        heading(info,"ДИАГНОСТИКА СИСТЕМЫ","Фактическая частота, нагрузка, память, ZRAM и место на диске.");
        Button diag=button("Проверить производительность",true);add(info,diag,12);
        diag.setOnClickListener(v->turboTask("Диагностика Vita Turbo","status"));
        Button viewer=button("Открыть отчёт в Sakura",false);add(info,viewer,9);
        viewer.setOnClickListener(v->viewLog());
        add(info,text("Диагностика ничего не меняет. ZRAM не перенастраиваем без необходимости. Прирост скорости зависит от приложения: видео и 3D могут ограничиваться графическими драйверами.",12,MUTED,false),11);
    }

    private String turboHostStage(SshClient ssh,String name,String command,int timeout) throws Exception {
        progress("Vita Turbo: "+name);
        String answer;
        try{answer=ssh.execute(command,timeout);}
        catch(Exception e){answer="SSH ОШИБКА: "+e.getMessage();}
        String logEntry=name+"\n"+answer+"\n";
        String encoded=Base64.encodeToString(logEntry.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
        String write=journalCommand(ssh,"Отчёт: "+name,
            "printf '%s' '"+encoded+"' | base64 -d >> "+LIVE_LOG,25000);
        if(!success(write))throw new IllegalStateException("Не удалось сохранить журнал: "+write);
        if(!success(answer))throw new IllegalStateException(name+": "+answer);
        return answer;
    }

    private void turboTask(String name,String profile){
        if(busy){log("Другая операция ещё выполняется.");return;}
        busy=true;status.setText("● Vita Turbo...");
        worker.execute(()->{
            String result;boolean done=false;
            try{
                SshClient ssh=client();
                progress(openProgressTerminal(ssh,"VITA TURBO: "+name,true));
                turboHostStage(ssh,"1/5: Исходное состояние CPU",
                    "command -v vita-cpu >/dev/null 2>&1 || { echo 'Утилита vita-cpu отсутствует'; exit 127; }; vita-cpu status",20000);
                if(!"status".equals(profile)){
                    if(!("max".equals(profile)||"ondemand".equals(profile)||"balanced".equals(profile)))
                        throw new IllegalArgumentException("Недопустимый профиль CPU");
                    turboHostStage(ssh,"2/5: Переключение CPU → "+profile,"vita-cpu "+profile,20000);
                }else{
                    turboHostStage(ssh,"2/5: Только диагностика",
                        "echo 'Настройки CPU не менялись'",15000);
                }
                turboHostStage(ssh,"3/5: Результат настройки CPU","vita-cpu status",20000);
                turboHostStage(ssh,"4/5: RAM и ZRAM",
                    "free -m; if command -v vita-swap >/dev/null 2>&1; then vita-swap status; else echo 'vita-swap не найден'; fi",25000);
                turboHostStage(ssh,"5/5: Нагрузка и накопители",
                    "uptime; df -h /mnt/ux0 /mnt/ur0 2>/dev/null || df -h /; "
                    +"if [ -r /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq ]; then "
                    +"echo 'Частота (кГц):'; cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq; fi",20000);
                done=true;
                result=name+": выполнено. Подробные данные в Sakura на Вите, журнал: "+LIVE_LOG;
            }catch(Exception e){
                result="Ошибка Vita Turbo: "+e.getMessage()+
                    "\nЕсли возникла ошибка после применения профиля, кнопка «Вернуть динамическую регулировку» переключает на ondemand.";
            }
            final boolean ok=done;final String message=result;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                busy=false;status.setText(ok?"● Vita Turbo готов":"● Vita Turbo: ошибка");log(message);
            });
        });
    }

    private void systemUi(){
        LinearLayout c=card();add(content,c,0);
        heading(c,"СИСТЕМА","Статистика реального LinuxOnVita, Wi-Fi, RAM и Alpine.");
        Button diag=button("Обновить сведения о Вите",true);add(c,diag,12);
        diag.setOnClickListener(v->task("О системе",
            "echo '=== Linux ==='; uname -a; echo '=== Wi-Fi ==='; ip -4 addr show; "
            +"echo '=== RAM ==='; free -m; echo '=== Alpine ==='; alpine-chroot -c 'cat /etc/alpine-release; df -h /' "
            +"2>&1; echo '=== Uptime ==='; uptime",45000,null));
        Button installed=button("Список установленных пакетов",false);add(c,installed,10);
        installed.setOnClickListener(v->task("Установленные пакеты",alpine("apk info | head -90"),45000,null));
        Button space=button("Свободное место",false);add(c,space,10);
        space.setOnClickListener(v->task("Свободная память", "df -h; free -m",20000,null));
        Button wifi=button("Статус Wi-Fi",false);add(c,wifi,10);
        wifi.setOnClickListener(v->task("Состояние Wi-Fi","vita-wifi status",20000,null));
        Button viewer=button("Открыть терминал прогресса на Вите",false);add(c,viewer,9);
        viewer.setOnClickListener(v->viewLog());
        Button pkg=button("Проверка Alpine / apk / X11",false);add(c,pkg,10);
        pkg.setOnClickListener(v->task("Диагностика",
            "command -v alpine-chroot; alpine-chroot -c 'command -v apk; "
            +"command -v xdotool; command -v xclip; command -v scrot; echo DISPLAY=$DISPLAY; "
            +"ls /tmp/.X11-unix 2>&1' ",25000,null));

        Button wm=button("↻  Перезапустить IceWM (остаться в Linux)",true);add(c,wm,15);
        wm.setOnClickListener(v->confirm("Обновить графическую оболочку?",
            "Перезапустится только оконный менеджер IceWM, Linux и открытые программы останутся запущены.",
            ()->task("Перезапуск IceWM",x11("icewm --restart"),20000,null)));
        Button gui=button("⟳  Выйти и снова войти в рабочий стол",false);add(c,gui,9);
        gui.setOnClickListener(v->confirm("Перезапустить сеанс рабочего стола?",
            "Linux останется запущен. Сначала завершится сеанс IceWM, затем приложение попробует снова выполнить alpine-desktop. Открытые окна закроются. Автоматический запуск может не сработать из SSH: тогда на Вите нужно ввести alpine-desktop в основном терминале.",
            ()->task("Перезапуск графического сеанса Linux",
                "nohup /bin/sh -c 'sleep 2; killall icewm-session 2>/dev/null || true; "
                +"n=0; while [ \"$n\" -lt 25 ] && (pidof Xorg >/dev/null 2>&1 || pidof xinit >/dev/null 2>&1); "
                +"do sleep 1; n=$((n+1)); done; sleep 3; "
                +"/usr/bin/alpine-desktop' </dev/null >/tmp/vitacontrol-session-restart.log 2>&1 & "
                +"echo 'Запрошен выход и повторный вход в графический сеанс Linux; если возврата нет, запусти alpine-desktop с TTY.'",
                15000,result->log("▶ Перезапуск сеанса\n"+result+
                    "\nДиагностика: /tmp/vitacontrol-session-restart.log"))));
        Button full=button("Полная перезагрузка консоли → VitaOS",false);add(c,full,12);
        full.setOnClickListener(v->confirm("Вернуться в Sony VitaOS?",
            "Это ПОЛНАЯ перезагрузка консоли, а не Linux-сеанса! Несохранённые данные пропадут. Продолжить?",
            ()->task("Полная перезагрузка","sync; (sleep 2; reboot) </dev/null >/dev/null 2>&1 & echo 'Возврат в VitaOS'",
                15000,null)));
        add(c,text("При выходе с повторным входом Wi-Fi и базовая Linux-система сохраняются. Если автоматическое открытие IceWM не удалось, введи alpine-desktop в TTY Виты.",12,MUTED,false),9);
        add(c,text("Команды выполняются от пользователя, указанного в SSH. Установку и изменение пакетов приложение делает только после подтверждения.",12,MUTED,false),15);
    }
    @Override protected void onStop(){
        stopScreenWatch();
        super.onStop();
    }
    @Override protected void onDestroy(){
        stopScreenWatch();
        super.onDestroy();
        worker.shutdownNow();
        screenWorker.shutdownNow();
        if(remoteScreenBitmap!=null && !remoteScreenBitmap.isRecycled())remoteScreenBitmap.recycle();
        if(currentImage!=null&&!currentImage.isRecycled())currentImage.recycle();
    }
}