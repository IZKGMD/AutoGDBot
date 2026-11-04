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
        buildUi(); showTab("Пакеты");
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
        String[] names={"Пакеты","Экран","YouTube","Эффекты","Система"};
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
        add(root,text("VitaControl 2.0 • Alpine chroot • SSH с проверкой ключа",11,MUTED,false),15);
        setContentView(scroller);
    }
    private void showTab(String name){
        activeTab=name; content.removeAllViews();
        String[] names={"Пакеты","Экран","YouTube","Эффекты","Система"};
        for(int i=0;i<names.length;i++) tabButtons[i].setBackground(bg(names[i].equals(name)?Color.rgb(39,88,115):Color.rgb(39,57,82),names[i].equals(name)?CYAN:0,12));
        switch(name){
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
        heading(c,"ВИЗУАЛЬНЫЕ ЭФФЕКТЫ","Небольшие X11-приложения без аппаратного 3D.");
        effect(c,"🟢  Матрица в терминале","cmatrix","cmatrix",
            "nohup sakura -e cmatrix </dev/null >/tmp/vita-matrix.log 2>&1 & echo 'Матрица запущена'");
        effect(c,"👀  Следящие глаза","xeyes","xeyes",
            "nohup xeyes -geometry 280x190+330+180 </dev/null >/tmp/vita-eyes.log 2>&1 & echo 'xeyes запущен'");
        effect(c,"🕒  Неоновые часы","xclock","xclock",
            "nohup xclock -digital -update 1 -fg '#66e7ff' -bg '#0c1631' -geometry 510x100+200+180 </dev/null >/tmp/vita-clock.log 2>&1 & echo 'xclock запущен'");
        effect(c,"🌈  Окно системного мониторинга","btop","btop",
            "nohup sakura -e btop </dev/null >/tmp/vita-btop.log 2>&1 & echo 'btop запущен'");
        add(c,text("Эффекты открываются поверх рабочего стола. Закрывай их обычной кнопкой закрытия окна IceWM.",12,MUTED,false),13);
    }
    private void effect(LinearLayout c,String title,String pkg,String cmd,String script){
        add(c,text(title,14,WHITE,true),18);
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);
        Button launch=button("Запустить",true),install=button("Установить "+pkg,false);
        LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(0,dp(54),1);l.rightMargin=dp(5);
        r.addView(launch,l);r.addView(install,new LinearLayout.LayoutParams(0,dp(54),1));
        add(c,r,7);
        launch.setOnClickListener(v->task(title,x11("if ! command -v "+cmd+" >/dev/null 2>&1; then echo 'Нужен пакет "+pkg+"'; exit 127; fi; "+script),20000,null));
        install.setOnClickListener(v->install(pkg,false));
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
        add(c,text("Команды выполняются от пользователя, указанного в SSH. Установку и изменение пакетов приложение делает только после подтверждения.",12,MUTED,false),15);
    }
    @Override protected void onDestroy(){
        super.onDestroy();
        worker.shutdownNow();
        if(currentImage!=null&&!currentImage.isRecycled())currentImage.recycle();
    }
}