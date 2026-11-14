package ru.pasha.adaptationwheel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import ru.pasha.adaptationwheel.AdaptationWheel;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import ru.pasha.adaptationwheel.core.MetaMechanic;
import ru.pasha.adaptationwheel.core.StageProtections;
import ru.pasha.adaptationwheel.net.AnalysisInfo;
import ru.pasha.adaptationwheel.net.CatalogueEntry;
import ru.pasha.adaptationwheel.net.MetaNetwork;
import ru.pasha.adaptationwheel.net.WheelEntry;
import ru.pasha.adaptationwheel.net.WheelNetwork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import static ru.pasha.adaptationwheel.client.WheelUi.*;

/**
 * The J menu. Catalogue discovery never starts adaptation; only server-supplied learned entries can be toggled.
 * Wide windows show the list and the selected adaptation's details side by side, narrower ones open the details as
 * their own screen ({@link MenuLayout}). The list is split into groups with a heading each ({@link MenuRows}).
 */
public final class AdaptationScreen extends Screen {
    /** The «Сводка колеса» row over the list; the menu opens on it and Home goes back to it. */
    static final String SUMMARY = "@summary";
    /** What happened to the wheel during this game, for «Недавно» and the details' «История». */
    static final WheelJournal JOURNAL = new WheelJournal();
    private static ClientWheelState.State journalState;
    private static WheelEntry journalFps;
    /** Whether «Подробности для настройки» is open; kept while the game runs. */
    private static boolean settingsOpen;
    /** List groups in their order; the index is {@link #group}. */
    private static final String[] GROUPS = {"running", "waiting", "done", "off", "undiscovered"};
    /** The small wheel of the summary row and title, as runs of one colour; normal and faded. */
    private static final int[] WHEEL_RUNS = WheelSummary.runs(WheelSummary.wheelIcon(false), 16),
            WHEEL_RUNS_FADED = WheelSummary.runs(WheelSummary.wheelIcon(true), 16);
    /** A 7×7 wheel for the wheel's own events in «Недавно». */
    private static final int[] WHEEL_GLYPH = {0b0011100, 0b0101010, 0b1001001, 0b1111111, 0b1001001, 0b0101010, 0b0011100};
    private static final int INNER = 0xFF29232D;
    private MenuLayout layout;
    private MenuRows rows;
    private int scroll, maxScroll;
    private boolean catalogue, picking;
    private String query = "";
    private AdaptationProfile category;
    private String selectedKey = SUMMARY;
    /** A counter of the summary leaves only its group in the list; -1 shows every group. */
    private int groupFilter = -1, filteredGroup = -1;
    /** The adaptation that ends before the others this frame, or null. */
    private String firstKey;
    private SummaryView summaryView;
    private Switch global, vision;
    private Tab mine, all;
    private Flat categoryButton, learning, look;
    private EditBox search;
    private DetailView detail;
    private List<VisibleEntry> visibleEntries = List.of();
    private List<CatalogueEntry> cachedCatalogue;
    private List<WheelEntry> cachedLearned;
    private WheelEntry cachedFps;
    private Map<String, VisibleEntry> cachedEntries = Map.of();
    private Map<String, VisibleEntry> filteredSource;
    private String filteredQuery;
    private AdaptationProfile filteredCategory;
    private boolean filteredCatalogue;
    private ItemStack wheelIcon;

    private record VisibleEntry(CatalogueEntry catalogue, WheelEntry learned) {
        String key() { return catalogue.key(); }
        AdaptationProfile profile() { return learned == null ? catalogue.profile() : learned.profile(); }
        int duration(int stage) { return (learned==null?catalogue.timings():learned.timings()).ticks(stage); }
        int color() { return key().equals(FpsAdaptation.KEY) ? FPS : profile().color; }
        int[] icon() { return CategoryIcons.forKey(key(), profile()); }
        String name() {
            if (I18n.exists(catalogue.translationKey())) return Component.translatable(catalogue.translationKey()).getString();
            return catalogue.fallbackName().isBlank() ? catalogue.key() : catalogue.fallbackName();
        }
        /** Damage and effect entries earn protection; meta and FPS adaptations unlock abilities instead. */
        boolean protects() { return learned == null ? !key().startsWith("environment:") && !key().equals(FpsAdaptation.KEY)
                : learned.protections().fourth() > 0 && MetaMechanic.fromKey(key()) == null && !key().equals(FpsAdaptation.KEY); }
        String categoryName() { return Component.translatable(key().equals(FpsAdaptation.KEY) ? "adaptationwheel.fps.category" : profile().translationKey()).getString(); }
        /** How a stage is sped up and started: a hit, an effect applied again, or a mechanic met again. */
        String trigger() { return key().startsWith("effect:") ? "_effect" : MetaMechanic.fromKey(key()) != null ? "_meta" : ""; }
    }

    /** A line of the details: its colour says how much it matters; a bar from 0 to 1 is drawn under it, -1 for none. */
    /** {@link Line#bar} marker: the row sits in the «Откуда знание» panel (design mod-compat-616-2026-11-07). */
    private static final float FRAMED = -2f;
    private static final int ORIGIN_FILL = 0xFF18222F, ORIGIN_BORDER = 0xFF2F4A6E, ORIGIN_LABEL = 0xFF6FA8FF;

    private record Line(Component text, int color, float bar, int barColor) {
        static Line of(Component text, int color) { return new Line(text, color, -1, 0); }
    }

    private record Section(Component title, List<Line> lines) {}

    public AdaptationScreen() { super(Component.translatable("gui.adaptationwheel.menu")); }

    public static void open() {
        WheelNetwork.requestCatalogue();
        Minecraft.getInstance().setScreen(new AdaptationScreen());
    }

    public static void openFpsDetails() {
        var mc=Minecraft.getInstance();
        var screen=new AdaptationScreen();
        screen.selectedKey=FpsAdaptation.KEY;
        screen.catalogue=FpsAdaptation.entry()==null;
        boolean split=new MenuLayout(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight()).split;
        mc.setScreen(split?screen:screen.new DetailScreen(FpsAdaptation.KEY));
    }

    /** The menu or its details are open; the corner panel then hides, it peeked out beside the wide menu. */
    static boolean showing(Screen screen) { return screen instanceof AdaptationScreen || screen instanceof DetailScreen || screen instanceof LearningScreen || screen instanceof WheelSkinScreen || screen instanceof SummaryScreen; }

    /** Learned adaptations (the low FPS one included), finished and running ones: the menu's header and the wheel's tooltip. */
    static int[] counts(ClientWheelState.State state) {
        List<WheelEntry> learned = new ArrayList<>(state == null ? List.of() : state.entries());
        // The low FPS adaptation lives on the client, outside the server's list.
        if (FpsAdaptation.entry() != null) learned.add(FpsAdaptation.entry());
        int done = 0, running = 0;
        for (WheelEntry entry : learned) {
            if (entry.finished()) done++;
            if (entry.pending()) running++;
        }
        return new int[]{learned.size(), done, running};
    }

    /** Feeds «Недавно» each client tick with the local player's wheel, when it changed. */
    static void observeJournal() {
        Minecraft mc = Minecraft.getInstance();
        ClientWheelState.State state = mc.player == null ? null : ClientWheelState.get(mc.player.getId());
        WheelEntry fps = FpsAdaptation.entry();
        if (state == null || state == journalState && fps == journalFps) return;
        journalState = state;
        journalFps = fps;
        List<WheelJournal.Seen> seen = new ArrayList<>();
        for (WheelEntry entry : state.entries()) seen.add(seen(entry));
        if (fps != null) seen.add(seen(fps));
        JOURNAL.observe(state.equipped(), seen, System.currentTimeMillis());
    }

    private static WheelJournal.Seen seen(WheelEntry entry) {
        int[] percents = new int[6];
        for (int stage = 1; stage < percents.length; stage++) percents[stage] = entry.protections().percent(Math.min(stage, 5));
        boolean protects = entry.protections().fourth() > 0 && MetaMechanic.fromKey(entry.key()) == null && !entry.key().equals(FpsAdaptation.KEY);
        return new WheelJournal.Seen(entry.key(), entry.translationKey(), entry.profile(), entry.stage(), entry.totalStages(), percents, protects);
    }

    static void clearJournal() {
        JOURNAL.clear();
        journalState = null;
        journalFps = null;
    }

