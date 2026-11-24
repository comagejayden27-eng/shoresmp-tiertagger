package com.comag.shoresmp.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

public class TierSettingsScreen extends Screen {

    private static final int TAB_SETTINGS = 0;
    private static final int TAB_PRIORITY = 1;
    private static final int TAB_COLORS = 2;
    private static final int TAB_LEADERBOARD = 3;
    private static final int TAB_SEARCH = 4;
    public static final String[] COLOR_KEYS = {"HT1", "LT1", "HT2", "LT2", "HT3", "LT3", "HT4", "LT4", "HT5", "LT5", "Retired"};

    private static final int ROW_H = 27;        // one overall leaderboard row
    private static final int LINE_H = 13;       // one name in a gamemode column
    public static final String DISCORD_URL = "https://discord.gg/shoretiers";

    // Tabs at the top of the leaderboard. 0 = Overall, then these gamemodes (Crystal, Mace, NethOP, SMP, Sword)
    private static final String[] LB_MODES = {"Crystal", "Mace", "NethOP", "SMP", "Sword"};

    private final Screen parent;
    private int tab = TAB_SETTINGS;

    // What the user typed in the Search tab, and the last error
    private String searchText = "";
    private String searchError = "";

    // Leaderboard: which top tab is open (0 = Overall) and how far it is scrolled (in rows)
    private int lbTab = 0;
    private int lbScroll = 0;
    private int lbMaxScroll = 0;
    private int lbViewHeight = 0;
    private int lbDataSig = -1;

    private int left, top, panelW, panelH, contentX, contentW;

    private record Label(int x, int y, Component text) {}
    private record Swatch(int x, int y, String key) {}
    private record LbRow(int y, int rank, TierData.Row row) {}
    private record ColHeader(int x, int y, int w, int tier) {}
    private record BoardLine(int x, int y, int w, TierData.BoardEntry entry) {}

    private final List<Label> labels = new ArrayList<>();
    private final List<Swatch> swatches = new ArrayList<>();
    private final List<LbRow> lbRows = new ArrayList<>();
    private final List<ColHeader> colHeaders = new ArrayList<>();
    private final List<BoardLine> boardLines = new ArrayList<>();

    // Lets one toggle button read and write one setting
    private interface BoolField {
        boolean get(TierConfig c);
        void set(TierConfig c, boolean value);
    }

