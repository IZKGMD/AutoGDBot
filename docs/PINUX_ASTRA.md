# Pinux Astra — Global Update 0.5.0

Это вымышленная ОС **внутри CTRL+LIFE**, а не веб-страница, виртуальная машина и не доступ к компьютеру игрока. Реализована на Java/libGDX через существующий \`PixelCanvas\`; весь терминал изолирован от host OS.

## Native Linux-style UI foundation (VisUI)

The desktop and newly ported applications now render at the **actual game-window resolution**, not the 480×270 pixel-art framebuffer.
The window layer uses [VisUI](https://github.com/kotcrab/vis-ui) 1.5.6 (Apache-2.0; matches libGDX 1.13.1), including `VisWindow` for drag, resize and stacking and `VisTextField` for the in-game terminal.
The architecture follows the separation found in [gezhov/Fake-OS-Desktop](https://github.com/gezhov/Fake-OS-Desktop) (MIT): shell, independent app windows, workspace, taskbar and focus. Swing classes were not copied because they are incompatible with libGDX's render loop.

Native screens: Terminal, Files, TaskMail and Settings. Astra Code still delegates to the original ByteScript workbench; porting its editor is a separate visual milestone. Terminal commands are **game simulations only** and never execute real commands.

**Visual quality gate:** `pinux-desktop-preview.yml` runs an actual GLFW/libGDX smoke under Xvfb, captures PNGs, and uploads them as a workflow artifact. We review new captures before moving to the next screen.

## Window manager

Отдельный `PinuxWindowManager` отвечает за z-order, фокус, двойное нажатие на заголовок, анимации открытия/сворачивания/закрытия, минимизацию, восстановление, перетаскивание, изменение размеров окна за правый нижний угол, прижатие к левому/правому краю, максимизацию при перетаскивании к верхнему краю, расположение окон на рабочем столе. Клики по заголовкам перехватываются до глобальных кнопок, чтобы предотвратить click-through. Клавиша Tab циклически меняет активное окно.

## Что уже есть

- Заставка загрузки, детализированный рабочий стол Pinux Astra, верхняя системная панель с внутриигровым временем, меню приложений и нижний dock.
- Приложения: **Astra Code**, **Terminal**, **Files**, **TaskMail**, **Settings**. Есть синяя и тёплая темы, режим уменьшения анимаций.
- Astra Code использует знакомую композицию IDE: activity rail → Explorer → вкладка \`main.bs\` → исходный код → панель проверок → действия.
- Терминал открывается как отдельное перемещаемое окно поверх рабочего места Astra Code; клавиатурный ввод переключается в активное окно консоли, а не в автопечать кода.
- Файловый экран показывает игровую структуру проекта, не реальные диски.
- Новая фаза **набора черновика**: в заданиях IDE любое обычное нажатие клавиши добавляет символы в подготовленный исходник; видимый код появляется постепенно.
- После проваленных тестов **следующее обычное нажатие клавиши автоматически запускает фазу исправления**: исправленный код появляется через ту же механику набора. Ручной выбор варианта патча в старом интерфейсе сохранён как дополнительный инструмент. Повторный прогон тестов нужен после завершения ввода.
- Используется **настоящая проверка ByteScript** из прежней версии игры: тесты, синтаксические ошибки, операции и локальная история версий сохраняются.

## Управление

| Клавиша | Действие |
|---|---|
| F1 | Astra Code |
| F2 | Terminal |
| F3 | Файлы |
| F4 | TaskMail |
| F6 | Settings |
| Tab | Переключить активное окно |
| Esc | Закрыть активное окно → встать от компьютера |
| Любые обычные клавиши в редакторе | Постепенно набрать исходник или исправление |
| Enter в Terminal | Выполнить игровую команду |
| ↑ / ↓ в Terminal | История команд |
| Backspace в Terminal | Удалить символ |

## Команды терминала

- \`help\`, \`clear\`, \`pwd\`, \`whoami\`, \`date\`, \`neofetch\`, \`ls\`, \`ls -la\`, \`echo <text>\`
- \`cat README.md\`, \`cat main.bs\`, \`git status\`, \`git log\`, \`jobs\`
- **\`test\`** запускает текущую проверку через \`Coding.test\` и показывает результаты выполнения.
- \`code\`, \`files\`, \`desktop\`, \`exit\` переключают игровые приложения.

**Важно:** \`git\` показывает только игровую локальную историю проекта; команды не запускают реальные процессы, не читают файлы с пользовательского диска и не обращаются к Интернету.

## Архитектура

- \`life.ctrl.ui.PinuxAstra\`: desktop, dock, игровая консоль, файловый экран и маршрутизация ввода.
- \`life.ctrl.ui.Workbench\`: внутренний UI Astra Code, биржа заказов и прежние инструменты.
- \`life.ctrl.ui.LifeScreens\`: связь игрового ПК с оболочкой.
- \`life.ctrl.CtrlLife\`: передача нажатий в оболочку при режиме \`IDE\`.
- \`life.ctrl.model.Coding\`: перегрузки \`accept(..., tactile)\` и \`repair(..., tactile)\`. Отдельный выбор режима защищает прямые доменные тесты от несовместимых изменений.

## Ограничения Global Update

Astra Code остаётся полнофункциональным игровым редактором в развёрнутом режиме; в небольшом восстановленном окне показывается предпросмотр проекта с кнопкой `EXPAND IDE`. Терминал содержит игровые команды и не запускает программы ПК игрока. Кнопки мыши используются в логических координатах libGDX.

## Следующий этап разработки

- Прогонять Windows GUI smoke на реальной машине с 125/150% DPI.
- Добавить сохранение расположения окон между игровыми днями.
- Углубить подсветку ByteScript, мультифайловые проекты, автодополнение, системные уведомления и звук UI.
- Расширить игровые заказы и улучшить баланс работы/жизни.
