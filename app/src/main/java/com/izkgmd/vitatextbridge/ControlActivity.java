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
    private String host,username,password; private int port;
    private LinearLayout content, root, tabs; private TextView status,output;
    private Button[] tabButtons;
    private String activeTab="Пакеты";
    private boolean busy;
    private ImageView preview; private Bitmap currentImage;
    private EditText packageSearch, videoUrl;

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
        buildUi(); showTab("Игры");
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
        String[] names={"Игры","Пакеты","Экран","YouTube","Эффекты","Система"};
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
        add(root,text("VitaControl 2.2 • 4 встроенных проекта • SSH",11,MUTED,false),15);
        setContentView(scroller);
    }
    private void showTab(String name){
        activeTab=name; content.removeAllViews();
        String[] names={"Игры","Пакеты","Экран","YouTube","Эффекты","Система"};
        for(int i=0;i<names.length;i++) tabButtons[i].setBackground(bg(names[i].equals(name)?Color.rgb(39,88,115):Color.rgb(39,57,82),names[i].equals(name)?CYAN:0,12));
        switch(name){
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
        heading(c,"ИГРОВОЙ ЦЕНТР","Все 4 проекта встроены в APK. Установка через SSH прямо на Vita — никаких архивов или ручного ввода на консоли.");
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
            alpine("tail -n 55 /tmp/vita-game-"+file+".log 2>&1"),15000,null));
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
    private void progress(String message){runOnUiThread(()->{
        if(!isFinishing()&&!isDestroyed()){status.setText("●  "+message);log("• "+message);}
    });}
    private void installGamePack(){
        if(busy){log("Сначала дождись завершения другого действия.");return;}
        busy=true;status.setText("●  Установка игр...");
        worker.execute(()->{
            String stage="Подготовка";String failure=null;
            try{
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
                    String answer=client().execute(alpine(script),45000);
                    if(!success(answer))throw new IllegalStateException(name+": "+answer);
                    progress("Передано: "+name);
                }
                stage="Обновление репозиториев";
                progress("Обновляем каталог пакетов Alpine");
                String update=client().execute(alpine("apk update"),120000);
                if(!success(update))throw new IllegalStateException("apk update:\n"+update);
                stage="Установка Python / Pygame / Tkinter";
                progress("Устанавливаем Python + игровые движки");
                String deps=client().execute(alpine("apk add python3 py3-pygame python3-tkinter"),240000);
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
                    String result=client().execute(alpine(cmd),20000);
                    if(!success(result))throw new IllegalStateException(result);
                }
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
            +probe+"nohup python3 "+file+" </dev/null >/tmp/vita-game-"+filename+".log 2>&1 & "
            +"echo 'Запущен: "+filename+" (PID '$!')'; sleep 1; "
            +"if ! kill -0 $! 2>/dev/null; then echo 'Процесс завершился; открой журнал запуска'; "
            +"tail -n 12 /tmp/vita-game-"+filename+".log; fi";
        task("Запуск "+filename,x11(script),25000,null);
    }

    private void packagesUi(){
        LinearLayout c=card();add(content,c,0);
        heading(c,"МАГАЗИН ПАКЕТОВ","Установка в Alpine Linux через alpine-chroot. Совместимость с ARMv7 проверяется на Вите.");
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
        update.setOnClickListener(v->task("apk update",alpine("apk update"),90000,null));add(c,update,9);
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
            task("Установка "+names,alpine("apk add "+names),180000,null));
    }
    private void screenUi(){
        LinearLayout c=card();add(content,c,0);
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
    @Override protected void onDestroy(){
        super.onDestroy();
        worker.shutdownNow();
        if(currentImage!=null&&!currentImage.isRecycled())currentImage.recycle();
    }
}