    public TierSettingsScreen(Screen parent) {
        super(Component.literal("ShoreSMP TierTagger"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelW = Math.min(this.width - 20, 600);
        panelH = Math.min(this.height - 16, 340);
        left = (this.width - panelW) / 2;
        top = (this.height - panelH) / 2;
        contentX = left + 130;
        contentW = panelW - 130 - 14;
        rebuild();
    }

    // Rebuilds every button for the current tab
    private void rebuild() {
        clearWidgets();
        labels.clear();
        swatches.clear();
        lbRows.clear();
        colHeaders.clear();
        boardLines.clear();

        // Sidebar
        String[] names = {"Settings", "Priority", "Colors", "Leaderboard", "Search"};
        for (int i = 0; i < names.length; i++) {
            final int t = i;
            String text = (tab == i ? "> " : "   ") + names[i];
            addRenderableWidget(Button.builder(Component.literal(text), b -> {
                tab = t;
                rebuild();
            }).bounds(left + 14, top + 56 + i * 26, 100, 20).build());
        }

        if (tab == TAB_SETTINGS) buildSettings();
        else if (tab == TAB_PRIORITY) buildPriority();
        else if (tab == TAB_COLORS) buildColors();
        else if (tab == TAB_LEADERBOARD) buildLeaderboard();
        else buildSearch();

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(left + panelW / 2 - 60, top + panelH - 30, 120, 20).build());
    }

    // Opens a web link in the player's browser. Plain Java, so it doesn't depend on the Minecraft version.
    private static void openLink(String url) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
            ProcessBuilder pb;
            if (os.contains("win")) pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            else if (os.contains("mac")) pb = new ProcessBuilder("open", url);
            else pb = new ProcessBuilder("xdg-open", url);
            pb.start();
        } catch (Exception e) {
            System.out.println("Could not open " + url + ": " + e.getMessage());
        }
    }

    private void openProfile(String name) {
        this.minecraft.gui.setScreen(new TierProfileScreen(this, name));
    }

    // ---------- Settings tab ----------
    private void buildSettings() {
        int colW = (contentW - 8) / 2;
        int x1 = contentX;
        int x2 = contentX + colW + 8;
        int y = top + 56;

        toggle(x1, y, colW, "Mod", new BoolField() {
            public boolean get(TierConfig c) { return c.modEnabled; }
            public void set(TierConfig c, boolean v) { c.modEnabled = v; }
        });
        toggle(x2, y, colW, "Tablist", new BoolField() {
            public boolean get(TierConfig c) { return c.tablist; }
            public void set(TierConfig c, boolean v) { c.tablist = v; }
        });
        toggle(x1, y + 26, colW, "Nametags (soon)", new BoolField() {
            public boolean get(TierConfig c) { return c.nametags; }
            public void set(TierConfig c, boolean v) { c.nametags = v; }
        });
        toggle(x2, y + 26, colW, "Icons", new BoolField() {
            public boolean get(TierConfig c) { return c.icons; }
            public void set(TierConfig c, boolean v) { c.icons = v; }
        });
        toggle(x1, y + 52, colW, "Retired", new BoolField() {
            public boolean get(TierConfig c) { return c.retired; }
            public void set(TierConfig c, boolean v) { c.retired = v; }
        });
        toggle(x2, y + 52, colW, "Peak tiers", new BoolField() {
            public boolean get(TierConfig c) { return c.showPeaks; }
            public void set(TierConfig c, boolean v) { c.showPeaks = v; }
        });

        labels.add(new Label(contentX, y + 86,
                Component.literal("Retired OFF hides R tiers. Peak tiers shows (peak: HT1) in /tiertagger.")
                        .withStyle(ChatFormatting.DARK_GRAY)));
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + ": ")
                .append(Component.literal(on ? "ON" : "OFF")
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private void toggle(int x, int y, int w, String label, BoolField field) {
        addRenderableWidget(Button.builder(toggleText(label, field.get(TierConfig.get())), btn -> {
            boolean value = !field.get(TierConfig.get());
            field.set(TierConfig.get(), value);
            TierConfig.save();
            btn.setMessage(toggleText(label, value));
        }).bounds(x, y, w, 20).build());
    }

    // ---------- Priority tab ----------
    private void buildPriority() {
        List<String> order = TierConfig.get().priority;
        for (int i = 0; i < order.size(); i++) {
            final int index = i;
            int y = top + 56 + i * 26;
            String mode = order.get(i);

            labels.add(new Label(contentX + 4, y + 6,
                    Component.literal("#" + (i + 1) + "  ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(mode)
                                    .withStyle(s -> s.withColor(TierData.modeColor(mode))))));

            int bx = contentX + contentW - 84;
            addRenderableWidget(Button.builder(Component.literal("Up"), b -> {
                if (index > 0) {
                    java.util.Collections.swap(TierConfig.get().priority, index, index - 1);
                    TierConfig.save();
                    rebuild();
                }
            }).bounds(bx, y, 40, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Down"), b -> {
                if (index < TierConfig.get().priority.size() - 1) {
                    java.util.Collections.swap(TierConfig.get().priority, index, index + 1);
                    TierConfig.save();
                    rebuild();
                }
            }).bounds(bx + 44, y, 40, 20).build());
        }
    }

    // ---------- Colors tab ----------
    private void buildColors() {
        int colW = (contentW - 8) / 2;
        String[] keys = TierConfig.COLOR_KEYS;
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            int col = i < 6 ? 0 : 1;
            int row = i < 6 ? i : i - 6;
            int x = contentX + col * (colW + 8);
            int y = top + 56 + row * 24;

            labels.add(new Label(x + 2, y + 6, Component.literal(key).withStyle(ChatFormatting.WHITE)));
            swatches.add(new Swatch(x + 52, y + 4, key));

            EditBox box = new EditBox(this.font, x + 70, y, 62, 18, Component.literal(key));
            box.setMaxLength(6);
            box.setValue(TierConfig.get().colors.get(key));
            box.setResponder(s -> {
                if (s.matches("[0-9a-fA-F]{6}")) {
                    TierConfig.get().colors.put(key, s.toUpperCase());
                    TierConfig.save();
                }
            });
            addRenderableWidget(box);
        }

        addRenderableWidget(Button.builder(Component.literal("Reset default colors"), b -> {
            TierConfig.resetColors();
            rebuild();
        }).bounds(contentX, top + 56 + 6 * 24 + 6, 160, 20).build());
    }

    // ---------- Leaderboard tab ----------
    // Overall = the top 100 players, scrolling with the mouse wheel.
    // A gamemode tab = Tier 1 to Tier 5 columns of that gamemode (HT players first, LT players last).
    private void buildLeaderboard() {
        // Tabs along the top: Overall, Crystal, Mace, NethOP, SMP, Sword
        int count = LB_MODES.length + 1;
        int gap = 3;
        int tabW = (contentW - gap * (count - 1)) / count;
        for (int i = 0; i < count; i++) {
            final int t = i;
            MutableComponent label = Component.literal("");
            if (i > 0) {
                label.append(TierData.modeIcon(TierData.modeIndex(LB_MODES[i - 1])));
                label.append(Component.literal(" "));
            }
            label.append(Component.literal(i == 0 ? "Overall" : LB_MODES[i - 1])
                    .withStyle(lbTab == i ? ChatFormatting.WHITE : ChatFormatting.GRAY));
            addRenderableWidget(Button.builder(label, b -> {
                lbTab = t;
                lbScroll = 0;
                rebuild();
            }).bounds(contentX + i * (tabW + gap), top + 8, tabW, 18).build());
        }

        // Heading, scroll buttons and the Discord link
        int rowY = top + 31;
        String heading = lbTab == 0 ? "Top 100 Players" : LB_MODES[lbTab - 1] + " Tiers";
        labels.add(new Label(contentX + 2, rowY + 4, Component.literal(heading).withStyle(ChatFormatting.WHITE)));
        addRenderableWidget(Button.builder(Component.literal("Discord"), b -> openLink(DISCORD_URL))
                .bounds(contentX + contentW - 58, rowY, 58, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Up"), b -> scrollBy(-3))
                .bounds(contentX + contentW - 58 - 66, rowY, 30, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Down"), b -> scrollBy(3))
                .bounds(contentX + contentW - 58 - 34, rowY, 30, 16).build());

        int y0 = top + 54;
        lbViewHeight = 0;
        lbMaxScroll = 0;

        TierApi.State state = lbTab == 0 ? TierData.leaderboardState() : TierData.boardState(LB_MODES[lbTab - 1]);
        if (state == TierApi.State.LOADING) {
            labels.add(new Label(contentX + 4, y0 + 10, Component.literal("Loading...").withStyle(ChatFormatting.GRAY)));
        } else if (state == TierApi.State.ERROR) {
            labels.add(new Label(contentX + 4, y0 + 10, Component.literal("Could not reach the tier server.").withStyle(ChatFormatting.RED)));
        } else if (lbTab == 0) {
            buildOverall(y0);
        } else {
            buildBoard(y0);
        }
        lbDataSig = dataSignature();

        // The points table, in two short lines so it fits
        String[] legend = TierData.pointsLegendLines();
        labels.add(new Label(contentX, top + panelH - 58, Component.literal(legend[0]).withStyle(ChatFormatting.DARK_GRAY)));
        labels.add(new Label(contentX, top + panelH - 47, Component.literal(legend[1]).withStyle(ChatFormatting.DARK_GRAY)));
    }

    // The overall top 100: only the rows that fit are drawn, the mouse wheel moves the list
    private void buildOverall(int y0) {
        List<TierData.Row> rows = TierData.leaderboard();
        int viewRows = Math.max(3, (panelH - 54 - 62) / ROW_H);
        lbViewHeight = viewRows * ROW_H;
        lbMaxScroll = Math.max(0, rows.size() - viewRows);
        lbScroll = Math.max(0, Math.min(lbScroll, lbMaxScroll));

        if (rows.isEmpty()) {
            labels.add(new Label(contentX + 4, y0 + 10, Component.literal("Nobody has been tiertested yet.").withStyle(ChatFormatting.GRAY)));
            return;
        }
        for (int i = 0; i < viewRows; i++) {
            int index = lbScroll + i;
            if (index >= rows.size()) break;
            int y = y0 + i * ROW_H;
            TierData.Row row = rows.get(index);
            lbRows.add(new LbRow(y, index + 1, row));

            // Clicking anywhere on the row opens the profile (the row is drawn on top of this button)
            String name = row.name();
            addRenderableWidget(Button.builder(Component.literal(""), b -> openProfile(name))
                    .bounds(contentX, y, contentW - 8, 25).build());
        }
    }

    // One gamemode: Tier 1 to Tier 5 columns
    private void buildBoard(int y0) {
        List<List<TierData.BoardEntry>> cols = TierData.board(LB_MODES[lbTab - 1]);
        int gap = 4;
        int colW = (contentW - 8 - gap * 4) / 5;
        int viewLines = Math.max(3, (panelH - 54 - 20 - 62) / LINE_H);
        lbViewHeight = 20 + viewLines * LINE_H;

        int longest = 0;
        for (List<TierData.BoardEntry> c : cols) longest = Math.max(longest, c.size());
        lbMaxScroll = Math.max(0, longest - viewLines);
        lbScroll = Math.max(0, Math.min(lbScroll, lbMaxScroll));

        for (int c = 0; c < 5; c++) {
            int x = contentX + c * (colW + gap);
            colHeaders.add(new ColHeader(x, y0, colW, c + 1));
            List<TierData.BoardEntry> list = cols.get(c);
            for (int i = 0; i < viewLines; i++) {
                int index = lbScroll + i;
                if (index >= list.size()) break;
                int y = y0 + 20 + i * LINE_H;
                TierData.BoardEntry entry = list.get(index);
                boardLines.add(new BoardLine(x, y, colW, entry));

                String name = entry.name();
                addRenderableWidget(Button.builder(Component.literal(""), b -> openProfile(name))
                        .bounds(x, y, colW, LINE_H - 1).build());
            }
        }
    }

    // Changes whenever the leaderboard data changes (so the screen can redraw when it finishes loading)
    private int dataSignature() {
        if (lbTab == 0) {
            return TierData.leaderboardState().ordinal() * 1_000_000 + TierData.leaderboard().size();
        }
        String mode = LB_MODES[lbTab - 1];
        int n = 0;
        for (List<TierData.BoardEntry> c : TierData.board(mode)) n += c.size();
        return TierData.boardState(mode).ordinal() * 1_000_000 + n;
    }

    private void scrollBy(int rows) {
        int next = Math.max(0, Math.min(lbScroll + rows, lbMaxScroll));
        if (next != lbScroll) {
            lbScroll = next;
            rebuild();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (tab == TAB_LEADERBOARD && dataSignature() != lbDataSig) rebuild();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == TAB_LEADERBOARD && lbMaxScroll > 0) {
            scrollBy(scrollY > 0 ? -3 : 3);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // Draws one leaderboard row: rank banner, avatar, name, title, region badge and tiers
    private void drawLeaderboardRow(GuiGraphicsExtractor g, LbRow lb) {
        int x = contentX;
        int y = lb.y();
        TierData.Row row = lb.row();
        String name = row.name();

        int w = contentW - 8; // leaves room for the scrollbar
        g.fill(x, y, x + w, y + 25, 0xFF121622);

        // Rank banner
        int rank = lb.rank();
        g.fill(x, y, x + 30, y + 25, 0xFF000000 | TierData.rankColor(rank));
        String rankText = rank + ".";
        g.text(this.font, Component.literal(rankText).withStyle(ChatFormatting.BOLD),
                x + 15 - this.font.width(rankText) / 2, y + 8, rank <= 3 ? 0xFF1A1A1A : 0xFFCFD8F0);

        // Avatar
        TierProfileScreen.drawFace(g, name, x + 34, y + 2, 21);

        // Name and title
        g.text(this.font, Component.literal(name).withStyle(ChatFormatting.BOLD), x + 60, y + 3, 0xFFFFFFFF);
        MutableComponent sub = Component.literal(TierData.titleName(row.points()))
                .withStyle(s -> s.withColor(TierData.titleColor(row.points())));
        sub.append(Component.literal(" (" + row.points() + " points)").withStyle(ChatFormatting.GRAY));
        g.text(this.font, sub, x + 60, y + 14, 0xFFFFFFFF);

        // Region badge
        int rx = x + w - 140;
        String code = TierData.regionCode(name);
        g.fill(rx, y + 2, rx + 30, y + 13, 0xFF000000 | TierData.regionBgColor(code));
        g.text(this.font, Component.literal(code),
                rx + 15 - this.font.width(code) / 2, y + 3, 0xFF000000 | TierData.regionTextColor(code));

        // Tiers: one small box per gamemode, in the user's priority order
        List<String> order = TierConfig.get().priority;
        int tx = x + w - 104;
        for (int i = 0; i < order.size(); i++) {
            int mode = TierData.modeIndex(order.get(i));
            if (mode < 0) continue;
            int cx = tx + i * 20 + 10;
            TierData.Entry e = TierData.entryFor(name, mode);

            g.fill(cx - 8, y + 1, cx + 8, y + 17, e == null ? 0xFF121622 : 0xFF1E2438);
            Component label;
            int labelColor;
            if (e == null) {
                label = Component.literal("-");
                labelColor = 0xFF6B7390;
            } else {
                g.text(this.font, TierData.modeIcon(mode), cx - 4, y + 5, 0xFFFFFFFF);
                label = Component.literal(TierData.label(e));
                labelColor = 0xFF000000 | TierData.colorOf(e);
            }
            g.text(this.font, label, cx - this.font.width(label) / 2, y + 17, labelColor);
        }
    }

    // "Tier 1" ... "Tier 5" heading of a gamemode column (gold, silver, bronze, then dark)
    private void drawColumnHeader(GuiGraphicsExtractor g, ColHeader h) {
        int accent = 0xFF000000 | TierData.rankColor(h.tier());
        g.fill(h.x(), h.y(), h.x() + h.w(), h.y() + 17, 0xFF161A28);
        g.fill(h.x(), h.y(), h.x() + h.w(), h.y() + 2, accent);
        Component text = Component.literal("Tier " + h.tier()).withStyle(ChatFormatting.BOLD);
        int color = h.tier() <= 3 ? accent : 0xFFCFD8F0;
        g.text(this.font, text, h.x() + h.w() / 2 - this.font.width(text) / 2, h.y() + 6, color);
    }

    // One player in a gamemode column: face, name and the HT/LT label
    private void drawBoardLine(GuiGraphicsExtractor g, BoardLine line) {
        int x = line.x();
        int y = line.y();
        TierData.BoardEntry e = line.entry();

        g.fill(x, y, x + line.w(), y + LINE_H - 1, 0xFF121622);
        TierProfileScreen.drawFace(g, e.name(), x + 2, y + 1, 10);

        String label = e.retired() ? "R" + e.tier() : e.tier();
        int labelColor = 0xFF000000 | TierConfig.color(e.retired() ? "Retired" : e.tier());
        int labelW = this.font.width(label);
        g.text(this.font, Component.literal(label), x + line.w() - labelW - 2, y + 2, labelColor);

        // Shorten the name if it would run into the label
        String name = e.name();
        int room = line.w() - 16 - labelW - 6;
        while (name.length() > 1 && this.font.width(name) > room) name = name.substring(0, name.length() - 1);
        g.text(this.font, Component.literal(name), x + 15, y + 2, 0xFFFFFFFF);
    }

    // The thin scroll bar on the right of the list
    private void drawScrollbar(GuiGraphicsExtractor g, int y0) {
        int x = contentX + contentW - 4;
        int trackH = lbViewHeight;
        g.fill(x, y0, x + 3, y0 + trackH, 0xFF1A1D2A);
        int visible = Math.max(1, lbTab == 0 ? lbViewHeight / ROW_H : (lbViewHeight - 20) / LINE_H);
        int thumbH = Math.max(14, trackH * visible / (visible + lbMaxScroll));
        int thumbY = y0 + (trackH - thumbH) * lbScroll / Math.max(1, lbMaxScroll);
        g.fill(x, thumbY, x + 3, thumbY + thumbH, 0xFFC0392B);
    }

    // ---------- Search tab ----------
    private void buildSearch() {
        int y = top + 60;

        EditBox box = new EditBox(this.font, contentX, y, Math.min(200, contentW - 90), 20,
                Component.literal("Player name"));
        box.setMaxLength(16);
        box.setValue(searchText);
        box.setResponder(s -> searchText = s);
        addRenderableWidget(box);

        addRenderableWidget(Button.builder(Component.literal("Search"), b -> {
            String name = searchText.trim();
            if (name.isEmpty()) {
                searchError = "Type a player name first.";
                rebuild();
            } else if (!TierData.exists(name)) {
                searchError = "Could not find player " + name;
                rebuild();
            } else {
                searchError = "";
                openProfile(name);
            }
        }).bounds(contentX + Math.min(200, contentW - 90) + 8, y, 74, 20).build());

        labels.add(new Label(contentX, y + 30,
                Component.literal("Opens a profile card with the player's tiers and points.")
                        .withStyle(ChatFormatting.DARK_GRAY)));

        if (!searchError.isEmpty()) {
            labels.add(new Label(contentX, y + 48,
                    Component.literal(searchError).withStyle(ChatFormatting.RED)));
        }
    }

    // ---------- Drawing ----------
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);

        int right = left + panelW;
        int bottom = top + panelH;
        int red = 0xFFC0392B;

        g.fill(left, top, right, bottom, 0xF00A0B12);                 // panel
        g.fill(left, top, right, top + 1, red);                       // border
        g.fill(left, bottom - 1, right, bottom, red);
        g.fill(left, top, left + 1, bottom, red);
        g.fill(right - 1, top, right, bottom, red);
        g.fill(left + 8, top + 8, left + 122, bottom - 8, 0xFF10121C); // sidebar
        g.fill(contentX - 8, top + 12, contentX - 7, bottom - 12, 0xFF2A2D3A); // divider
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        g.text(this.font, Component.literal("ShoreSMP"), left + 16, top + 16, 0xFFFFFFFF);
        g.text(this.font, Component.literal("TierTagger").withStyle(ChatFormatting.GRAY),
                left + 16, top + 29, 0xFFAAAAAA);

        String title;
        String hint;
        if (tab == TAB_SETTINGS) {
            title = "Settings";
            hint = "Client display controls";
        } else if (tab == TAB_PRIORITY) {
            title = "Gamemode priority";
            hint = "Higher wins when two tiers tie, and lists first";
        } else if (tab == TAB_COLORS) {
            title = "Tier colors";
            hint = "Type a 6-letter hex color for each tier";
        } else if (tab == TAB_LEADERBOARD) {
            title = "";
            hint = "";
        } else {
            title = "Search player";
            hint = "Look up a player's tiers";
        }
        if (!title.isEmpty()) {
            g.text(this.font, Component.literal(title), contentX, top + 16, 0xFFFFFFFF);
        }
        if (!hint.isEmpty()) {
            g.text(this.font, Component.literal(hint).withStyle(ChatFormatting.GRAY),
                    contentX, top + 30, 0xFFAAAAAA);
        }

        for (LbRow lb : lbRows) {
            drawLeaderboardRow(g, lb);
        }
        for (ColHeader h : colHeaders) {
            drawColumnHeader(g, h);
        }
        for (BoardLine line : boardLines) {
            drawBoardLine(g, line);
        }
        if (tab == TAB_LEADERBOARD && lbMaxScroll > 0) drawScrollbar(g, top + 54);
        for (Label l : labels) {
            g.text(this.font, l.text(), l.x(), l.y(), 0xFFFFFFFF);
        }
        for (Swatch s : swatches) {
            g.fill(s.x() - 1, s.y() - 1, s.x() + 13, s.y() + 13, 0xFF555566);
            g.fill(s.x(), s.y(), s.x() + 12, s.y() + 12, 0xFF000000 | TierConfig.color(s.key()));
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}