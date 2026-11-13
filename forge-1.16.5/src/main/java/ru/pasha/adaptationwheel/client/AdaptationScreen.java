package ru.pasha.adaptationwheel.client;

import net.minecraft.client.Minecraft;




import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.IReorderingProcessor;
import ru.pasha.adaptationwheel.core.AdaptationProfile;
import ru.pasha.adaptationwheel.net.CatalogueEntry;
import ru.pasha.adaptationwheel.net.WheelEntry;
import ru.pasha.adaptationwheel.net.WheelNetwork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;import ru.pasha.adaptationwheel.core.Compat;

/** Catalogue discovery never starts adaptation; only server-supplied learned entries can be toggled. */
public final class AdaptationScreen extends PortScreen {
    private static final int GOLD = 0xFFEBC568;
    private static final int MUTED = 0xFFB5A78B;
    private static final int ROW_HEIGHT = 37;
    private int page, rows, panelWidth, left, listTop;
    private boolean catalogue;
    private String query = "";
    private AdaptationProfile category;
    private Button global, vision, tab, categoryButton, previous, next;
    private EditBox search;
    private final List<Button> details = new ArrayList<>();
    private final List<Button> toggles = new ArrayList<>();
    private List<VisibleEntry> visibleEntries = Compat.list();
    private List<CatalogueEntry> cachedCatalogue;
    private List<WheelEntry> cachedLearned;
    private Map<String, VisibleEntry> cachedEntries = java.util.Collections.emptyMap();
    private Map<String, VisibleEntry> filteredSource;
    private String filteredQuery;
    private AdaptationProfile filteredCategory;
    private boolean filteredCatalogue;

    private static final class VisibleEntry {
        private final CatalogueEntry catalogue;private final WheelEntry learned;
        VisibleEntry(CatalogueEntry c,WheelEntry e){catalogue=c;learned=e;}
        CatalogueEntry catalogue(){return catalogue;}WheelEntry learned(){return learned;}
        String key() { return catalogue.key(); }
        AdaptationProfile profile() { return learned == null ? catalogue.profile() : learned.profile(); }
        String name() {
            if (I18n.exists(catalogue.translationKey())) return Text.translatable(catalogue.translationKey()).getString();
            return catalogue.fallbackName().trim().isEmpty() ? catalogue.key() : catalogue.fallbackName();
        }
    }

    public AdaptationScreen() { super(Text.translatable("gui.adaptationwheel.menu")); }

    public static void open() {
        WheelNetwork.requestCatalogue();
        Minecraft.getInstance().setScreen(new AdaptationScreen());
    }