    private ClientWheelState.State state() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : ClientWheelState.get(mc.player.getId());
    }

    private Map<String, VisibleEntry> allEntries() {
        List<CatalogueEntry> catalogueEntries = ClientWheelState.catalogue();
        ClientWheelState.State state = state();
        List<WheelEntry> learnedEntries = state == null ? List.of() : state.entries();
        WheelEntry fps=FpsAdaptation.entry();
        if (catalogueEntries == cachedCatalogue && learnedEntries == cachedLearned && fps==cachedFps) return cachedEntries;
        Map<String, VisibleEntry> result = new LinkedHashMap<>();
        for (CatalogueEntry entry : catalogueEntries) result.put(entry.key(), new VisibleEntry(entry, null));
        for (WheelEntry learned : learnedEntries) {
            VisibleEntry known = result.get(learned.key());
            CatalogueEntry entry = known == null
                    ? new CatalogueEntry(learned.key(), learned.translationKey(), ru.pasha.adaptationwheel.server.DamageCategories.fallbackName(learned.key()), learned.profile(),learned.timings())
                    : known.catalogue();
            result.put(learned.key(), new VisibleEntry(entry, learned));
        }
        result.put(FpsAdaptation.KEY,new VisibleEntry(FpsAdaptation.catalogue(),fps));
        cachedCatalogue = catalogueEntries;
        cachedLearned = learnedEntries;
        cachedFps=fps;
        cachedEntries = result;
        return cachedEntries;
    }

    /** The current tab and search; the category filter only when asked, so the picker can count every category. */
    private boolean matches(VisibleEntry entry, String match, boolean anyCategory) {
        return (catalogue || entry.learned() != null) && (anyCategory || category == null || entry.profile() == category)
                && (match.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(match)
                || entry.key().toLowerCase(Locale.ROOT).contains(match));
    }

    private void refreshEntries() {
        Map<String, VisibleEntry> source = allEntries();
        if (source == filteredSource && query.equals(filteredQuery) && category == filteredCategory
                && catalogue == filteredCatalogue && groupFilter == filteredGroup) return;
        String match = query.strip().toLowerCase(Locale.ROOT);
        // Groups in their order (running, waiting, finished, switched off, never met), each by name.
        visibleEntries = source.values().stream().filter(entry -> matches(entry, match, false)
                        && (groupFilter < 0 || group(entry) == groupFilter))
                .sorted(Comparator.comparingInt(AdaptationScreen::group)
                        .thenComparing(VisibleEntry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(VisibleEntry::key)).toList();
        int[] groups = new int[visibleEntries.size()];
        for (int i = 0; i < groups.length; i++) groups[i] = group(visibleEntries.get(i));
        if (rows != null) rows.build(groups);
        filteredSource = source;
        filteredQuery = query;
        filteredCategory = category;
        filteredCatalogue = catalogue;
        filteredGroup = groupFilter;
        // The details pane always shows something: a row that left the list gives way to the first match, or to the summary.
        if (layout != null && layout.split && !summarySelected() && selectedIndex() < 0)
            select(visibleEntries.isEmpty() ? SUMMARY : visibleEntries.get(0).key(), false);
    }

    private boolean summarySelected() { return SUMMARY.equals(selectedKey); }

    /** Shows only one group of the list (a counter of the summary), or every group again for the same one or -1. */
    private void filterGroup(int group) {
        groupFilter = group == groupFilter ? -1 : group;
        scroll = 0;
        refreshEntries();
    }

    private static int group(VisibleEntry entry) {
        WheelEntry learned = entry.learned();
        if (learned == null) return 4;
        if (!learned.enabled()) return 3;
        return learned.pending() ? 0 : learned.finished() ? 2 : 1;
    }

    @Override
    protected void init() {
        layout = new MenuLayout(width, height);
        rows = new MenuRows(layout.row, layout.compact ? 11 : 13);
        wheelIcon = new ItemStack(AdaptationWheel.WHEEL.get());
        int x = layout.left + 10;
        global = addRenderableWidget(new Switch(x, layout.controlsY, MenuLayout.GLOBAL_WIDTH, Component.translatable("gui.adaptationwheel.switch.adaptation"),
                () -> state() != null && state().enabled(), button -> {
                    ClientWheelState.State state = state();
                    if (state != null) WheelNetwork.setEnabled("", !state.enabled());
                }));
        vision = addRenderableWidget(new Switch(x + MenuLayout.GLOBAL_WIDTH + 6, layout.controlsY, MenuLayout.VISION_WIDTH, Component.translatable("gui.adaptationwheel.switch.vision"),
                () -> state() != null && state().nightVision(), button -> {
                    ClientWheelState.State state = state();
                    if (state != null && state.nightVisionUnlocked()) WheelNetwork.setEnabled("@nightvision", !state.nightVision());
                }));
        int tab = MenuLayout.TAB_WIDTH;
        mine = addRenderableWidget(new Tab(layout.tabX, layout.tabY, tab, 16, Component.translatable("gui.adaptationwheel.tab.mine"), button -> showCatalogue(false)));
        all = addRenderableWidget(new Tab(layout.tabX + tab + 4, layout.tabY, tab, 16, Component.translatable("gui.adaptationwheel.tab.all"), button -> showCatalogue(true)));
        categoryButton = addRenderableWidget(new Flat(layout.categoryX, layout.categoryY, layout.categoryWidth, 18, Component.empty(), button -> picking = !picking));
        search = new EditBox(font, layout.searchX + 5, layout.searchY + 5, layout.searchWidth - 10, 10, Component.translatable("gui.adaptationwheel.search"));
        search.setBordered(false);
        search.setMaxLength(100);
        // The hint is drawn unclipped; cut it to the field or it runs over the category button in narrow windows.
        search.setHint(Component.literal(fit(font, Component.translatable("gui.adaptationwheel.search").getString(), layout.searchWidth - 10))
                .withStyle(s -> s.withColor(FAINT)));
        search.setValue(query);
        search.setResponder(value -> { query = value; scroll = 0; refreshEntries(); });
        addRenderableWidget(search);
        addRenderableWidget(new IconButton(layout.closeX, layout.closeY, IconButton.CLOSE, Component.translatable("gui.adaptationwheel.close"), button -> onClose()));
        learning = addRenderableWidget(new Flat(layout.right - 86, layout.statusY - 1, 76, 12,
                Component.translatable("learning.adaptationwheel.open"), button -> minecraft.setScreen(new LearningScreen(this))));
        learning.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("learning.adaptationwheel.open.hint")));
        look = addRenderableWidget(new Flat(layout.right - 86 - 4 - 60, layout.statusY - 1, 60, 12,
                Component.translatable("skin.adaptationwheel.open"), button -> minecraft.setScreen(new WheelSkinScreen(this, null))));
        look.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("skin.adaptationwheel.open.hint")));
        detail =layout.split ? new DetailView(this::addRenderableWidget, layout.detailLeft, layout.detailTop,
                layout.detailRight, layout.detailBottom, selectedKey, null) : null;
        summaryView = layout.split ? new SummaryView(layout.detailLeft, layout.detailTop, layout.detailRight, layout.detailBottom, layout.compact, true) : null;
        filteredSource = null;
        tick();
        reveal(selectedIndex());
    }

    private void showCatalogue(boolean showCatalogue) {
        catalogue = showCatalogue;
        scroll = 0;
        tick();
    }

    @Override
    public void tick() {
        if (global == null) return;
        search.tick();
        refreshEntries();
        ClientWheelState.State state = state();
        global.active = state != null;
        vision.visible = state != null && state.nightVisionUnlocked();
        mine.selected = !catalogue;
        all.selected = catalogue;
        mine.count(counts(state)[0]);
        all.count(allEntries().size());
        String label = category == null ? Component.translatable("gui.adaptationwheel.all_categories").getString()
                : Component.translatable(category.translationKey()).getString();
        categoryButton.setMessage(Component.literal(fit(font, label, categoryButton.getWidth() - 16) + " ▾"));
        if (detail != null) detail.tick();
    }

    private int selectedIndex() {
        if (selectedKey == null) return -1;
        for (int i = 0; i < visibleEntries.size(); i++) if (visibleEntries.get(i).key().equals(selectedKey)) return i;
        return -1;
    }

    private void select(String key, boolean reveal) {
        selectedKey = key;
        if (detail != null) detail.show(key);
        if (reveal) reveal(selectedIndex());
    }

    /** The orange strip over the list while the wheel is not worn: its height with the gap under it, or 0. */
    private int bannerHeight() {
        ClientWheelState.State state = state();
        return state != null && !state.equipped() ? (layout.compact ? 15 : 17) + 3 : 0;
    }

    /** The «Сводка колеса» row: under the strip, over the list. */
    private int summaryTop() { return layout.listTop + bannerHeight(); }

    private boolean onSummaryRow(double mouseX, double mouseY) {
        int top = summaryTop();
        return mouseX >= layout.listLeft && mouseX < layout.listRight && mouseY >= top && mouseY < top + layout.row - 2;
    }

    /** The list's top edge, under the strip and the summary row. */
    private int listTop() { return summaryTop() + layout.row; }

    private int listHeight() { return layout.listBottom - listTop(); }

    /** Scrolls the list just enough to show a row. */
    private void reveal(int index) {
        if (index < 0 || layout == null || index >= rows.size()) return;
        maxScroll = Math.max(0, rows.height() - listHeight());
        scroll = rows.reveal(index, scroll, listHeight());
    }

    private boolean rowShown(int index) {
        return rows.shown(index, scroll, listHeight());
    }

    /** Up from the first row reaches the summary row, Down from it the first row. */
    private void moveSelection(int delta) {
        int index = summarySelected() ? -1 : selectedIndex();
        if (index < 0 && !summarySelected()) { selectRow(delta < 0 ? visibleEntries.size() - 1 : 0); return; }
        if (index + delta < 0) { selectSummary(); return; }
        selectRow(index + delta);
    }

    private void selectSummary() {
        select(SUMMARY, false);
        scroll = 0;
    }

    private void selectRow(int index) {
        if (!visibleEntries.isEmpty()) select(visibleEntries.get(Mth.clamp(index, 0, visibleEntries.size() - 1)).key(), true);
    }

    private float progress(WheelEntry entry, ClientWheelState.State state, float partialTick) {
        if (!entry.pending()) return entry.finished() ? 1 : 0;
        if (entry.key().equals(FpsAdaptation.KEY)) return Mth.clamp((float) (entry.work() / Math.max(1, entry.duration())), 0, 1);
        float remaining = state == null ? (float) Math.max(0, entry.duration() - entry.work()) : state.remaining(entry, partialTick);
        return Mth.clamp(1 - remaining / Math.max(1, entry.duration()), 0, 1);
    }

    /** Real seconds until the stage ends at the current speed (9.5 s at ×1.30, not the 12.4 s of work left); only orders the summary. */
    private float remainingSeconds(WheelEntry entry, ClientWheelState.State state, float partialTick) {
        if (entry.key().equals(FpsAdaptation.KEY) || state == null)
            return (float) (Math.max(0, entry.duration() - entry.work()) / Math.max(0.01, entry.speed())) / 20;
        return state.realRemaining(entry, partialTick) / 20;
    }

    /** Seconds of adaptation left at ×1: what the menu shows. A repeat hit does not change it, it makes it run faster. */
    private float adaptationSeconds(WheelEntry entry, ClientWheelState.State state, float partialTick) {
        if (entry.key().equals(FpsAdaptation.KEY) || state == null) return (float) Math.max(0, entry.duration() - entry.work()) / 20;
        return state.remaining(entry, partialTick) / 20;
    }

    /** The summary keeps real seconds to order its rows; it shows them, like the rest of the menu, at ×1. */
    private float shownSeconds(WheelSummary.Item item) {
        VisibleEntry visible = allEntries().get(item.key());
        WheelEntry entry = visible == null ? null : visible.learned();
        return entry == null ? item.seconds() : (float) (item.seconds() * Math.max(0.01, entry.speed()));
    }

    private static String seconds(float seconds) { return String.format(Locale.ROOT, "%.1f", seconds); }

    /** Whether a running stage is moving now; otherwise it is paused (wheel off or not worn, no darkness…). */
    private static boolean moving(WheelEntry entry, ClientWheelState.State state) {
        return entry.progressing() && (entry.key().equals(FpsAdaptation.KEY) || state != null && state.enabled() && state.equipped());
    }

    /** The second line of a row: the category in its colour, the state, and for a running stage the time in gold. */
    private List<Part> rowLine(VisibleEntry visible, ClientWheelState.State state, float partialTick) {
        WheelEntry entry = visible.learned();
        boolean on = entry != null && entry.enabled();
        List<Part> line = new ArrayList<>();
        line.add(part(visible.categoryName(), on ? visible.color() : dim(visible.color())));
        line.add(part(" · ", FAINT));
        if (entry == null) { line.add(part(Component.translatable("gui.adaptationwheel.row.undiscovered"), MUTED)); return line; }
        if (!entry.enabled()) { line.add(part(Component.translatable("gui.adaptationwheel.row.off"), FAINT)); return line; }
        if (entry.key().equals(FpsAdaptation.KEY) && FpsAdaptation.qualifying()) {
            line.add(part(Component.translatable("adaptationwheel.fps.qualifying_progress", seconds((float) FpsAdaptation.qualificationRemainingSeconds())), MUTED));
            return line;
        }
        if (entry.finished()) { line.add(part(Component.translatable("gui.adaptationwheel.row.done"), MUTED)); return line; }
        if (!entry.pending()) {
            // What the next hit brings: «удар → этап 2 · 40%».
            int next = Math.min(entry.stage() + 1, entry.totalStages());
            line.add(part(visible.protects() && next <= 4
                    ? Component.translatable("gui.adaptationwheel.row.waiting_target" + visible.trigger(), next, entry.protections().percent(next))
                    : Component.translatable("gui.adaptationwheel.row.waiting_next" + visible.trigger(), next), MUTED));
            return line;
        }
        int stage = entry.stage() + 1;
        line.add(part(visible.protects() && entry.stage() < 4
                ? Component.translatable("gui.adaptationwheel.row.stage_target", stage, entry.totalStages(), entry.protections().percent(stage))
                : Component.translatable("gui.adaptationwheel.row.stage", stage, entry.totalStages()), MUTED));
        line.add(part(" · ", FAINT));
        line.add(moving(entry, state) ? part(Component.translatable("gui.adaptationwheel.seconds", seconds(adaptationSeconds(entry, state, partialTick))), GOLD)
                : part(Component.translatable("gui.adaptationwheel.row.pause"), WARN));
        return line;
    }

    private Component progressLine(WheelEntry entry) {
        if(FpsAdaptation.qualifying()) return Component.translatable(entry.progressing()
                ?"adaptationwheel.fps.qualifying_progress":"adaptationwheel.fps.qualifying_paused",
                seconds((float) FpsAdaptation.qualificationRemainingSeconds()));
        if(entry.finished()) return Component.translatable("adaptationwheel.fps.done");
        return Component.translatable(entry.progressing()?"adaptationwheel.fps.progress":"adaptationwheel.fps.paused",
                entry.stage()+1,seconds((float) Math.max(0,entry.duration()-entry.work())/20));
    }

    /** What an adaptation with its own ability gives (mechanics, low FPS, Infinity, darkness, hunger, existence), or null. */
    private Component ability(WheelEntry entry) {
        var mechanic=MetaMechanic.fromKey(entry.key());
        if(mechanic!=null)return Component.translatable(mechanic.nameKey()+".stage"+Math.min(4,entry.stage()));
        if(entry.key().equals(FpsAdaptation.KEY)) return Component.translatable(
                entry.finished()?(FpsAdaptation.optimizerAvailable()?"adaptationwheel.fps.reward":"adaptationwheel.fps.missing")
                        :"adaptationwheel.fps.reward_pending");
        if (entry.key().equals("condition:jujutsucraft:technique/205"))
            return Component.translatable(entry.stage()>=4?"gui.adaptationwheel.infinity_ready":"gui.adaptationwheel.infinity_learning",entry.stage());
        if (entry.key().equals(AdaptationProfile.DARK_KEY))
            return Component.translatable("gui.adaptationwheel.glow", entry.stage(), entry.stage() >= 3 ? 15 : entry.stage() * 5);
        if (entry.key().equals(AdaptationProfile.HUNGER_KEY) && entry.reduction() == 100)
            return Component.translatable("gui.adaptationwheel.fed");
        if (entry.stage() == 5 && entry.existenceEnabled()) return Component.translatable(entry.oneHitKillEnabled()?"gui.adaptationwheel.existence_done":"gui.adaptationwheel.existence_protection_only");
        return null;
    }

    /** «Что даёт»: the protection now in plain words, then what the next stages bring. */
    private List<Line> benefitLines(VisibleEntry visible) {
        List<Line> lines = new ArrayList<>();
        WheelEntry entry = visible.learned();
        String key = visible.key();
        if (entry == null) {
            lines.add(Line.of(Component.translatable(key.equals(FpsAdaptation.KEY)?"adaptationwheel.fps.discover":key.equals("condition:jujutsucraft:technique/205")?"gui.adaptationwheel.infinity_discover":"gui.adaptationwheel.discover_hint"), MUTED));
            return lines;
        }
        if (!entry.enabled() && visible.protects()) {
            lines.add(Line.of(Component.translatable("gui.adaptationwheel.benefit.off"), TEXT));
            return lines;
        }
        Component ability = ability(entry);
        int stage = Math.min(entry.stage(), 4), now = entry.reduction();
        if (ability != null) lines.add(Line.of(ability, TEXT));
        else if (key.startsWith("effect:") || key.startsWith("condition:")) {
            lines.add(Line.of(now > 0 ? Component.translatable("gui.adaptationwheel.effect_resist", now)
                    : Component.translatable("gui.adaptationwheel.benefit.effect_none"), TEXT));
            if (stage < 4) lines.add(Line.of(Component.translatable("gui.adaptationwheel.benefit.effect_next", stage + 1, entry.protections().percent(stage + 1)), MUTED));
        } else {
            String name = visible.name();
            lines.add(Line.of(now > 0 ? Component.translatable("gui.adaptationwheel.benefit.damage", name, now)
                    : Component.translatable("gui.adaptationwheel.benefit.damage_none", name), TEXT));
            if (now >= 100) lines.add(Line.of(Component.translatable("gui.adaptationwheel.benefit.immune"), MUTED));
            else if (stage < 4) {
                int next = stage + 1;
                lines.add(Line.of(next == 4 ? Component.translatable("gui.adaptationwheel.benefit.last", 4, amount(entry, 4))
                        : Component.translatable("gui.adaptationwheel.benefit.next", next, amount(entry, next), 4, amount(entry, 4)), MUTED));
            }
        }
        if (entry.stage() == 4 && entry.boss() && entry.existenceEnabled())
            lines.add(Line.of(Component.translatable("gui.adaptationwheel.benefit.existence_next"), MUTED));
        // The protection needs the wheel on the head and switched on; say so instead of promising it.
        ClientWheelState.State state = state();
        if (!key.equals(FpsAdaptation.KEY) && state != null && (!state.equipped() || !state.enabled()))
            lines.add(Line.of(Component.translatable(!state.equipped() ? "gui.adaptationwheel.benefit.inactive_unequipped"
                    : "gui.adaptationwheel.benefit.inactive_off"), WARN));
        return lines;
    }

    /** «иммунитет» for full protection, else «на N%». */
    private static Component amount(WheelEntry entry, int stage) {
        int percent = entry.protections().percent(stage);
        return percent >= 100 ? Component.translatable("gui.adaptationwheel.benefit.immunity")
                : Component.translatable("gui.adaptationwheel.benefit.percent", percent);
    }

    private void toggleEntry(String key,boolean enabled) {
        if(key.equals(FpsAdaptation.KEY)) FpsAdaptation.setEnabled(enabled);
        else WheelNetwork.setEnabled(key,enabled);
    }

    /** Opens the details of a visible row (or selects it when they are shown beside the list); also used by the screenshot probe. */
    void openRow(int index) {
        if (index < 0 || index >= visibleEntries.size()) return;
        String key = visibleEntries.get(index).key();
        if (layout.split) select(key, true);
        else minecraft.setScreen(new DetailScreen(key));
    }

    /** The visible row of a key, or -1; for the screenshot probe. */
    int rowOf(String key) {
        for (int i = 0; i < visibleEntries.size(); i++) if (visibleEntries.get(i).key().equals(key)) return i;
        return -1;
    }

    private int rowAt(double mouseX, double mouseY) {
        int top = listTop();
        if (mouseX < layout.listLeft || mouseX >= listRight() || mouseY < top || mouseY >= layout.listBottom) return -1;
        return rows.at(Mth.floor(mouseY - top + scroll));
    }

    private int listRight() { return layout.listRight - (maxScroll > 0 ? 6 : 0); }

    /** Clicks right of this edge hit a row's switch. */
    private int switchEdge() { return listRight() - 32; }

    private boolean inSearch(double mouseX, double mouseY) {
        return mouseX >= layout.searchX && mouseX < layout.searchX + layout.searchWidth && mouseY >= layout.searchY && mouseY < layout.searchY + 18;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        panel(g, layout.left, layout.top, layout.right, layout.bottom);
        ClientWheelState.State state = state();
        renderHeader(g, state);
        g.fill(layout.searchX, layout.searchY, layout.searchX + layout.searchWidth, layout.searchY + 18, search.isFocused() ? CARD_HOVER : CARD);
        g.fill(layout.searchX, layout.searchY + 17, layout.searchX + layout.searchWidth, layout.searchY + 18, search.isFocused() ? GOLD : 0xFF4A4050);
        if (layout.split) g.fill(layout.detailLeft - 6, layout.detailTop, layout.detailLeft - 5, layout.detailBottom, RULE);
        boolean modal = picking || fixing();
        int x = modal ? -1 : mouseX, y = modal ? -1 : mouseY;
        List<WheelSummary.Item> items = items(state, partialTick);
        firstKey = WheelSummary.first(items);
        super.render(g, x, y, partialTick);
        renderBanner(g, state);
        renderSummaryRow(g, state, items, onSummaryRow(x, y));
        renderList(g, state, x, y, partialTick);
        if (summarySelected() && summaryView != null) summaryView.render(g, x, y, state, items);
        else if (detail != null) detail.render(g, x, y, partialTick);
        renderStatus(g, x, y);
        if (picking) renderCategories(g, mouseX, mouseY);
        if (fixing()) detail.renderPicker(g);
    }

    /** The window that corrects a row's category is open over the details. */
    private boolean fixing() { return detail != null && detail.picking(); }

    /** The label that tells whether the wheel works now: worn and switched on, switched off, or not on the head. Returns its x. */
    private int stateChip(GuiGraphics g, ClientWheelState.State state, int closeX, int y) {
        boolean off = state != null && !state.enabled(), worn = state != null && state.equipped();
        Component wheelState = Component.translatable(off ? "gui.adaptationwheel.chip.off" : worn ? "gui.adaptationwheel.chip.working" : "gui.adaptationwheel.chip.not_worn");
        int chipX = closeX - 6 - font.width(wheelState) - 8;
        chip(g, font, wheelState, chipX, y, off ? MUTED : worn ? GOOD : WARN);
        return chipX;
    }

    private void renderHeader(GuiGraphics g, ClientWheelState.State state) {
        boolean compact = layout.compact;
        g.renderItem(wheelIcon, layout.left + 9, layout.top + (compact ? 3 : 8));
        Component title = Component.translatable("gui.adaptationwheel.title");
        int textY = layout.top + (compact ? 7 : 8);
        g.drawString(font, title, layout.left + 30, textY, GOLD, false);
        int chipX = stateChip(g, state, layout.closeX, layout.top + (compact ? 5 : 9));
        // Short windows put the summary beside the title instead of under it.
        int summaryX = compact ? layout.left + 30 + font.width(title) + 8 : layout.left + 30, summaryY = compact ? textY : layout.top + 19;
        Component summary = summary(state);
        int room = chipX - 8 - summaryX;
        if (font.width(summary) <= room) g.drawString(font, summary, summaryX, summaryY, MUTED, false);
        else g.drawString(font, fit(font, summary.getString(), room), summaryX, summaryY, MUTED, false);
    }

    /** Numbers of the summary stand out from its words. */
    private static Component number(long value) { return Component.literal(String.valueOf(value)).withStyle(s -> s.withColor(TEXT)); }

    private Component summary(ClientWheelState.State state) {
        if (catalogue) {
            long met = allEntries().values().stream().filter(e -> e.learned() != null).count();
            return Component.translatable("gui.adaptationwheel.catalogue_summary", number(allEntries().size()), number(met));
        }
        int[] counts = counts(state);
        return Component.translatable("gui.adaptationwheel.summary", number(counts[0]), number(counts[1]), number(counts[2]));
    }

    /** While the wheel is not worn, an orange strip over the list says that stages and protection are paused. */
    private void renderBanner(GuiGraphics g, ClientWheelState.State state) {
        if (bannerHeight() == 0) return;
        int top = layout.listTop, h = layout.compact ? 15 : 17;
        g.fill(layout.listLeft, top, layout.listRight, top + h, tint(WARN));
        g.fill(layout.listLeft, top, layout.listLeft + 2, top + h, WARN);
        parts(g, font, List.of(part(Component.translatable("gui.adaptationwheel.banner.unequipped"), WARN),
                        part(Component.translatable("gui.adaptationwheel.banner.unequipped_rest"), TEXT)),
                layout.listLeft + 8, top + (layout.compact ? 4 : 5), layout.listRight - layout.listLeft - 14);
    }

    /** One line at the bottom: what a click does where the mouse is, or the keys in frames, the most useful first. */
    private void renderStatus(GuiGraphics g, int mouseX, int mouseY) {
        int row = rowAt(mouseX, mouseY), x = layout.left + 10, room = look.getX() - 10 - x;
        if (row >= 0 && visibleEntries.get(row).learned() != null && mouseX >= switchEdge()) {
            g.drawString(font, fit(font, Component.translatable("gui.adaptationwheel.status.toggle").getString(), room), x, layout.statusY, MUTED, false);
            return;
        }
        VisibleEntry current = layout.split ? allEntries().get(selectedKey) : null;
        WheelEntry fixable = current != null && current.learned() != null && current.learned().fixable() ? current.learned() : null;
        String[] keys = fixable != null ? new String[]{"arrows", "select", "home", "summary", "space", "toggle", "k", fixable.manual() ? "restore" : "fix", "letters", "search", "esc", "close"}
                : layout.split ? new String[]{"arrows", "select", "home", "summary", "space", "toggle", "letters", "search", "esc", "close"}
                : new String[]{"arrows", "select", "enter", "details", "space", "toggle", "letters", "search"};
        String[] text = new String[keys.length];
        int[] widths = new int[keys.length / 2];
        for (int i = 0; i < keys.length; i++) text[i] = Component.translatable("gui.adaptationwheel.keys." + keys[i]).getString();
        for (int i = 0; i < widths.length; i++) widths[i] = keycapWidth(font, text[i * 2]) + 4 + font.width(text[i * 2 + 1]);
        int shown = MenuLayout.hintsThatFit(widths, 10, room);
        for (int i = 0; i < shown; i++) {
            x += keycap(g, font, text[i * 2], x, layout.statusY) + 4;
            g.drawString(font, text[i * 2 + 1], x, layout.statusY, MUTED, false);
            x += font.width(text[i * 2 + 1]) + 10;
        }
    }

    private void renderList(GuiGraphics g, ClientWheelState.State state, int mouseX, int mouseY, float partialTick) {
        int left = layout.listLeft, top = listTop(), bottom = layout.listBottom, visible = bottom - top;
        maxScroll = Math.max(0, rows.height() - visible);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        int right = listRight(), hovered = rowAt(mouseX, mouseY), selected = selectedIndex();
        if (visibleEntries.isEmpty()) {
            Component empty = Component.translatable(!query.isBlank() || category != null ? "gui.adaptationwheel.no_matches"
                    : catalogue ? "gui.adaptationwheel.catalogue_loading" : "gui.adaptationwheel.empty");
            int y = top + 20;
            for (FormattedCharSequence line : font.split(empty, right - left - 20)) {
                g.drawCenteredString(font, line, (left + right) / 2, y, MUTED);
                y += font.lineHeight + 2;
            }
            return;
        }
        g.enableScissor(left, top, right, bottom);
        for (int i = 0; i < rows.heads(); i++) {
            int y = top + rows.headTop(i) - scroll;
            if (y + rows.head <= top || y >= bottom) continue;
            renderHeading(g, i, left, y, right);
        }
        for (int index = rows.firstBelow(scroll - rows.row); index < visibleEntries.size(); index++) {
            int y = top + rows.top(index) - scroll;
            if (y >= bottom) break;
            renderRow(g, visibleEntries.get(index), left, y, right, index == hovered, index == selected, mouseX, state, partialTick);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int thumb = Math.max(16, visible * visible / rows.height());
            int thumbTop = top + (visible - thumb) * scroll / maxScroll, x = layout.listRight - 3;
            g.fill(x, top, x + 3, bottom, TRACK);
            g.fill(x, thumbTop, x + 3, thumbTop + thumb, GOLD);
        }
    }

    /** «Изучаются 2 ———»: a group's name, its count and a rule to the right edge. */
    private void renderHeading(GuiGraphics g, int head, int left, int y, int right) {
        int textY = y + (layout.compact ? 2 : 3);
        String label = Component.translatable("gui.adaptationwheel.group." + GROUPS[rows.headGroup(head)]).getString();
        String count = String.valueOf(rows.headCount(head));
        g.drawString(font, label, left + 2, textY, MUTED, false);
        int countX = left + 2 + font.width(label) + 3;
        g.drawString(font, count, countX, textY, FAINT, false);
        g.fill(countX + font.width(count) + 5, textY + 4, right - 2, textY + 5, RULE);
    }

    private void renderRow(GuiGraphics g, VisibleEntry entry, int left, int y, int right, boolean hovered, boolean selected, int mouseX,
                           ClientWheelState.State state, float partialTick) {
        WheelEntry learned = entry.learned();
        boolean on = learned != null && learned.enabled(), compact = layout.compact;
        int color = entry.color(), height = layout.row - 2, middle = y + (compact ? 6 : 7);
        g.fill(left, y, right, y + height, selected ? SELECTED : hovered ? CARD_HOVER : CARD);
        if (selected) g.renderOutline(left, y, right - left, height, dim(GOLD));
        int tileSize = compact ? 14 : 15, tileX = left + 3;
        tile(g, entry.icon(), tileX, y + (height - tileSize) / 2, tileSize, color, on || learned == null);
        int x = right - 6;
        if (learned != null) {
            toggle(g, x - 22, middle - 1, on, true);
            if (hovered && mouseX >= switchEdge()) g.renderOutline(x - 23, middle - 2, 24, 12, GOLD);
            x -= 30;
        }
        if (entry.protects() && learned != null) {
            String percent = learned.reduction() + "%";
            g.drawString(font, percent, x - font.width(percent), middle, learned.reduction() >= 100 ? GOOD : on ? TEXT : MUTED, false);
            x -= Math.max(font.width("100%"), font.width(percent)) + 6;
        }
        int total = learned == null ? 4 : learned.totalStages(), earned = learned == null ? 0 : Math.min(learned.stage(), total);
        float running = learned == null ? 0 : progress(learned, state, partialTick);
        int pipsWidth = total * 7 - 2;
        pips(g, x - pipsWidth, middle, total, earned, learned != null && learned.pending() ? running : 0, on ? color : dim(color));
        x -= pipsWidth + 8;
        int textX = tileX + tileSize + 5, textWidth = x - textX, nameY = y + (compact ? 1 : 3), lineY = y + (compact ? 10 : 13);
        int nameColor = learned == null ? MUTED : on ? TEXT : MUTED;
        if (entry.key().equals(firstKey))
            parts(g, font, List.of(part(entry.name(), nameColor), part(" · " + Component.translatable("gui.adaptationwheel.first").getString(), GOLD)), textX, nameY, textWidth);
        else g.drawString(font, fit(font, entry.name(), textWidth), textX, nameY, nameColor, false);
        parts(g, font, rowLine(entry, state, partialTick), textX, lineY, textWidth);
        if (learned != null && learned.pending())
            bar(g, left, y + height - 2, right - left, 2, running, on && moving(learned, state) ? color : dim(color));
    }

    /** The learned adaptations as the summary sees them, in the list's group order. */
    private List<WheelSummary.Item> items(ClientWheelState.State state, float partialTick) {
        List<WheelSummary.Item> items = new ArrayList<>();
        for (VisibleEntry visible : allEntries().values()) {
            WheelEntry entry = visible.learned();
            if (entry == null) continue;
            int total = entry.totalStages(), group = group(visible);
            boolean running = group == WheelSummary.RUNNING;
            items.add(new WheelSummary.Item(visible.key(), visible.profile(), group, Math.min(entry.stage(), total), total,
                    running ? progress(entry, state, partialTick) : 0, running && moving(entry, state) ? remainingSeconds(entry, state, partialTick) : -1,
                    entry.reduction(), visible.protects()));
        }
        items.sort(Comparator.comparingInt(WheelSummary.Item::group));
        return items;
    }

    /** An adaptation's name by key, also for one that left the list (the journal keeps old keys). */
    private String nameOf(String key, String translationKey) {
        VisibleEntry entry = allEntries().get(key);
        if (entry != null) return entry.name();
        return I18n.exists(translationKey) ? Component.translatable(translationKey).getString() : key;
    }

    private int colorOf(String key, AdaptationProfile profile) {
        return key.equals(FpsAdaptation.KEY) ? FPS : profile == null ? GOLD : profile.color;
    }

    /** The small wheel picture: one fill per run of one colour, batched. */
    private static void wheel(GuiGraphics g, int x, int y, boolean faded) {
        int[] runs = faded ? WHEEL_RUNS_FADED : WHEEL_RUNS;
        g.drawManaged(() -> {
            for (int i = 0; i < runs.length; i += 4) g.fill(x + runs[i], y + runs[i + 1], x + runs[i] + runs[i + 2], y + runs[i + 1] + 1, runs[i + 3]);
        });
    }

    /** Russian form of a word after a number: key.one, key.few or key.many. */
    private static Component plural(String key, int count, Object... args) {
        return Component.translatable(key + "." + LearningLayout.plural(count).name().toLowerCase(Locale.ROOT), args);
    }

    /** «за эту игру: 4 этапа пройдено · 1 завершена · 2 новых», numbers bright; short: «4 этапа · 1 завершена». */
    private List<Part> session(boolean dense) {
        int[] totals = JOURNAL.totals();
        List<Part> parts = new ArrayList<>();
        parts.add(part(Component.translatable("gui.adaptationwheel.summary.session"), MUTED));
        if (totals[0] + totals[1] + totals[2] == 0) {
            parts.add(part(Component.translatable("gui.adaptationwheel.summary.session.none"), MUTED));
            return parts;
        }
        parts.add(part(String.valueOf(totals[0]), TEXT));
        parts.add(part(plural(dense ? "gui.adaptationwheel.summary.session.stages_short" : "gui.adaptationwheel.summary.session.stages", totals[0]), MUTED));
        parts.add(part(" · ", MUTED));
        parts.add(part(String.valueOf(totals[1]), TEXT));
        parts.add(part(plural("gui.adaptationwheel.summary.session.finished", totals[1]), MUTED));
        if (!dense) {
            parts.add(part(" · ", MUTED));
            parts.add(part(String.valueOf(totals[2]), TEXT));
            parts.add(part(plural("gui.adaptationwheel.summary.session.new", totals[2]), MUTED));
        }
        return parts;
    }

    private static int width(Font font, List<Part> parts) {
        int width = 0;
        for (Part part : parts) width += font.width(part.text());
        return width;
    }

    /** «Сводка колеса · 8 из 14 завершено · дальше: Горение через 9.5 с» with a strip of marks, one per adaptation. */
    private void renderSummaryRow(GuiGraphics g, ClientWheelState.State state, List<WheelSummary.Item> items, boolean hovered) {
        int left = layout.listLeft, right = layout.listRight, y = summaryTop(), height = layout.row - 2;
        boolean selected = summarySelected(), compact = layout.compact, worn = state == null || state.equipped();
        g.fill(left, y, right, y + height, selected ? SELECTED : hovered ? CARD_HOVER : CARD);
        if (selected) g.renderOutline(left, y, right - left, height, dim(GOLD));
        wheel(g, left + 2, y + (height - 16) / 2, !worn);
        int middle = y + (compact ? 6 : 7), x = right - 6;
        g.drawString(font, "›", x - 3, middle, selected ? GOLD : FAINT, false);
        x -= 10;
        List<WheelSummary.Item> marked = WheelSummary.marks(items);
        int count = Math.min(marked.size(), Math.max(0, (right - left) / 30));
        int marks = x - count * 4 + 1;
        for (int i = 0; i < count; i++) {
            WheelSummary.Item item = marked.get(i);
            int l = marks + i * 4, filled = Math.round(7 * item.fill());
            g.fill(l, middle, l + 3, middle + 7, TRACK);
            int color = WheelSummary.markColor(item.group());
            if (filled > 0) g.fill(l, middle + 7 - filled, l + 3, middle + 7, worn ? color : dim(color));
        }
        int textX = left + 21, textWidth = marks - 8 - textX;
        g.drawString(font, fit(font, Component.translatable("gui.adaptationwheel.summary.title").getString(), textWidth), textX, y + (compact ? 1 : 3), selected ? GOLD : TEXT, false);
        int[] counts = WheelSummary.counts(items);
        List<Part> line = new ArrayList<>();
        line.add(part(Component.translatable("gui.adaptationwheel.summary.done_of", counts[WheelSummary.DONE], counts[4]), MUTED));
        line.add(part(" · ", FAINT));
        line.add(nextPart(state, items));
        parts(g, font, line, textX, y + (compact ? 10 : 13), textWidth);
    }

    /** What the summary row says after the count: the filter, a pause, or the stage that ends next. */
    private Part nextPart(ClientWheelState.State state, List<WheelSummary.Item> items) {
        if (groupFilter >= 0) return part(Component.translatable("gui.adaptationwheel.summary.row_filter",
                Component.translatable("gui.adaptationwheel.group." + GROUPS[groupFilter])), GOLD);
        if (state != null && !state.equipped()) return part(Component.translatable("gui.adaptationwheel.summary.row_paused"), WARN);
        if (state != null && !state.enabled()) return part(Component.translatable("gui.adaptationwheel.summary.row_off"), WARN);
        List<WheelSummary.Item> next = WheelSummary.next(items);
        if (!next.isEmpty() && next.get(0).moving()) return part(Component.translatable("gui.adaptationwheel.summary.row_next",
                nameOf(next.get(0).key(), ""), seconds(shownSeconds(next.get(0)))), GOLD);
        if (!next.isEmpty()) return part(Component.translatable("gui.adaptationwheel.summary.row_paused"), WARN);
        return part(Component.translatable("gui.adaptationwheel.summary.row_idle"), MUTED);
    }

    private void renderCategories(GuiGraphics g, int mouseX, int mouseY) {
        int[] box = categoryBox(), counts = categoryCounts();
        // Text is batched: draw what is queued now and lift the picker above it, or the search hint and the category
        // button's label show through where the picker covers them (compact windows).
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(0, 0, width, height, 0x88000000);
        panel(g, box[0], box[1], box[0] + box[2], box[1] + box[3]);
        g.drawString(font, Component.translatable("gui.adaptationwheel.choose_category"), box[0] + 8, box[1] + 7, GOLD, false);
        int columns = box[4], cell = (box[2] - 16 - (columns - 1) * 4) / columns;
        AdaptationProfile[] profiles = AdaptationProfile.values();
        for (int i = 0; i <= profiles.length; i++) {
            AdaptationProfile profile = i == 0 ? null : profiles[i - 1];
            int x = box[0] + 8 + (i % columns) * (cell + 4), y = box[1] + 22 + (i / columns) * 18;
            boolean hot = mouseX >= x && mouseX < x + cell && mouseY >= y && mouseY < y + 16, selected = profile == category, none = counts[i] == 0;
            g.fill(x, y, x + cell, y + 16, selected ? 0xFF4C3B1B : hot ? CARD_HOVER : CARD);
            int color = profile == null ? GOLD : profile.color;
            if (profile == null) g.fill(x + 4, y + 5, x + 10, y + 11, none ? dim(color) : color);
            else icon(g, CategoryIcons.of(profile), x + 4, y + 4, none ? dim(color) : color);
            String count = String.valueOf(counts[i]);
            String name = Component.translatable(profile == null ? "gui.adaptationwheel.all_categories" : profile.translationKey()).getString();
            g.drawString(font, fit(font, name, cell - 24 - font.width(count)), x + 14, y + 4, selected ? 0xFFFFF0C8 : none ? FAINT : TEXT, false);
            g.drawString(font, count, x + cell - 5 - font.width(count), y + 4, none ? FAINT : MUTED, false);
        }
        g.pose().popPose();
    }

    /** Entries each category would show in the current tab and search; index 0 is all of them. */
    private int[] categoryCounts() {
        AdaptationProfile[] profiles = AdaptationProfile.values();
        int[] counts = new int[profiles.length + 1];
        String match = query.strip().toLowerCase(Locale.ROOT);
        for (VisibleEntry entry : allEntries().values()) if (matches(entry, match, true)) {
            counts[0]++;
            counts[entry.profile().ordinal() + 1]++;
        }
        return counts;
    }

    /** x, y, width, height and columns of the category picker. */
    private int[] categoryBox() {
        int available = layout.right - layout.left - 20, columns = available >= 3 * 180 ? 3 : 2, count = AdaptationProfile.values().length + 1;
        int boxWidth = Math.min(available, columns * 180), rows = (count + columns - 1) / columns;
        int boxHeight = 30 + rows * 18;
        return new int[]{(width - boxWidth) / 2, Math.max(layout.top + 20, (height - boxHeight) / 2), boxWidth, boxHeight, columns};
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (picking) {
            int[] box = categoryBox();
            int columns = box[4], cell = (box[2] - 16 - (columns - 1) * 4) / columns;
            for (int i = 0; i <= AdaptationProfile.values().length; i++) {
                int x = box[0] + 8 + (i % columns) * (cell + 4), y = box[1] + 22 + (i / columns) * 18;
                if (mouseX >= x && mouseX < x + cell && mouseY >= y && mouseY < y + 16) {
                    category = i == 0 ? null : AdaptationProfile.values()[i - 1];
                    scroll = 0;
                    break;
                }
            }
            picking = false;
            tick();
            return true;
        }
        if (fixing()) {
            detail.pickerClick(mouseX, mouseY);
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            // Keys go back to the list after a click on a button; only the search keeps them.
            if (getFocused() != search) setFocused(null);
            return true;
        }
        // The whole search field takes the click, not only the line of text inside it.
        if (inSearch(mouseX, mouseY)) {
            setFocused(search);
            return true;
        }
        setFocused(null);
        if (summarySelected() && summaryView != null) {
            int group = summaryView.counterAt(mouseX, mouseY);
            if (group >= 0) { filterGroup(group); return true; }
        } else if (detail != null && detail.click(mouseX, mouseY)) return true;
        if (onSummaryRow(mouseX, mouseY)) {
            selectSummary();
            if (!layout.split) minecraft.setScreen(new SummaryScreen());
            return true;
        }
        int row = rowAt(mouseX, mouseY);
        if (row < 0) return false;
        VisibleEntry entry = visibleEntries.get(row);
        select(entry.key(), false);
        if (entry.learned() != null && mouseX >= switchEdge()) toggleEntry(entry.key(), !entry.learned().enabled());
        else if (!layout.split) minecraft.setScreen(new DetailScreen(entry.key()));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (picking || fixing()) return true;
        if (detail != null && detail.contains(mouseX, mouseY)) return summarySelected() || detail.mouseScrolled(delta);
        scroll = Mth.clamp(scroll - (int) (delta * layout.row * 2), 0, maxScroll);
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (fixing()) detail.pickerHover(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        // The menu key closes the menu again (also over the category picker) unless the player is typing in the search.
        // It comes before type-to-search, so a letter key bound to the menu (default J) closes rather than searches.
        boolean menuKey = !search.isFocused() && ClientEvents.MENU.matches(key, scanCode);
        if (picking) {
            if (key == GLFW.GLFW_KEY_ESCAPE) picking = false;
            else if (menuKey) onClose();
            return true;
        }
        if (fixing()) {
            if (menuKey) onClose();
            else detail.pickerKey(key);
            return true;
        }
        if (key == GLFW.GLFW_KEY_L && (hasControlDown() || (modifiers & GLFW.GLFW_MOD_CONTROL) != 0)) {
            minecraft.setScreen(new LearningScreen(this));
            return true;
        }
        if (key == GLFW.GLFW_KEY_K && (hasControlDown() || (modifiers & GLFW.GLFW_MOD_CONTROL) != 0)) {
            minecraft.setScreen(new WheelSkinScreen(this, null));
            return true;
        }
        if (key == GLFW.GLFW_KEY_F && hasControlDown()) {
            setFocused(search);
            return true;
        }
        if (search.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                // Esc clears the search first, then leaves it; the next Esc closes the menu.
                if (!query.isEmpty()) search.setValue("");
                else setFocused(null);
                return true;
            }
            // Arrows and Enter leave the search for the list. Vanilla would move the focus to the switch above the
            // search, and Space would then turn off the whole wheel instead of the selected row.
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                setFocused(null);
                // A selection kept from before the search may be scrolled away; Enter must not open a hidden row.
                int index = selectedIndex();
                if (index < 0 || !rowShown(index)) selectRow(0);
                return true;
            }
            return super.keyPressed(key, scanCode, modifiers);
        }
        if (menuKey) { onClose(); return true; }
        // K acts on the row whose details are shown; on anything else it starts a search like any letter.
        if (key == GLFW.GLFW_KEY_K && detail != null && !summarySelected() && detail.fixKey()) return true;
        // Esc first shows every group again after a counter of the summary left only one.
        if (key == GLFW.GLFW_KEY_ESCAPE && groupFilter >= 0 && getFocused() == null) { filterGroup(-1); return true; }
        int page = rows.page(listHeight());
        boolean listKey = key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_PAGE_UP
                || key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END;
        // List keys take the focus back from a button Tab moved it to, so Enter and Space act on the selected row.
        if (listKey) setFocused(null);
        switch (key) {
            case GLFW.GLFW_KEY_UP -> moveSelection(-1);
            case GLFW.GLFW_KEY_DOWN -> moveSelection(1);
            case GLFW.GLFW_KEY_PAGE_UP -> moveSelection(-page);
            case GLFW.GLFW_KEY_PAGE_DOWN -> moveSelection(page);
            case GLFW.GLFW_KEY_HOME -> selectSummary();
            case GLFW.GLFW_KEY_END -> selectRow(visibleEntries.size() - 1);
            // Vanilla moves the focus between buttons with these too; Tab still does.
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> { }
            default -> {
                // Enter and Space act on the selected row unless Tab moved the focus to a button.
                if (summarySelected() && getFocused() == null && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE)) {
                    if (key != GLFW.GLFW_KEY_SPACE && !layout.split) minecraft.setScreen(new SummaryScreen());
                    return true;
                }
                int index = selectedIndex();
                if (getFocused() != null || index < 0 || key != GLFW.GLFW_KEY_ENTER && key != GLFW.GLFW_KEY_KP_ENTER && key != GLFW.GLFW_KEY_SPACE)
                    return super.keyPressed(key, scanCode, modifiers);
                VisibleEntry entry = visibleEntries.get(index);
                if (key == GLFW.GLFW_KEY_SPACE) {
                    if (entry.learned() != null) toggleEntry(entry.key(), !entry.learned().enabled());
                } else if (!layout.split) minecraft.setScreen(new DetailScreen(entry.key()));
            }
        }
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (detail != null) detail.released(key);
        return super.keyReleased(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        // The letter of the K that just acted on the details is not a search.
        if (detail != null && (detail.picking() || detail.keyHeld())) return true;
        // Typing anywhere starts a search (the menu key's own letter closed the menu in keyPressed already).
        if (!picking && !search.isFocused() && getFocused() == null && character > ' ' && character != 127) {
            setFocused(search);
            return search.charTyped(character, modifiers);
        }
        return super.charTyped(character, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }

    /**
     * One adaptation's details, drawn into a rectangle of the menu or of its own screen: the name, a «Сейчас» card with
     * the protection now and the running stage, the stage track, «Что даёт», a short analysis, and the technical lines
     * folded into «Подробности для настройки».
     */
    private final class DetailView {
        private static final int CARD_HEIGHT = 48, FIX_HEIGHT = 14;
        /** Below this the analyzer's confidence shows in orange and its button asks to be pressed. */
        private static final int UNSURE = 60;
        private final int x0, y0, x1, y1;
        private final boolean back;
        private final Switch toggle;
        private final Flat utility;
        private String key;
        private int scrollOffset, maxScroll;
        /** Where the folded block's header was drawn last frame, for clicks; -1 when it was not on screen. */
        private int settingsY = -1, contentTop;
        /** The button of the «Категория» row as drawn last frame (fixY -1: not on screen), and where that row sits in the content. */
        private int fixX, fixY = -1, fixWidth, categoryTop = -1;
        /** The picker's cursor over {@link AdaptationProfile#values()}; -1 while it is closed. */
        private int cursor = -1;
        private boolean revealCategory, keyHeld;

        private DetailView(Consumer<AbstractWidget> add, int x0, int y0, int x1, int y1, String key, Runnable onBack) {
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1; this.key = key;
            back = onBack != null;
            Component label = Component.translatable("gui.adaptationwheel.switch.entry");
            int toggleWidth = Math.min(140, font().width(label) + 40);
            toggle = new Switch(x1 - toggleWidth, y0, toggleWidth, label, this::enabled, button -> {
                VisibleEntry entry = entry();
                if (FpsAdaptation.KEY.equals(this.key)) FpsAdaptation.setEnabled(!FpsAdaptation.enabled());
                else if (entry != null && entry.learned() != null) toggleEntry(this.key, !entry.learned().enabled());
            });
            utility = new Flat(x1 - 170, y0 + 18, 170, 16, Component.empty(), button -> {
                var m = MetaMechanic.fromKey(this.key);
                if (m == MetaMechanic.POCKET) { MetaNetwork.action("compact"); Minecraft.getInstance().setScreen(null); }
                if (m == MetaMechanic.BORDER) { MetaNetwork.action("border"); Minecraft.getInstance().setScreen(null); }
            }).accent();
            add.accept(toggle);
            add.accept(utility);
            if (back) add.accept(new IconButton(x0, y0 + 1, IconButton.BACK, Component.translatable("gui.back"), button -> onBack.run()));
            tick();
        }

        private static Font font() { return Minecraft.getInstance().font; }
        private VisibleEntry entry() { return key == null ? null : allEntries().get(key); }
        private boolean enabled() {
            if (FpsAdaptation.KEY.equals(key)) return FpsAdaptation.enabled();
            VisibleEntry entry = entry();
            return entry != null && entry.learned() != null && entry.learned().enabled();
        }
        boolean contains(double x, double y) { return x >= x0 && x < x1 && y >= y0 && y < y1; }
        boolean mouseScrolled(double delta) {
            scrollOffset = Mth.clamp(scrollOffset - (int) (delta * 22), 0, maxScroll);
            return true;
        }
        void scrollBy(int amount) { scrollOffset = Mth.clamp(scrollOffset + amount, 0, maxScroll); }

        /** A click on the folded block's header opens or closes it. */
        boolean click(double x, double y) {
            if (onFix(x, y)) { fix(); return true; }
            if (settingsY < 0 || x < x0 || x >= x1 || y < settingsY || y >= settingsY + 16 || y < contentTop || y >= y1) return false;
            settingsOpen = !settingsOpen;
            return true;
        }

        private boolean onFix(double x, double y) {
            return fixY >= 0 && x >= fixX && x < fixX + fixWidth && y >= fixY && y < fixY + FIX_HEIGHT && y >= contentTop && y < y1;
        }

        /** The picker on a row the analyzer decides, the analyzer back on a corrected one; false on a row without a category to correct. */
        private boolean fix() {
            VisibleEntry entry = entry();
            WheelEntry learned = entry == null ? null : entry.learned();
            if (learned == null || !learned.fixable()) return false;
            settingsOpen = true;
            revealCategory = true;
            if (learned.manual()) WheelNetwork.fixCategory(key, null);
            else cursor = learned.profile().ordinal();
            return true;
        }

        /** K, once per press: holding it must not give the analyzer back and open the picker in one go. */
        boolean fixKey() {
            if (keyHeld) return true;
            return keyHeld = fix();
        }

        void released(int key) { if (key == GLFW.GLFW_KEY_K) keyHeld = false; }
        boolean keyHeld() { return keyHeld; }
        boolean picking() { return cursor >= 0; }

        private CategoryGrid grid() { return new CategoryGrid(x0, y0, x1, y1, y0 + 36, AdaptationProfile.values().length); }

        void pickerHover(double x, double y) {
            int index = grid().at(x, y);
            if (index >= 0) cursor = index;
        }

        /** A click on a category takes it, a click outside the window closes it. */
        void pickerClick(double x, double y) {
            CategoryGrid grid = grid();
            int index = grid.at(x, y);
            if (index >= 0) { cursor = index; accept(); }
            else if (!grid.contains(x, y)) cursor = -1;
        }

        void pickerKey(int pressed) {
            int count = AdaptationProfile.values().length;
            switch (pressed) {
                case GLFW.GLFW_KEY_LEFT -> cursor = CategoryGrid.move(cursor, -1, 0, count);
                case GLFW.GLFW_KEY_RIGHT -> cursor = CategoryGrid.move(cursor, 1, 0, count);
                case GLFW.GLFW_KEY_UP -> cursor = CategoryGrid.move(cursor, 0, -1, count);
                case GLFW.GLFW_KEY_DOWN -> cursor = CategoryGrid.move(cursor, 0, 1, count);
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> accept();
                case GLFW.GLFW_KEY_ESCAPE -> cursor = -1;
                default -> { }
            }
        }

        /** The analyzer's own category is no correction: the window just closes. */
        private void accept() {
            VisibleEntry entry = entry();
            AdaptationProfile chosen = AdaptationProfile.values()[cursor];
            if (entry != null && entry.learned() != null && chosen != entry.learned().profile()) WheelNetwork.fixCategory(key, chosen);
            cursor = -1;
        }

        void show(String key) {
            if (key != null && key.equals(this.key)) return;
            this.key = key;
            scrollOffset = 0;
            cursor = -1;
            tick();
        }

        void tick() {
            VisibleEntry entry = entry();
            var mechanic = key == null ? null : MetaMechanic.fromKey(key);
            utility.visible = mechanic == MetaMechanic.POCKET && MetaClient.active(mechanic) && MetaClient.stage(mechanic) >= 2
                    || mechanic == MetaMechanic.BORDER && MetaClient.full(mechanic);
            if (utility.visible) {
                Component text = Component.translatable(mechanic == MetaMechanic.BORDER ? "meta.adaptationwheel.border.cross" : "meta.adaptationwheel.pocket.compact");
                int width = Math.min(Math.max(90, font().width(text) + 16), (x1 - x0) / 2);
                utility.setMessage(text);
                utility.setWidth(width);
                utility.setX(x1 - width);
            }
            toggle.visible = entry != null && (entry.learned() != null || FpsAdaptation.KEY.equals(key));
        }

        /** Sections under the stage track; the folded technical block is drawn after them. */
        private List<Section> sections(VisibleEntry entry) {
            List<Section> sections = new ArrayList<>();
            WheelEntry learned = entry.learned();
            sections.add(new Section(Component.translatable("gui.adaptationwheel.section.benefit"), benefitLines(entry)));
            List<Line> how = new ArrayList<>();
            var mechanic=MetaMechanic.fromKey(key);
            if(mechanic!=null){
                how.add(Line.of(Component.translatable(mechanic.hintKey()), TEXT));
                for(Component line:OutsideClient.details(mechanic))how.add(Line.of(line, MUTED));
            }
            if(key.equals(FpsAdaptation.KEY)) {
                how.add(Line.of(Component.translatable("adaptationwheel.fps.explained"), TEXT));
                how.add(Line.of(Component.translatable("adaptationwheel.fps.sample",FpsAdaptation.measuredFps()), MUTED));
                how.add(Line.of(Component.translatable(FpsAdaptation.optimizerAvailable()?"adaptationwheel.fps.available":"adaptationwheel.fps.missing"), MUTED));
            }
            if(key.equals("condition:jujutsucraft:technique/205"))how.add(Line.of(Component.translatable("gui.adaptationwheel.infinity_explained"), TEXT));
            if (!how.isEmpty()) sections.add(new Section(Component.translatable("gui.adaptationwheel.section.mechanic"), how));
            if (learned != null && learned.analysis().present()) {
                // The verdict, how sure the analyzer is and why; what it observed goes to the folded block.
                AnalysisInfo info = learned.analysis();
                int confidence = info.confidence();
                String reasonKey = "analysis.adaptationwheel.reason." + info.reason();
                List<Line> analysis = new ArrayList<>();
                analysis.add(new Line(Component.translatable(learned.manual() ? "gui.adaptationwheel.analysis.verdict_manual" : "gui.adaptationwheel.analysis.verdict", Component.translatable(learned.verdict().translationKey()), confidence),
                        TEXT, confidence / 100f, confidence >= 85 ? GOOD : confidence >= 60 ? GOLD : WARN));
                if (info.fromTable()) analysis.addAll(originLines(learned, info));
                analysis.add(Line.of(Component.translatable("gui.adaptationwheel.analysis.reason", Component.translatable(I18n.exists(reasonKey) ? reasonKey : "analysis.adaptationwheel.reason.fallback.profile")), MUTED));
                sections.add(new Section(Component.translatable("gui.adaptationwheel.analysis.title"), analysis));
            }
            return sections;
        }

        /** «Откуда знание»: two framed rows when the compatibility table decided the verdict; shorter in the narrow menu. */
        private List<Line> originLines(WheelEntry learned, AnalysisInfo info) {
            boolean narrow = x1 - x0 < 260;
            Component mod = Component.literal(info.originMod()).withStyle(s -> s.withColor(TEXT & 0xFFFFFF));
            Component first = Component.translatable(narrow ? "gui.adaptationwheel.origin.label_short" : "gui.adaptationwheel.origin.label").withStyle(s -> s.withColor(ORIGIN_LABEL & 0xFFFFFF))
                    .append(Component.translatable(learned.manual() ? "gui.adaptationwheel.origin.manual" : narrow ? "gui.adaptationwheel.origin.table_short" : "gui.adaptationwheel.origin.table").withStyle(s -> s.withColor(TEXT & 0xFFFFFF)));
            Component second;
            if (learned.manual() && learned.analyzerProfile() != null)
                second = Component.translatable(narrow ? "gui.adaptationwheel.origin.manual_mod_short" : "gui.adaptationwheel.origin.manual_mod", mod, Component.translatable(learned.analyzerProfile().translationKey()));
            else if (narrow) second = mod;
            else if (without(info) != null)
                second = Component.translatable("gui.adaptationwheel.origin.mod_without", mod, Component.translatable(without(info).translationKey()));
            else second = Component.translatable("gui.adaptationwheel.origin.mod", mod);
            return List.of(new Line(first, TEXT, FRAMED, 0), new Line(second, MUTED, FRAMED, 0));
        }

        private static AdaptationProfile without(AnalysisInfo info) {
            if (info.originWithout().isEmpty()) return null;
            try { return AdaptationProfile.valueOf(info.originWithout()); } catch (IllegalArgumentException unknown) { return null; }
        }

        /** «Подробности для настройки»: the source ID for Configured, the analyzer's figures and how stage time is chosen. */
        private List<Line> settings(VisibleEntry entry) {
            List<Line> lines = new ArrayList<>();
            if (key.equals(FpsAdaptation.KEY)) return lines;
            WheelEntry learned = entry.learned();
            lines.add(Line.of(Component.translatable("gui.adaptationwheel.source_id", key), MUTED));
            if (learned != null && learned.analysis().present()) {
                var suggested=learned.analysis().recommendedTimings();
                lines.add(Line.of(Component.translatable("gui.adaptationwheel.analysis.recommended",suggested.first()/20,suggested.second()/20,suggested.third()/20,suggested.fourth()/20), MUTED));
                if(learned.boss() && learned.existenceEnabled())lines.add(Line.of(Component.translatable("gui.adaptationwheel.analysis.recommended_fifth",suggested.existence()/20), MUTED));
                if(learned.pending())lines.add(Line.of(Component.translatable("gui.adaptationwheel.analysis.active",learned.duration()/20), MUTED));
                if(!suggested.equals(learned.timings())&&!learned.manual())lines.add(Line.of(Component.translatable("gui.adaptationwheel.analysis.applied_difference"), FAINT));
                lines.addAll(observations(learned));
            }
            lines.add(Line.of(Component.translatable("gui.adaptationwheel.settings.time"), FAINT));
            return lines;
        }

        /** «Категория»: what the row is filed under and whose word that is, with the button that corrects it or gives it back. */
        private int renderCategory(GuiGraphics g, Font font, WheelEntry learned, int y, int mouseX, int mouseY, int textWidth) {
            boolean manual = learned.manual();
            AnalysisInfo info = learned.analysis();
            boolean unsure = !manual && info.confidence() < UNSURE;
            categoryTop = y + scrollOffset;
            Component label = Component.translatable(manual ? "gui.adaptationwheel.category.restore" : "gui.adaptationwheel.category.fix");
            fixWidth = font.width(label) + 16;
            fixX = x1 - 6 - fixWidth;
            fixY = y;
            boolean hot = onFix(mouseX, mouseY);
            g.fill(fixX, y, fixX + fixWidth, y + FIX_HEIGHT, unsure ? hot ? 0xFF6B5323 : 0xFF54411C : hot ? 0xFF342C39 : 0xFF241F28);
            g.fill(fixX, y + FIX_HEIGHT - 1, fixX + fixWidth, y + FIX_HEIGHT, unsure ? GOLD : 0xFF4A4050);
            g.drawString(font, label, fixX + 8, y + 3, unsure ? 0xFFFFF0C8 : TEXT, false);
            int room = fixX - 8 - x0 - 4;
            List<Part> line = new ArrayList<>();
            line.add(part(Component.translatable("gui.adaptationwheel.category.label"), TEXT));
            line.add(part(Component.translatable(learned.profile().translationKey()), learned.profile().color));
            if (manual) {
                int used = parts(g, font, line, x0 + 4, y + 3, room);
                Component tag = Component.translatable("gui.adaptationwheel.category.manual");
                if (used + 6 + font.width(tag) + 8 <= room) chip(g, font, tag, x0 + 4 + used + 6, y + 1, GOLD);
            } else {
                line.add(part(Component.translatable("gui.adaptationwheel.category.confidence", info.confidence()), TEXT));
                String reason = "gui.adaptationwheel.category.reason." + info.reason().split("\\.")[0];
                // The reason is the first to go in a narrow window: «Анализ атаки» above says it in full.
                if (I18n.exists(reason) && width(font, line) + font.width(" · " + Component.translatable(reason).getString()) <= room)
                    line.add(part(" · " + Component.translatable(reason).getString(), TEXT));
                parts(g, font, line, x0 + 4, y + 3, room);
            }
            List<Line> notes = new ArrayList<>();
            notes.add(Line.of(!manual ? Component.translatable(unsure ? "gui.adaptationwheel.category.unsure" : "gui.adaptationwheel.category.sure")
                    : info.present() ? Component.translatable("gui.adaptationwheel.category.was", Component.translatable(learned.verdict().translationKey()), info.confidence())
                    : Component.translatable("gui.adaptationwheel.category.was_off"), MUTED));
            notes.add(Line.of(Component.translatable("gui.adaptationwheel.category.changes"), MUTED));
            notes.add(Line.of(Component.translatable("gui.adaptationwheel.category.changes_text"), MUTED));
            return lines(g, font, notes, y + FIX_HEIGHT + 4, textWidth) + 4;
        }

        /** The window over the details: every category, the row's own lit, the cursor in gold. */
        void renderPicker(GuiGraphics g) {
            VisibleEntry entry = entry();
            if (entry == null || entry.learned() == null || !entry.learned().fixable()) { cursor = -1; return; }
            Font font = font();
            CategoryGrid grid = grid();
            // Text is batched: draw what is queued now and lift the window above it, or the details show through.
            g.flush();
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(grid.x, grid.y, grid.x + grid.width, grid.y + grid.height, PANEL);
            g.renderOutline(grid.x, grid.y, grid.width, grid.height, GOLD);
            String name = entry.name().replaceFirst("\\s*\\[[^\\]]*]$", "");
            g.drawString(font, fit(font, Component.translatable("gui.adaptationwheel.category.picker", name).getString(), grid.width - CategoryGrid.PAD * 2), grid.x + CategoryGrid.PAD + 2, grid.y + 7, TEXT, false);
            AdaptationProfile[] profiles = AdaptationProfile.values();
            for (int i = 0; i < profiles.length; i++) {
                int x = grid.cellX(i), y = grid.cellY(i), height = CategoryGrid.ROW - 1;
                boolean pick = i == cursor;
                if (pick || profiles[i] == entry.learned().profile()) g.fill(x, y, x + grid.cell, y + height, pick ? SELECTED : CARD_HOVER);
                if (pick) g.renderOutline(x, y, grid.cell, height, GOLD);
                icon(g, CategoryIcons.of(profiles[i]), x + 3, y + 3, profiles[i].color);
                g.drawString(font, fit(font, Component.translatable(profiles[i].translationKey()).getString(), grid.cell - 16), x + 13, y + 3, pick ? 0xFFFFF0C8 : TEXT, false);
            }
            g.drawString(font, fit(font, Component.translatable("gui.adaptationwheel.category.picker_keys").getString(), grid.width - CategoryGrid.PAD * 2),
                    grid.x + CategoryGrid.PAD + 2, grid.y + grid.height - CategoryGrid.FOOT + 5, MUTED, false);
            g.pose().popPose();
        }

        /** What the analyzer observed and how it works. */
        private List<Line> observations(WheelEntry learned) {
            AnalysisInfo info=learned.analysis();
            var insight=info.insight();
            List<Line> lines = new ArrayList<>();
            lines.add(Line.of(Component.translatable("gui.adaptationwheel.analysis.origin",Component.translatable("analysis.adaptationwheel.kind."+info.kind()),info.observations()), MUTED));
            if(!insight.previousProfile().isEmpty())lines.add(Line.of(Component.translatable("analysis.adaptationwheel.revised",Component.translatable(AdaptationProfile.valueOf(insight.previousProfile()).translationKey()),Component.translatable(AdaptationProfile.valueOf(insight.revisedProfile()).translationKey())), MUTED));
            if(insight.variants()>0){
                lines.add(Line.of(Component.translatable("analysis.adaptationwheel.identity",insight.variants(),insight.owner().isEmpty()?"—":insight.owner(),insight.direct().isEmpty()?"—":insight.direct(),insight.damage().isEmpty()?"—":insight.damage()), MUTED));
                lines.add(Line.of(Component.translatable("analysis.adaptationwheel.context",Component.translatable("analysis.adaptationwheel.range."+insight.range()),insight.effects().isEmpty()?"—":insight.effects()), MUTED));
            }
            if(insight.verified()>0||insight.leaks()>0)lines.add(Line.of(Component.translatable("analysis.adaptationwheel.protection",insight.verified(),insight.leaks(),String.format(Locale.ROOT,"%.2f",insight.maxLeak())), insight.leaks()>0?WARN:MUTED));
            if(insight.leaks()>0)lines.add(Line.of(Component.translatable("analysis.adaptationwheel.leak_explained"), FAINT));
            if(!insight.flow().isEmpty())lines.add(Line.of(Component.translatable("analysis.adaptationwheel.flow",flowLabel(insight.flow())), MUTED));
            if(!insight.phase().isEmpty())lines.add(Line.of(Component.translatable("analysis.adaptationwheel.phase",phaseLabel(insight.phase())), MUTED));
            if(insight.intervalTicks()>0)lines.add(Line.of(Component.translatable("analysis.adaptationwheel.interval",String.format(Locale.ROOT,"%.1f",insight.intervalTicks()/20.0)), MUTED));
            if(!insight.predicted().isEmpty())lines.add(Line.of(Component.translatable("analysis.adaptationwheel.next",flowLabel(insight.predicted())), MUTED));
            if(info.kind().equals("effect"))lines.add(Line.of(Component.translatable("gui.adaptationwheel.analysis.effect_severity"), FAINT));
            lines.add(Line.of(Component.translatable("analysis.adaptationwheel.advisor."+insight.advisor()), FAINT));
            return lines;
        }

        void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = font();
            VisibleEntry entry = entry();
            settingsY = -1;
            fixY = -1;
            categoryTop = -1;
            if (entry == null) {
                int y = y0 + 40;
                for (FormattedCharSequence line : font.split(Component.translatable("gui.adaptationwheel.detail.empty"), x1 - x0 - 20)) {
                    g.drawCenteredString(font, line, (x0 + x1) / 2, y, MUTED);
                    y += font.lineHeight + 2;
                }
                return;
            }
            WheelEntry learned = entry.learned();
            boolean on = learned == null || learned.enabled();
            int color = entry.color(), tileX = x0 + (back ? 18 : 0), nameX = tileX + 20;
            int nameWidth = x1 - nameX - (toggle.visible ? toggle.getWidth() + 8 : 0);
            tile(g, entry.icon(), tileX, y0, 15, color, on);
            g.drawString(font, fit(font, entry.name(), nameWidth), nameX, y0 + 4, GOLD, false);
            int chipX = x0 + chip(g, font, Component.literal(entry.categoryName()), entry.icon(), x0, y0 + 19, color) + 4;
            if (learned != null && learned.boss()) chipX += chip(g, font, Component.translatable("gui.adaptationwheel.chip.boss"), chipX, y0 + 19, EXISTENCE) + 4;
            if (key.equals(firstKey)) chip(g, font, Component.translatable("gui.adaptationwheel.first"), chipX, y0 + 19, GOLD);
            int cardTop = y0 + 36;
            renderCard(g, font, entry, cardTop, color, partialTick);
            contentTop = renderTrack(g, font, entry, cardTop + CARD_HEIGHT + 10, color, partialTick) + 8;
            int textWidth = x1 - x0 - 12;
            g.enableScissor(x0 - 2, contentTop, x1, y1);
            int y = contentTop + 2 - scrollOffset;
            boolean history = false;
            for (Section section : sections(entry)) {
                y = sectionTitle(g, font, section.title(), y);
                y = lines(g, font, section.lines(), y, textWidth);
                y += 6;
                // «История» follows «Что даёт»: when each stage of this game passed and when the adaptation started.
                if (!history) { history = true; y = renderHistory(g, font, on ? color : dim(color), y); }
            }
            List<Line> settings = settings(entry);
            if (!settings.isEmpty()) {
                // The technical lines stay folded until asked for; the choice is remembered.
                boolean hot = mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + 16 && mouseY >= contentTop && mouseY < y1;
                g.fill(x0, y, x1, y + 16, hot ? CARD_HOVER : CARD);
                String title = (settingsOpen ? "▾ " : "▸ ") + Component.translatable("gui.adaptationwheel.section.settings").getString();
                g.drawString(font, title, x0 + 6, y + 4, MUTED, false);
                String side = Component.translatable("gui.adaptationwheel.section.settings_side").getString();
                int sideRoom = x1 - x0 - 24 - font.width(title);
                if (font.width(side) <= sideRoom) g.drawString(font, side, x1 - 6 - font.width(side), y + 4, FAINT, false);
                settingsY = y;
                y += 22;
                if (settingsOpen) {
                    if (learned != null && learned.fixable()) y = renderCategory(g, font, learned, y, mouseX, mouseY, textWidth);
                    y = lines(g, font, settings, y, textWidth);
                }
            }
            g.disableScissor();
            int contentHeight = y + scrollOffset - contentTop, visible = y1 - contentTop;
            maxScroll = Math.max(0, contentHeight - visible);
            // K folds the block open: bring the row it acted on into view, with the block's header above it.
            if (revealCategory && categoryTop >= 0) scrollOffset = categoryTop - contentTop - 24;
            revealCategory = false;
            scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
            if (maxScroll > 0) {
                int thumb = Math.max(12, visible * visible / contentHeight);
                int thumbTop = contentTop + (visible - thumb) * scrollOffset / maxScroll;
                g.fill(x1 - 3, contentTop, x1, y1, TRACK);
                g.fill(x1 - 3, thumbTop, x1, thumbTop + thumb, GOLD);
            }
        }

        private int renderHistory(GuiGraphics g, Font font, int color, int y) {
            List<WheelJournal.Event> events = JOURNAL.of(key);
            if (events.isEmpty()) return y;
            if (events.size() > 8) events = events.subList(0, 8);
            y = sectionTitle(g, font, Component.translatable("gui.adaptationwheel.section.history"), y);
            if (events.size() > 1) g.fill(x0 + 7, y + 3, x0 + 8, y + (events.size() - 1) * 11 + 4, RULE);
            long now = System.currentTimeMillis();
            for (WheelJournal.Event event : events) {
                Component what = switch (event.kind()) {
                    case STARTED -> Component.translatable("gui.adaptationwheel.history.started");
                    case FINISHED -> Component.translatable("gui.adaptationwheel.history.finished");
                    default -> Component.translatable("gui.adaptationwheel.history.stage", event.stage(), event.total());
                };
                String text = what.getString() + (event.kind() != WheelJournal.Kind.STARTED && event.protects()
                        ? Component.translatable("gui.adaptationwheel.history.protection", event.percent()).getString() : "");
                String when = ago(now - event.at(), false);
                g.fill(x0 + 5, y + 2, x0 + 10, y + 7, color);
                g.drawString(font, fit(font, text, x1 - x0 - 30 - font.width(when)), x0 + 16, y, TEXT, false);
                g.drawString(font, when, x1 - 6 - font.width(when), y, FAINT, false);
                y += 11;
            }
            return y + 7;
        }

        private int sectionTitle(GuiGraphics g, Font font, Component title, int y) {
            g.drawString(font, title, x0, y, GOLD, false);
            g.fill(x0 + font.width(title) + 6, y + 4, x1 - 6, y + 5, RULE);
            return y + 13;
        }

        private int lines(GuiGraphics g, Font font, List<Line> lines, int y, int textWidth) {
            for (int i = 0; i < lines.size(); i++) {
                Line line = lines.get(i);
                if (line.bar() == FRAMED) {
                    // Consecutive framed rows share one panel: fill and border first, then the text over them.
                    int end = i, rows = 0;
                    while (end < lines.size() && lines.get(end).bar() == FRAMED) rows += font.split(lines.get(end++).text(), textWidth - 8).size();
                    int height = rows * 10 + 4;
                    g.fill(x0 + 2, y, x0 + 4 + textWidth, y + height, ORIGIN_BORDER);
                    g.fill(x0 + 3, y + 1, x0 + 3 + textWidth, y + height - 1, ORIGIN_FILL);
                    int ty = y + 3;
                    for (; i < end; i++) for (FormattedCharSequence wrapped : font.split(lines.get(i).text(), textWidth - 8)) {
                        g.drawString(font, wrapped, x0 + 7, ty, lines.get(i).color(), false);
                        ty += 10;
                    }
                    i--;
                    y += height + 3;
                    continue;
                }
                for (FormattedCharSequence wrapped : font.split(line.text(), textWidth)) {
                    g.drawString(font, wrapped, x0 + 4, y, line.color(), false);
                    y += 10;
                }
                if (line.bar() >= 0) {
                    bar(g, x0 + 4, y, Math.min(120, textWidth), 3, line.bar(), line.barColor());
                    y += 6;
                }
                y += 2;
            }
            return y;
        }

        /**
         * «Сейчас»: the protection now in large type; beside it the stage, what it leads to, its bar, the time left at ×1
         * and the speed; on the right, when there is room, how a repeated hit changes the stage.
         */
        private void renderCard(GuiGraphics g, Font font, VisibleEntry entry, int top, int color, float partialTick) {
            WheelEntry learned = entry.learned();
            ClientWheelState.State state = state();
            boolean on = learned == null || learned.enabled(), fps = key.equals(FpsAdaptation.KEY);
            g.fill(x0, top, x1, top + CARD_HEIGHT, CARD);
            g.fill(x0, top, x0 + 2, top + CARD_HEIGHT, on ? color : dim(color));
            int total = learned == null ? 4 : learned.totalStages(), stage = learned == null ? 0 : Math.min(learned.stage(), total);
            boolean finished = learned != null && learned.finished();
            String big = entry.protects() ? (learned == null ? 0 : learned.reduction()) + "%" : stage + "/" + total;
            bigText(g, font, big, x0 + 12, top + 9, finished ? GOOD : learned != null && on ? TEXT : MUTED);
            g.drawString(font, Component.translatable(entry.protects() ? "gui.adaptationwheel.card.now_protection" : "gui.adaptationwheel.card.now_stages"),
                    x0 + 12, top + 32, MUTED, false);
            int middle = x0 + 108;
            g.fill(middle - 10, top + 8, middle - 9, top + CARD_HEIGHT - 8, RULE);
            int hintWidth = x1 - x0 >= 480 ? 150 : 0, middleWidth = x1 - middle - 10 - (hintWidth > 0 ? hintWidth + 20 : 0);
            List<Part> first = new ArrayList<>(), third = new ArrayList<>();
            float progress = 0;
            Component hint = null;
            String chipShort = null, chipFull = null, flash = null;
            String trigger = entry.trigger();
            int acceleration = learned == null ? 0 : learned.hitAccelerationPercent();
            if (learned == null) {
                first.add(part(Component.translatable("gui.adaptationwheel.card.undiscovered"), TEXT));
                third.add(part(Component.translatable("gui.adaptationwheel.card.undiscovered_hint"), MUTED));
                hint = Component.translatable("gui.adaptationwheel.card.hint_undiscovered");
            } else if (!learned.enabled()) {
                first.add(part(Component.translatable("gui.adaptationwheel.card.off"), WARN));
                third.add(part(Component.translatable("gui.adaptationwheel.card.off_hint"), MUTED));
                progress = learned.pending() ? progress(learned, state, partialTick) : finished ? 1 : 0;
                hint = Component.translatable("gui.adaptationwheel.card.hint_off");
            } else if (fps) {
                first.add(part(progressLine(learned), finished ? GOOD : TEXT));
                progress = progress(learned, state, partialTick);
            } else if (finished) {
                first.add(part(total == 5 ? Component.translatable("gui.adaptationwheel.card.done_five")
                        : Component.translatable("gui.adaptationwheel.card.done", total), GOOD));
                third.add(part(Component.translatable("gui.adaptationwheel.card.done_active"), MUTED));
                progress = 1;
                hint = Component.translatable("gui.adaptationwheel.card.hint_done");
            } else if (learned.pending()) {
                first.add(part(Component.translatable("gui.adaptationwheel.card.stage", stage + 1, total), TEXT));
                if (stage >= 4) first.add(part(Component.translatable("gui.adaptationwheel.card.target_existence"), EXISTENCE));
                else if (entry.protects()) first.add(part(Component.translatable("gui.adaptationwheel.card.target", learned.protections().percent(stage + 1)), color));
                if (moving(learned, state)) {
                    // The time stays in seconds of adaptation (×1); the speed is a gold chip beside it.
                    third.add(part(Component.translatable("gui.adaptationwheel.card.left", seconds(adaptationSeconds(learned, state, partialTick))), GOLD));
                    if (Math.abs(learned.speed() - 1) > 0.005) {
                        String speed = String.format(Locale.ROOT, "%.2f", learned.speed());
                        chipShort = Component.translatable("gui.adaptationwheel.card.speed_chip", speed).getString();
                        chipFull = learned.hits() > 0
                                ? Component.translatable("gui.adaptationwheel.card.speed_chip_full", speed, Math.round((learned.speed() - 1) * 100)).getString()
                                : Component.translatable("gui.adaptationwheel.card.speed_chip_experience", speed, learned.experiencePercent()).getString();
                    }
                    if (state != null && state.hitFlashAge(key, partialTick) < ClientWheelState.HIT_FLASH_TICKS)
                        flash = Component.translatable("gui.adaptationwheel.hud.hit_speed", acceleration).getString();
                } else third.add(part(Component.translatable(state == null || !state.equipped() ? "gui.adaptationwheel.card.paused_unequipped"
                        : !state.enabled() ? "gui.adaptationwheel.card.paused_off"
                        : key.equals(AdaptationProfile.DARK_KEY) ? "gui.adaptationwheel.card.paused_dark" : "gui.adaptationwheel.card.paused"), WARN));
                progress = progress(learned, state, partialTick);
                if (acceleration > 0) hint = Component.translatable("gui.adaptationwheel.card.hint_running" + trigger, acceleration);
            } else {
                first.add(part(Component.translatable("gui.adaptationwheel.card.waiting"), TEXT));
                first.add(part(entry.protects() && stage < 4
                        ? Component.translatable("gui.adaptationwheel.card.waiting_target", stage + 1, learned.protections().percent(stage + 1))
                        : Component.translatable("gui.adaptationwheel.card.waiting_stage", stage + 1), color));
                third.add(part(Component.translatable("gui.adaptationwheel.card.waiting_next" + trigger), MUTED));
                if (acceleration > 0) hint = Component.translatable("gui.adaptationwheel.card.hint_waiting" + trigger, acceleration);
                if (learned.experiencePercent() > 0) {
                    // Element experience: the next stage starts faster than ×1.
                    String speed = String.format(Locale.ROOT, "%.2f", 1 + learned.experiencePercent() / 100.0);
                    chipShort = Component.translatable("gui.adaptationwheel.card.speed_chip", speed).getString();
                    chipFull = Component.translatable("gui.adaptationwheel.card.speed_chip_experience", speed, learned.experiencePercent()).getString();
                }
            }
            int firstUsed = parts(g, font, first, middle, top + 8, middleWidth);
            bar(g, middle, top + 21, middleWidth, 5, progress, on ? color : dim(color));
            int used = parts(g, font, third, middle, top + 32, middleWidth);
            if (chipShort != null) {
                int room = middleWidth - used - 6;
                String chip = font.width(chipFull) + 8 <= room ? chipFull : chipShort;
                if (font.width(chip) + 8 <= room) chip(g, font, Component.literal(chip), middle + used + 6, top + 30, GOLD);
            }
            // A repeat hit lights «+30% скорости» at the right end of the first line for a second and a half.
            if (flash != null && font.width(flash) + 8 <= middleWidth - firstUsed)
                g.drawString(font, flash, middle + middleWidth - font.width(flash), top + 8, LIT, false);
            if (hintWidth > 0 && hint != null) {
                int hintX = x1 - hintWidth - 8, y = top + 8;
                g.fill(hintX - 10, top + 8, hintX - 9, top + CARD_HEIGHT - 8, RULE);
                List<FormattedCharSequence> wrapped = font.split(hint, hintWidth);
                for (int i = 0; i < Math.min(3, wrapped.size()); i++, y += 10) g.drawString(font, wrapped.get(i), hintX, y, MUTED, false);
            }
        }

        /** Stage timeline: one segment per stage with its protection and time, passed stages ticked. Returns the bottom edge. */
        private int renderTrack(GuiGraphics g, Font font, VisibleEntry entry, int y, int color, float partialTick) {
            WheelEntry learned = entry.learned();
            int total = learned == null ? 4 : learned.totalStages(), earned = learned == null ? 0 : Math.min(learned.stage(), total);
            float running = learned != null && learned.pending() ? progress(learned, state(), partialTick) : 0;
            boolean on = learned == null || learned.enabled();
            int gap = 4, segment = (x1 - x0 - gap * (total - 1)) / total;
            for (int i = 0; i < total; i++) {
                int x = x0 + i * (segment + gap), shade = i == 4 ? EXISTENCE : color;
                boolean current = i == earned && learned != null && learned.pending();
                bar(g, x, y, segment, 6, i < earned ? 1 : current ? running : 0, (i < earned || current) && on ? shade : dim(shade));
                Component label = i == 4 ? Component.translatable("gui.adaptationwheel.stage_existence")
                        : entry.protects() ? Component.translatable("gui.adaptationwheel.stage_protection", i + 1,
                                learned != null ? learned.protections().percent(i + 1) : StageProtections.DEFAULTS.percent(i + 1))
                        : Component.translatable("gui.adaptationwheel.stage_number", i + 1);
                String text = (i < earned ? "✓ " : "") + label.getString();
                g.drawString(font, fit(font, text, segment), x, y + 9, i < earned ? TEXT : current ? GOLD : MUTED, false);
                g.drawString(font, fit(font, Component.translatable("gui.adaptationwheel.seconds", entry.duration(i) / 20).getString(), segment), x, y + 19, MUTED, false);
            }
            return y + 28;
        }

        private String flowLabel(String raw){return java.util.Arrays.stream(raw.split(" → ")).map(token->{int colon=token.indexOf(':');if(colon<0)return token;String kind=token.substring(0,colon).toLowerCase(Locale.ROOT);String name=ru.pasha.adaptationwheel.server.DamageCategories.displayName(token.substring(colon+1)).getString();return Component.translatable("analysis.adaptationwheel.event."+kind).getString()+" · "+name;}).collect(java.util.stream.Collectors.joining(" → "));}
        private String phaseLabel(String raw){try{
            if(raw.startsWith("health-band:")){int n=Integer.parseInt(raw.substring(12));return Component.translatable("analysis.adaptationwheel.phase.health",(n-1)*25,n*25).getString();}
            if(raw.startsWith("dragon:"))return Component.translatable("analysis.adaptationwheel.phase.dragon",raw.substring(7)).getString();
            if(raw.startsWith("wither:"))return Component.translatable("analysis.adaptationwheel.phase.wither_"+raw.substring(7)).getString();
        }catch(RuntimeException ignored){}return raw;}
    }

    /** The details on their own screen, for windows too narrow to show them beside the list. */
    private final class DetailScreen extends Screen {
        private final String key;
        private DetailView view;
        private DetailScreen(String key) {
            super(Component.translatable("gui.adaptationwheel.details"));
            this.key = key;
            selectedKey = key;
        }
        @Override protected void init() {
            int[] panel = MenuLayout.detailPanel(width, height);
            view = new DetailView(this::addRenderableWidget, panel[0] + 12, panel[1] + 8, panel[2] - 12, panel[3] - 6, key, this::onClose);
        }
        @Override public void tick() { if (view != null) view.tick(); }
        @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            renderBackground(g);
            firstKey = WheelSummary.first(items(state(), partialTick));
            int[] panel = MenuLayout.detailPanel(width, height);
            panel(g, panel[0], panel[1], panel[2], panel[3]);
            int x = view.picking() ? -1 : mouseX, y = view.picking() ? -1 : mouseY;
            super.render(g, x, y, partialTick);
            view.render(g, x, y, partialTick);
            if (view.picking()) view.renderPicker(g);
        }
        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (view.picking()) { view.pickerClick(mouseX, mouseY); return true; }
            return super.mouseClicked(mouseX, mouseY, button) || view.click(mouseX, mouseY);
        }
        @Override public void mouseMoved(double mouseX, double mouseY) {
            if (view.picking()) view.pickerHover(mouseX, mouseY);
        }
        @Override public boolean keyReleased(int key, int scanCode, int modifiers) {
            view.released(key);
            return super.keyReleased(key, scanCode, modifiers);
        }
        @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (ClientEvents.MENU.matches(key, scanCode)) { minecraft.setScreen(null); return true; }
            if (view.picking()) { view.pickerKey(key); return true; }
            if (key == GLFW.GLFW_KEY_K && view.fixKey()) return true;
            switch (key) {
                case GLFW.GLFW_KEY_UP -> view.scrollBy(-22);
                case GLFW.GLFW_KEY_DOWN -> view.scrollBy(22);
                case GLFW.GLFW_KEY_PAGE_UP -> view.scrollBy(-(height / 2));
                case GLFW.GLFW_KEY_PAGE_DOWN -> view.scrollBy(height / 2);
                case GLFW.GLFW_KEY_BACKSPACE -> onClose();
                default -> { return super.keyPressed(key, scanCode, modifiers); }
            }
            return true;
        }
        @Override public void onClose() { minecraft.setScreen(AdaptationScreen.this); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) { return view.picking() || view.mouseScrolled(delta); }
    }

    /** «2 мин назад» / «только что»; dense: «2 мин». */
    private static String ago(long millis, boolean dense) {
        long[] ago = WheelJournal.ago(millis);
        if (ago[0] == 0) return Component.translatable("gui.adaptationwheel.ago.now").getString();
        return Component.translatable("gui.adaptationwheel.ago." + (ago[0] == 1 ? "minutes" : "hours") + (dense ? "_short" : ""), ago[1]).getString();
    }

    /** One line of «Что дальше»: when, its colour, the category icon and colour, the name and what comes. */
    private record NextLine(String when, int whenColor, int[] icon, int iconColor, String name, String tail) {}

    /**
     * «Сводка колеса»: the wheel with a mark per adaptation, «Что дальше», the counters that leave one group in the
     * list, a tip, the protection per category with the weak places, and «Недавно». In the details pane of a wide
     * menu, or dense on its own screen in a narrow one ({@link SummaryScreen}); the mock's numbers in both.
     */
    private final class SummaryView {
        private final int x0, y0, x1, y1;
        private final boolean dense, title;
        private final WheelSummary.Layout at;
        /** The counters' boxes of the last frame: x, y, width, height and the group each leaves in the list. */
        private final List<int[]> counters = new ArrayList<>();

        private SummaryView(int x0, int y0, int x1, int y1, boolean dense, boolean title) {
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1; this.dense = dense; this.title = title;
            at = WheelSummary.Layout.of(x1 - x0, dense);
        }

        int counterAt(double x, double y) {
            for (int[] box : counters) if (x >= box[0] && x < box[0] + box[2] && y >= box[1] && y < box[1] + box[3]) return box[4];
            return -1;
        }

        private int section(GuiGraphics g, Component text, int left, int right, int y) {
            g.drawString(font, text, left, y, GOLD, false);
            g.fill(left + font.width(text) + 6, y + 4, right - 6, y + 5, RULE);
            return y + 13;
        }

        void render(GuiGraphics g, int mouseX, int mouseY, ClientWheelState.State state, List<WheelSummary.Item> items) {
            boolean worn = state == null || state.equipped();
            int top = y0;
            if (title) {
                wheel(g, x0, y0, !worn);
                Component name = Component.translatable("gui.adaptationwheel.summary.title");
                g.drawString(font, name, x0 + 20, y0 + 4, GOLD, false);
                int room = x1 - x0 - 32 - font.width(name);
                List<Part> session = session(false);
                if (width(font, session) > room) session = session(true);
                int w = Math.min(room, width(font, session));
                parts(g, font, session, x1 - w, y0 + 4, room);
                top = y0 + 21;
            }
            int[] counts = WheelSummary.counts(items);
            int card = at.card(), wheelX = x0 + at.wheelX();
            g.fill(x0, top, x1, top + card, CARD);
            g.fill(x0, top, x0 + 2, top + card, worn ? GOLD : dim(WARN));
            renderRing(g, wheelX, top + at.wheelY(), items, worn);
            List<Part> done = List.of(part(String.valueOf(counts[WheelSummary.DONE]), GOLD),
                    part(Component.translatable(dense ? "gui.adaptationwheel.summary.ready_of" : "gui.adaptationwheel.summary.finished_of", counts[4]), MUTED));
            parts(g, font, done, wheelX - width(font, done) / 2, top + (dense ? 78 : 99), 140);
            if (!dense) {
                int lx = wheelX - 60;
                String[] legend = {"done", "running", "waiting"};
                int[] colors = {GOLD, WheelSummary.LIT, MUTED};
                for (int i = 0; i < 3; i++) {
                    String label = Component.translatable("gui.adaptationwheel.summary.legend." + legend[i]).getString();
                    g.fill(lx, top + 111, lx + 4, top + 115, worn ? colors[i] : dim(colors[i]));
                    g.drawString(font, label, lx + 7, top + 110, FAINT, false);
                    lx += 7 + font.width(label) + 8;
                }
            }
            int rx = x0 + at.rightX(), rw = x1 - (dense ? 6 : 8) - rx;
            g.fill(rx - (dense ? 6 : 8), top + (dense ? 6 : 8), rx - (dense ? 5 : 7), top + card - (dense ? 6 : 8), RULE);
            int y = section(g, Component.translatable("gui.adaptationwheel.summary.next"), rx, x1 - 2, top + (dense ? 5 : 7));
            int whenWidth = dense ? 34 : 36;
            for (NextLine line : nextLines(items)) {
                if (line.icon() == null) {
                    g.drawString(font, fit(font, line.name(), rw), rx, y, MUTED, false);
                } else {
                    g.drawString(font, line.when(), rx, y, line.whenColor(), false);
                    icon(g, line.icon(), rx + whenWidth, y, worn ? line.iconColor() : dim(line.iconColor()));
                    parts(g, font, List.of(part(line.name(), TEXT), part(line.tail(), MUTED)), rx + whenWidth + 11, y, rw - whenWidth - 11);
                }
                y += dense ? 10 : 11;
            }
            renderCounters(g, rx, top + (dense ? 52 : 56), rw, dense ? 18 : 28, counts, mouseX, mouseY);
            List<Part> tip = List.of(part(Component.translatable("gui.adaptationwheel.summary.tip"), GOLD), part(tip(state, items), TEXT));
            if (dense) parts(g, font, tip, rx, top + 77, rw);
            else {
                int tipY = top + 94;
                g.fill(rx, tipY, rx + rw, tipY + 20, tint(GOLD));
                g.fill(rx, tipY, rx + 2, tipY + 20, GOLD);
                parts(g, font, tip, rx + 8, tipY + 6, rw - 16);
            }
            int by = top + card + (dense ? 6 : 9), middle = x0 + at.middle();
            renderCategories(g, items, by, middle);
            renderRecent(g, middle + 8, by);
        }

        /** The wheel and its ring of marks, batched as runs of one colour. */
        private void renderRing(GuiGraphics g, int cx, int cy, List<WheelSummary.Item> items, boolean worn) {
            List<WheelSummary.Item> marked = WheelSummary.marks(items);
            int count = Math.min(marked.size(), 72), size = at.ring();
            int[] colors = new int[count];
            float[] fills = new float[count];
            for (int i = 0; i < count; i++) {
                colors[i] = WheelSummary.markColor(marked.get(i).group());
                fills[i] = marked.get(i).fill();
            }
            int[] runs = WheelSummary.runs(dense ? WheelSummary.ring(size, 21, 26, 32, colors, fills, worn)
                    : WheelSummary.ring(size, 29, 36, 43, colors, fills, worn), size);
            int x = cx - size / 2, y = cy - size / 2;
            g.drawManaged(() -> {
                for (int i = 0; i < runs.length; i += 4) g.fill(x + runs[i], y + runs[i + 1], x + runs[i] + runs[i + 2], y + runs[i + 1] + 1, runs[i + 3]);
            });
        }

        /** Up to three lines: the running stages that end soonest, then how many wait for a hit. */
        private List<NextLine> nextLines(List<WheelSummary.Item> items) {
            List<NextLine> lines = new ArrayList<>();
            int waiting = WheelSummary.counts(items)[WheelSummary.WAITING];
            List<WheelSummary.Item> next = WheelSummary.next(items);
            for (WheelSummary.Item item : next.subList(0, Math.min(next.size(), waiting > 0 ? 2 : 3))) {
                VisibleEntry entry = allEntries().get(item.key());
                int stage = item.stage() + 1;
                String tail = entry != null && entry.learned() != null && item.protects() && stage <= 4
                        ? Component.translatable("gui.adaptationwheel.summary.next.stage_target", stage, entry.learned().protections().percent(stage)).getString()
                        : Component.translatable("gui.adaptationwheel.summary.next.stage", stage).getString();
                lines.add(new NextLine(item.moving() ? Component.translatable("gui.adaptationwheel.seconds", seconds(shownSeconds(item))).getString()
                        : Component.translatable("gui.adaptationwheel.row.pause").getString(), item.moving() ? GOLD : WARN,
                        CategoryIcons.forKey(item.key(), item.profile()), colorOf(item.key(), item.profile()), nameOf(item.key(), ""), tail));
            }
            if (waiting > 0) {
                WheelSummary.Item first = items.stream().filter(item -> item.group() == WheelSummary.WAITING).findFirst().orElseThrow();
                lines.add(new NextLine(Component.translatable("gui.adaptationwheel.summary.next.hit").getString(), MUTED,
                        CategoryIcons.forKey(first.key(), first.profile()), colorOf(first.key(), first.profile()),
                        plural(next.isEmpty() ? "gui.adaptationwheel.summary.next.waiting" : "gui.adaptationwheel.summary.next.waiting_more", waiting, waiting).getString(),
                        Component.translatable("gui.adaptationwheel.summary.next.waiting_tail").getString()));
            }
            if (lines.isEmpty()) lines.add(new NextLine("", MUTED, null, 0, Component.translatable("gui.adaptationwheel.summary.next.none").getString(), ""));
            return lines;
        }

        /** «2 идут», «3 ждут удара», «1 выключена»: a click leaves only that group in the list. */
        private void renderCounters(GuiGraphics g, int x, int y, int w, int h, int[] counts, int mouseX, int mouseY) {
            counters.clear();
            int[] groups = {WheelSummary.RUNNING, WheelSummary.WAITING, WheelSummary.OFF};
            String[] keys = {"running", "waiting", "off"};
            int[] colors = {WheelSummary.LIT, MUTED, FAINT};
            int gap = 6, box = (w - gap * 2) / 3;
            for (int i = 0; i < 3; i++) {
                int bx = x + i * (box + gap), n = counts[groups[i]];
                boolean hot = mouseX >= bx && mouseX < bx + box && mouseY >= y && mouseY < y + h;
                g.fill(bx, y, bx + box, y + h, hot ? CARD_HOVER : INNER);
                if (groupFilter == groups[i]) g.renderOutline(bx, y, box, h, GOLD);
                String number = String.valueOf(n);
                if (!dense) bigText(g, font, number, bx + 8, y + (h - 14) / 2, colors[i]);
                else g.drawString(font, number, bx + 8, y + (h - 7) / 2, colors[i], false);
                int lx = bx + 8 + font.width(number) * (dense ? 1 : 2) + 6;
                g.drawString(font, fit(font, plural("gui.adaptationwheel.summary.count." + keys[i], n).getString(), bx + box - 14 - lx), lx, y + (h - 7) / 2, TEXT, false);
                g.drawString(font, "›", bx + box - 8, y + (h - 7) / 2, FAINT, false);
                counters.add(new int[]{bx, y, box, h, groups[i]});
            }
        }

        /** The tip under the counters: put the wheel on, switch it on, or how to speed up the stage that ends next. */
        private Component tip(ClientWheelState.State state, List<WheelSummary.Item> items) {
            if (state != null && !state.equipped()) return Component.translatable("gui.adaptationwheel.summary.tip.unworn");
            if (state != null && !state.enabled()) return Component.translatable("gui.adaptationwheel.summary.tip.off");
            List<WheelSummary.Item> next = WheelSummary.next(items);
            if (!next.isEmpty()) {
                VisibleEntry entry = allEntries().get(next.get(0).key());
                if (entry != null && entry.learned() != null && entry.learned().hitAccelerationPercent() > 0)
                    return Component.translatable("gui.adaptationwheel.summary.tip.running" + entry.trigger(), entry.name(), entry.learned().hitAccelerationPercent());
            }
            if (WheelSummary.counts(items)[WheelSummary.WAITING] > 0) return Component.translatable("gui.adaptationwheel.summary.tip.waiting");
            return Component.translatable("gui.adaptationwheel.summary.tip.idle");
        }

        private String names(List<WheelSummary.Category> categories) {
            List<String> names = new ArrayList<>();
            for (WheelSummary.Category category : categories) names.add(Component.translatable(category.profile().translationKey()).getString());
            if (names.size() <= 1) return String.join("", names);
            String and = Component.translatable("gui.adaptationwheel.summary.and").getString();
            return String.join(", ", names.subList(0, names.size() - 1)) + and + names.get(names.size() - 1);
        }

        /** «Холод и Взрывы — 20%» when they share the protection, else «Холод — 20%, Взрывы — 30%». */
        private String lowText(List<WheelSummary.Category> low, boolean only) {
            String key = only ? "gui.adaptationwheel.summary.weak.low_only" : "gui.adaptationwheel.summary.weak.low";
            boolean same = low.stream().allMatch(c -> c.best() == low.get(0).best());
            if (same) return Component.translatable(key, names(low), low.get(0).best()).getString();
            List<String> each = new ArrayList<>();
            for (WheelSummary.Category c : low) each.add(Component.translatable(key, names(List.of(c)), c.best()).getString());
            return String.join(", ", each);
        }

        private void renderCategories(GuiGraphics g, List<WheelSummary.Item> items, int by, int middle) {
            List<WheelSummary.Category> categories = WheelSummary.categories(items);
            List<WheelSummary.Category> unmet = WheelSummary.unmet(categories, dense ? 2 : 3), low = WheelSummary.low(categories, 2);
            int right = middle - (dense ? 8 : 10);
            int y = section(g, Component.translatable("gui.adaptationwheel.summary.categories"), x0, middle - (dense ? 2 : 4), by);
            // The weak places go under the rows; the rows take what room is left.
            Component weakTitle = Component.translatable("gui.adaptationwheel.summary.weak");
            List<FormattedCharSequence> weak = List.of();
            int chipWidth = font.width(weakTitle) + 8, weakHeight;
            if (dense) weakHeight = 22;
            else {
                List<String> pieces = new ArrayList<>();
                if (!unmet.isEmpty()) pieces.add(plural("gui.adaptationwheel.summary.weak.unmet", unmet.size(), names(unmet)).getString());
                if (!low.isEmpty()) pieces.add(lowText(low, false));
                String text = pieces.isEmpty() ? Component.translatable("gui.adaptationwheel.summary.weak.none").getString() : String.join("; ", pieces) + ".";
                weak = font.split(Component.literal(text), Math.max(40, middle - 14 - x0 - chipWidth - 6));
                if (weak.size() > 4) weak = weak.subList(0, 4);
                weakHeight = Math.max(11, weak.size() * 10) + 3;
            }
            int row = at.catRow(), shown = Math.max(0, Math.min(categories.size(), (y1 - y - weakHeight - 2) / row));
            for (int i = 0; i < shown; i++, y += row) categoryRow(g, categories.get(i), x0 + 2, right, y);
            if (dense) {
                List<Part> first = new ArrayList<>();
                first.add(part(weakTitle.getString() + ": ", WARN));
                first.add(part(unmet.isEmpty() ? (low.isEmpty() ? Component.translatable("gui.adaptationwheel.summary.weak.no").getString() : "")
                        : names(unmet), MUTED));
                parts(g, font, first, x0 + 2, y + 1, right - x0);
                if (!low.isEmpty()) g.drawString(font, fit(font, lowText(low, true), right - x0), x0 + 2, y + 11, MUTED, false);
                return;
            }
            y += 3;
            chip(g, font, weakTitle, x0, y, WARN);
            for (int i = 0; i < weak.size(); i++) g.drawString(font, weak.get(i), x0 + chipWidth + 6, y + 2 + i * 10, MUTED, false);
        }

        private void categoryRow(GuiGraphics g, WheelSummary.Category category, int left, int right, int y) {
            AdaptationProfile profile = category.profile();
            String name = Component.translatable(profile.translationKey()).getString();
            if (!category.met()) {
                String never = Component.translatable("gui.adaptationwheel.row.undiscovered").getString();
                icon(g, CategoryIcons.of(profile), left, y, dim(profile.color));
                g.drawString(font, fit(font, name, right - left - 16 - font.width(never)), left + 11, y, FAINT, false);
                g.drawString(font, never, right - font.width(never), y, FAINT, false);
                return;
            }
            int barWidth = at.barWidth(), best = category.best();
            icon(g, CategoryIcons.of(profile), left, y, profile.color);
            g.drawString(font, fit(font, name, right - left - barWidth - 60), left + 11, y, TEXT, false);
            String percent = (best >= 100 ? "✓ " : "") + best + "%";
            g.drawString(font, percent, right - font.width(percent), y, best >= 100 ? GOOD : best <= 20 ? WARN : TEXT, false);
            bar(g, right - 40 - barWidth, y + 3, barWidth, 3, best / 100f, category.off() ? dim(profile.color) : profile.color);
        }

        /** «Недавно»: the wheel's events of this game, newest first. */
        private void renderRecent(GuiGraphics g, int left, int by) {
            int y = section(g, Component.translatable("gui.adaptationwheel.summary.recent"), left, x1, by) + (dense ? 0 : 1);
            List<WheelJournal.Event> events = JOURNAL.recent();
            if (events.isEmpty()) {
                for (FormattedCharSequence line : font.split(Component.translatable("gui.adaptationwheel.summary.recent.empty"), x1 - left - 4)) {
                    g.drawString(font, line, left, y, FAINT, false);
                    y += 10;
                }
                return;
            }
            long now = System.currentTimeMillis();
            int pitch = dense ? 10 : 21, count = Math.min(events.size(), Math.max(0, (y1 - y) / pitch));
            if (!dense && count > 1) g.fill(left + 5, y + 4, left + 6, y + (count - 1) * pitch + 6, RULE);
            for (int i = 0; i < count; i++, y += pitch) {
                WheelJournal.Event event = events.get(i);
                int color = event.wheel() ? GOLD : colorOf(event.key(), event.profile());
                int[] icon = event.wheel() ? WHEEL_GLYPH : CategoryIcons.forKey(event.key(), event.profile());
                String name = event.wheel() ? "" : nameOf(event.key(), event.translationKey());
                String text = switch (event.kind()) {
                    case STARTED -> Component.translatable("gui.adaptationwheel.event.started", name).getString();
                    case STAGE -> Component.translatable("gui.adaptationwheel.event.stage", name, event.stage(), event.total()).getString();
                    case FINISHED -> Component.translatable("gui.adaptationwheel.event.finished", name).getString();
                    case WORN -> Component.translatable("gui.adaptationwheel.event.worn").getString();
                    case UNWORN -> Component.translatable("gui.adaptationwheel.event.unworn").getString();
                };
                int textColor = event.kind() == WheelJournal.Kind.FINISHED ? GOOD : event.kind() == WheelJournal.Kind.UNWORN ? WARN : TEXT;
                String when = ago(now - event.at(), dense);
                if (dense) {
                    icon(g, icon, left, y, color);
                    g.drawString(font, fit(font, text, x1 - left - 16 - font.width(when)), left + 11, y, textColor, false);
                    g.drawString(font, when, x1 - font.width(when), y, FAINT, false);
                    continue;
                }
                tile(g, icon, left, y, 11, color, true);
                g.drawString(font, fit(font, text, x1 - left - 16), left + 16, y, textColor, false);
                List<Part> second = new ArrayList<>();
                if (event.protects() && (event.kind() == WheelJournal.Kind.STAGE || event.kind() == WheelJournal.Kind.FINISHED))
                    second.add(part(Component.translatable("gui.adaptationwheel.event.protection", event.percent()).getString() + " · ", MUTED));
                second.add(part(when, FAINT));
                parts(g, font, second, left + 16, y + 10, x1 - left - 16);
            }
        }
    }

    /** The summary on its own screen for windows too narrow to show it beside the list; Esc goes back to the list. */
    private final class SummaryScreen extends Screen {
        private SummaryView view;
        private MenuLayout frame;
        private SummaryScreen() { super(Component.translatable("gui.adaptationwheel.summary.title")); }

        @Override protected void init() {
            frame = new MenuLayout(width, height);
            addRenderableWidget(new IconButton(frame.closeX, frame.closeY, IconButton.CLOSE, Component.translatable("gui.adaptationwheel.close"), button -> minecraft.setScreen(null)));
            view = new SummaryView(frame.left + 10, frame.top + (frame.compact ? 22 : 30), frame.right - 10, frame.statusY - 4, frame.compact, false);
        }

        @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            renderBackground(g);
            panel(g, frame.left, frame.top, frame.right, frame.bottom);
            ClientWheelState.State state = state();
            List<WheelSummary.Item> items = items(state, partialTick);
            boolean compact = frame.compact;
            g.renderItem(wheelIcon, frame.left + 9, frame.top + (compact ? 3 : 8));
            Component name = Component.translatable("gui.adaptationwheel.summary.title");
            int textY = frame.top + (compact ? 7 : 8);
            g.drawString(font, name, frame.left + 30, textY, GOLD, false);
            int chipX = stateChip(g, state, frame.closeX, frame.top + (compact ? 5 : 9));
            int sessionX = frame.left + 30 + font.width(name) + 8;
            parts(g, font, session(true), sessionX, textY, chipX - 8 - sessionX);
            super.render(g, mouseX, mouseY, partialTick);
            view.render(g, mouseX, mouseY, state, items);
            int x = frame.left + 10;
            x += keycap(g, font, Component.translatable("gui.adaptationwheel.keys.esc").getString(), x, frame.statusY) + 4;
            g.drawString(font, Component.translatable("gui.adaptationwheel.keys.back"), x, frame.statusY, MUTED, false);
        }

        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (super.mouseClicked(mouseX, mouseY, button)) return true;
            int group = view.counterAt(mouseX, mouseY);
            if (group < 0) return false;
            // A counter leaves its group in the list and shows the list.
            filterGroup(group);
            onClose();
            return true;
        }

        @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (ClientEvents.MENU.matches(key, scanCode)) { minecraft.setScreen(null); return true; }
            if (key == GLFW.GLFW_KEY_BACKSPACE) { onClose(); return true; }
            return super.keyPressed(key, scanCode, modifiers);
        }

        @Override public void onClose() { minecraft.setScreen(AdaptationScreen.this); }
        @Override public boolean isPauseScreen() { return false; }
    }
}