    private ClientWheelState.State state() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : ClientWheelState.get(mc.player.getId());
    }

    private Map<String, VisibleEntry> allEntries() {
        List<CatalogueEntry> catalogueEntries = ClientWheelState.catalogue();
        ClientWheelState.State state = state();
        List<WheelEntry> learnedEntries = state == null ? Compat.list() : state.entries();
        if (catalogueEntries == cachedCatalogue && learnedEntries == cachedLearned) return cachedEntries;
        Map<String, VisibleEntry> result = new LinkedHashMap<>();
        for (CatalogueEntry entry : catalogueEntries) result.put(entry.key(), new VisibleEntry(entry, null));
        for (WheelEntry learned : learnedEntries) {
            VisibleEntry known = result.get(learned.key());
            CatalogueEntry entry = known == null
                    ? new CatalogueEntry(learned.key(), learned.translationKey(), learned.key(), learned.profile())
                    : known.catalogue();
            result.put(learned.key(), new VisibleEntry(entry, learned));
        }
        cachedCatalogue = catalogueEntries;
        cachedLearned = learnedEntries;
        cachedEntries = result;
        return cachedEntries;
    }

    private void refreshEntries() {
        Map<String, VisibleEntry> source = allEntries();
        if (source == filteredSource && query.equals(filteredQuery) && category == filteredCategory
                && catalogue == filteredCatalogue) {
            page = Math.max(0, Math.min(page, Math.max(0, (visibleEntries.size() - 1) / rows)));
            return;
        }
        String match = query.trim().toLowerCase(Locale.ROOT);
        visibleEntries = source.values().stream()
                .filter(entry -> catalogue || entry.learned() != null)
                .filter(entry -> category == null || entry.profile() == category)
                .filter(entry -> match.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(match)
                        || entry.key().toLowerCase(Locale.ROOT).contains(match))
                .sorted(Comparator.comparing(VisibleEntry::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(VisibleEntry::key)).collect(java.util.stream.Collectors.toList());
        filteredSource = source;
        filteredQuery = query;
        filteredCategory = category;
        filteredCatalogue = catalogue;
        page = Math.max(0, Math.min(page, Math.max(0, (visibleEntries.size() - 1) / rows)));
    }

    @Override
    protected void init() {
        details.clear();
        toggles.clear();
        panelWidth = Math.min(600, width - 20);
        left = (width - panelWidth) / 2;
        listTop = 113;
        rows = Math.max(1, (height - listTop - 49) / ROW_HEIGHT);
        global = addRenderableWidget(Button.builder(Text.empty(), button -> {
            ClientWheelState.State state = state();
            if (state != null) WheelNetwork.setEnabled("", !state.enabled());
        }).bounds(left + 10, 31, panelWidth - 20, 20).build());
        vision = addRenderableWidget(Button.builder(Text.empty(), button -> {
            ClientWheelState.State state = state();
            if (state != null && state.nightVisionUnlocked()) WheelNetwork.setEnabled("@nightvision", !state.nightVision());
        }).bounds(left + 10, 31, 100, 20).build());
        int half = (panelWidth - 25) / 2;
        tab = addRenderableWidget(Button.builder(Text.empty(), button -> {
            catalogue = !catalogue;
            page = 0;
            tick();
        }).bounds(left + 10, 55, half, 20).build());
        categoryButton = addRenderableWidget(Button.builder(Text.empty(), button ->
                minecraft.setScreen(new CategoryScreen())).bounds(left + 15 + half, 55, half, 20).build());
        search = new EditBox(font, left + 11, 81, panelWidth - 22, 18,
                Text.translatable("gui.adaptationwheel.search"));
        search.setMaxLength(100);
        search.setHint(Text.translatable("gui.adaptationwheel.search"));
        search.setValue(query);
        search.setResponder(value -> { query = value; page = 0; tick(); });
        addRenderableWidget(search);
        for (int row = 0; row < rows; row++) {
            int index = row;
            details.add(addRenderableWidget(Button.builder(Text.empty(), button -> {
                int item = page * rows + index;
                if (item < visibleEntries.size()) minecraft.setScreen(new DetailScreen(visibleEntries.get(item).key()));
            }).bounds(left + 10, listTop + row * ROW_HEIGHT, panelWidth - 101, 20).build()));
            toggles.add(addRenderableWidget(Button.builder(Text.empty(), button -> {
                int item = page * rows + index;
                if (item < visibleEntries.size()) {
                    WheelEntry learned = visibleEntries.get(item).learned();
                    if (learned != null) WheelNetwork.setEnabled(learned.key(), !learned.enabled());
                }
            }).bounds(left + panelWidth - 85, listTop + row * ROW_HEIGHT, 75, 20).build()));
        }
        previous = addRenderableWidget(Button.builder(Text.literal("<"), button -> { page--; tick(); })
                .bounds(left + 10, height - 49, 30, 20).build());
        next = addRenderableWidget(Button.builder(Text.literal(">"), button -> { page++; tick(); })
                .bounds(left + panelWidth - 40, height - 49, 30, 20).build());
        addRenderableWidget(Button.builder(Text.translatable("gui.done"), button -> onClose())
                .bounds(width / 2 - 50, height - 25, 100, 20).build());
        tick();
    }

    @Override
    public void tick() {
        if (global == null) return;
        search.tick();
        refreshEntries();
        ClientWheelState.State state = state();
        boolean unlocked = state != null && state.nightVisionUnlocked();
        int half = (panelWidth - 25) / 2;
        global.active = state != null;
        global.setWidth(unlocked ? half : panelWidth - 20);
        global.setMessage(Text.translatable(state != null && state.enabled()
                ? "gui.adaptationwheel.global_on" : "gui.adaptationwheel.global_off"));
        vision.visible = unlocked;
        vision.active = unlocked;
        vision.setX(left + 15 + half);
        vision.setWidth(half);
        vision.setMessage(Text.translatable(state != null && state.nightVision()
                ? "gui.adaptationwheel.vision_on" : "gui.adaptationwheel.vision_off"));
        tab.setMessage(Text.translatable(catalogue ? "gui.adaptationwheel.my_adaptations" : "gui.adaptationwheel.catalogue"));
        ITextComponent categoryName = Text.translatable(category == null ? "gui.adaptationwheel.all_categories" : category.translationKey());
        categoryButton.setMessage(Text.literal(font.plainSubstrByWidth(categoryName.getString(), half - 12)));
        for (int row = 0; row < rows; row++) {
            int item = page * rows + row;
            Button detail = details.get(row), toggle = toggles.get(row);
            detail.visible = item < visibleEntries.size();
            toggle.visible = detail.visible && visibleEntries.get(item).learned() != null;
            if (!detail.visible) continue;
            VisibleEntry entry = visibleEntries.get(item);
            detail.setWidth(toggle.visible ? panelWidth - 101 : panelWidth - 20);
            detail.setMessage(Text.literal(font.plainSubstrByWidth(entry.name(), detail.getWidth() - 15)));
            if (toggle.visible) toggle.setMessage(Text.translatable(entry.learned().enabled()
                    ? "gui.adaptationwheel.on" : "gui.adaptationwheel.off"));
        }
        previous.active = page > 0;
        next.active = (page + 1) * rows < visibleEntries.size();
    }

    private ITextComponent progress(WheelEntry entry, ClientWheelState.State state, float partialTick) {
        if (!entry.pending()) return Text.translatable(entry.finished()
                ? "gui.adaptationwheel.finished" : "gui.adaptationwheel.await_hit");
        float remaining = state == null ? (float) Math.max(0, entry.duration() - entry.work()) : state.remaining(entry, partialTick);
        return Text.translatable(entry.progressing() && state != null && state.enabled() && state.equipped()
                        ? "gui.adaptationwheel.phase" : "gui.adaptationwheel.phase_paused",
                entry.stage() + 1, entry.boss() ? 5 : 4, (int) Math.ceil(remaining / 20),
                String.format(Locale.ROOT, "%.2f", entry.speed()));
    }

    private ITextComponent benefit(WheelEntry entry) {
        if (entry.key().equals(AdaptationProfile.DARK_KEY))
            return Text.translatable("gui.adaptationwheel.glow", entry.stage(), entry.stage() >= 3 ? 15 : entry.stage() * 5);
        if (entry.key().equals(AdaptationProfile.HUNGER_KEY) && entry.stage() >= 4)
            return Text.translatable("gui.adaptationwheel.fed");
        if (entry.stage() == 5) return Text.translatable("gui.adaptationwheel.existence_done");
        return Text.translatable(entry.key().startsWith("effect:")
                ? "gui.adaptationwheel.effect_resist" : "gui.adaptationwheel.reduction", entry.reduction());
    }

    private void line(GuiGraphics graphics, ITextComponent text, int x, int y, int color, int limit) {
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), limit), x, y, color, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, 8, left + panelWidth, height - 28, 0xED15121B);
        graphics.fill(left, 8, left + panelWidth, 10, GOLD);
        graphics.drawCenteredString(font, title, width / 2, 17, GOLD);
        ClientWheelState.State state = state();
        line(graphics, Text.translatable(state != null && state.equipped()
                        ? "gui.adaptationwheel.equipped" : "gui.adaptationwheel.not_equipped"),
                left + 11, 102, state != null && state.equipped() ? 0xFFBDE596 : 0xFFFFAD88, panelWidth - 22);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (visibleEntries.isEmpty()) line(graphics, Text.translatable(!query.trim().isEmpty() || category != null
                        ? "gui.adaptationwheel.no_matches" : catalogue ? "gui.adaptationwheel.catalogue_loading" : "gui.adaptationwheel.empty"),
                left + 11, listTop + 25, MUTED, panelWidth - 22);
        VisibleEntry hovered = null;
        for (int row = 0; row < rows && page * rows + row < visibleEntries.size(); row++) {
            VisibleEntry entry = visibleEntries.get(page * rows + row);
            ITextComponent status = entry.learned() == null ? Text.translatable("gui.adaptationwheel.undiscovered")
                    : progress(entry.learned(), state, partialTick);
            line(graphics, status, left + 13, listTop + row * ROW_HEIGHT + 23,
                    entry.learned() == null ? MUTED : entry.profile().color, panelWidth - 26);
            if (details.get(row).isHoveredOrFocused()) hovered = entry;
        }
        graphics.drawCenteredString(font, Text.translatable("gui.adaptationwheel.page", page + 1,
                Math.max(1, (visibleEntries.size() + rows - 1) / rows)), width / 2, height - 43, MUTED);
        if (hovered != null) graphics.renderTooltip(font,
                Compat.list(Text.literal(hovered.name()), Text.translatable(hovered.profile().translationKey()),
                        Text.translatable("gui.adaptationwheel.open_details")), java.util.Optional.empty(), mouseX, mouseY);
    }

    @Override public boolean isPauseScreen() { return false; }

    /** Direct category selection, including all categories, with small-screen pagination. */
    private final class CategoryScreen extends PortScreen {
        private int categoryPage;
        private CategoryScreen() { super(Text.translatable("gui.adaptationwheel.choose_category")); }
        @Override protected void init() {
            int panel = Math.min(560, width - 20), x = (width - panel) / 2;
            int columns = width >= 320 ? 2 : 1;
            int capacity = Math.max(1, (height - 86) / 24) * columns;
            int total = AdaptationProfile.values().length + 1;
            int cellWidth = (panel - 15) / columns;
            for (int index = 0; index < capacity && categoryPage * capacity + index < total; index++) {
                int item = categoryPage * capacity + index;
                AdaptationProfile profile = item == 0 ? null : AdaptationProfile.values()[item - 1];
                ITextComponent label = Text.translatable(profile == null ? "gui.adaptationwheel.all_categories" : profile.translationKey());
                Button button = addRenderableWidget(Button.builder(label, selected -> {
                    category = profile;
                    page = 0;
                    minecraft.setScreen(AdaptationScreen.this);
                }).bounds(x + 5 + (index % columns) * (cellWidth + 5), 39 + (index / columns) * 24, cellWidth, 20).build());
                button.active = profile != category;
            }
            if (categoryPage > 0) addRenderableWidget(Button.builder(Text.literal("<"), button -> {
                categoryPage--; rebuildWidgets();
            }).bounds(x + 5, height - 48, 30, 20).build());
            if ((categoryPage + 1) * capacity < total) addRenderableWidget(Button.builder(Text.literal(">"), button -> {
                categoryPage++; rebuildWidgets();
            }).bounds(x + panel - 35, height - 48, 30, 20).build());
            addRenderableWidget(Button.builder(Text.translatable("gui.back"), button -> onClose())
                    .bounds(width / 2 - 50, height - 26, 100, 20).build());
        }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            graphics.drawCenteredString(font, title, width / 2, 16, GOLD);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
        @Override public void onClose() { minecraft.setScreen(AdaptationScreen.this); }
        @Override public boolean isPauseScreen() { return false; }
    }

    /** Selecting a catalogue entry never invokes a mutation until a learned-entry toggle is pressed. */
    private final class DetailScreen extends PortScreen {
        private final String key;
        private Button toggle;
        private int panel, x;
        private int scrollOffset, maxScroll;
        private DetailScreen(String key) { super(Text.translatable("gui.adaptationwheel.details")); this.key = key; }
        @Override protected void init() {
            panel = Math.min(560, width - 20);
            x = (width - panel) / 2;
            toggle = addRenderableWidget(Button.builder(Text.empty(), button -> {
                VisibleEntry entry = allEntries().get(key);
                if (entry != null && entry.learned() != null) WheelNetwork.setEnabled(key, !entry.learned().enabled());
            }).bounds(width / 2 - 100, height - 51, 200, 20).build());
            addRenderableWidget(Button.builder(Text.translatable("gui.back"), button -> onClose())
                    .bounds(width / 2 - 50, height - 26, 100, 20).build());
            tick();
        }
        @Override public void tick() {
            VisibleEntry entry = allEntries().get(key);
            toggle.visible = entry != null && entry.learned() != null;
            if (toggle.visible) toggle.setMessage(Text.translatable(entry.learned().enabled()
                    ? "gui.adaptationwheel.disable_entry" : "gui.adaptationwheel.enable_entry"));
        }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            graphics.fill(x, 8, x + panel, height - 28, 0xED15121B);
            graphics.fill(x, 8, x + panel, 10, GOLD);
            VisibleEntry entry = allEntries().get(key);
            if (entry != null) {
                int y = 18 - scrollOffset;
                List<ITextComponent> lines = new ArrayList<>();
                lines.add(Text.literal(entry.name()));
                lines.add(Text.translatable("gui.adaptationwheel.type", Text.translatable(entry.profile().translationKey())));
                if (entry.learned() == null) {
                    lines.add(Text.translatable("gui.adaptationwheel.undiscovered"));
                    lines.add(Text.translatable("gui.adaptationwheel.discover_hint"));
                } else {
                    lines.add(benefit(entry.learned()));
                    lines.add(progress(entry.learned(), state(), partialTick));
                    lines.add(Text.translatable("gui.adaptationwheel.speed", String.format(Locale.ROOT, "%.2f", entry.learned().speed())));
                }
                lines.add(Text.translatable("gui.adaptationwheel.timings", entry.profile().duration(0) / 20,
                        entry.profile().duration(1) / 20, entry.profile().duration(2) / 20, entry.profile().duration(3) / 20));
                if (entry.learned() != null && entry.learned().boss())
                    lines.add(Text.translatable("gui.adaptationwheel.fifth_time", entry.profile().duration(4) / 20));
                lines.add(Text.translatable("gui.adaptationwheel.time_explained"));
                int index = 0;
                graphics.enableScissor(x + 10, 16, x + panel - 10, height - 62);
                for (ITextComponent line : lines) {
                    int color = index == 0 ? GOLD : index == 1 ? entry.profile().color : MUTED;
                    for (IReorderingProcessor wrapped : font.split(line, panel - 32)) {
                        graphics.drawString(font, wrapped, x + 13, y, color, false);
                        y += 11;
                    }
                    y += 3;
                    index++;
                }
                graphics.disableScissor();
                int contentHeight = y + scrollOffset - 18;
                int visibleHeight = height - 80;
                maxScroll = Math.max(0, contentHeight - visibleHeight);
                scrollOffset = Math.min(scrollOffset, maxScroll);
                if (maxScroll > 0) {
                    int thumbHeight = Math.max(12, visibleHeight * visibleHeight / contentHeight);
                    int thumbTop = 18 + (visibleHeight - thumbHeight) * scrollOffset / maxScroll;
                    graphics.fill(x + panel - 7, 18, x + panel - 4, height - 62, 0xFF493D2A);
                    graphics.fill(x + panel - 7, thumbTop, x + panel - 4, thumbTop + thumbHeight, GOLD);
                }
            }
            super.render(graphics, mouseX, mouseY, partialTick);
        }
        @Override public void onClose() { minecraft.setScreen(AdaptationScreen.this); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (delta * 22)));
            return true;
        }
    }
}
