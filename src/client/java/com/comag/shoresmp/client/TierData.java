package com.comag.shoresmp.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class TierData {

    // Best to worst
    private static final String[] TIERS = {"HT1", "LT1", "HT2", "LT2", "HT3", "LT3", "HT4", "LT4", "HT5", "LT5"};

    // Points for each tier, in the same order as TIERS (HT1 = 60 ... LT5 = 1)
    private static final int[] POINTS = {60, 45, 30, 20, 10, 6, 4, 3, 2, 1};

    // Rank titles by total points
    private static final int[] TITLE_MIN = {0, 10, 20, 50, 100, 150, 250};
    private static final String[] TITLE_NAMES = {
            "Rookie", "Combat Novice", "Combat Cadet", "Combat Specialist",
            "Combat Ace", "Combat Master", "Combat Grandmaster"};
    private static final int[] TITLE_COLORS = {0xC9CCD6, 0xAD9BFF, 0xB59CFF, 0xC8A2FF, 0xFF7A90, 0xFFD23F, 0xFFE24A};

    // FAKE regions for testing. Later this comes from the bot.
    private static final String[] REGIONS = {"North America", "Europe", "Asia", "Oceania", "South America"};

    // Same order as the icons in icons.json
    public static final String[] MODE_NAMES = {"Crystal", "SMP", "Mace", "NethOP", "Sword"};
    private static final char[] ICONS = {'\uE000', '\uE001', '\uE002', '\uE003', '\uE004'};

    // Colour of each gamemode name in /tiertagger and the settings screen
    private static final int[] MODE_COLORS = {0xFF55FF, 0xFFD633, 0xAAAAAA, 0xFF7F50, 0x6BFFE6};

    // Gradient for the "Searching..." and "Rankings for" lines: dark ocean blue -> light sky cyan
    private static final int GRADIENT_START = 0x1F57C9;
    private static final int GRADIENT_END = 0x9BF6FF;

    private static final FontDescription ICON_FONT =
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("shoresmp-tiertagger", "icons"));

    // FAKE players for the sample leaderboard. Later this comes from the bot's API.
    private static final String[] SAMPLE_PLAYERS = {
            "ShoreKing", "CrystalCarl", "MaceMaster", "NethLord", "SwordSage", "Pixel", "Dusk", "Ember",
            "Frost", "Glitch", "Havoc", "Ion", "Jinx", "Kraken", "Lumen", "Mirage", "Nova", "Onyx",
            "Phantom", "Quartz", "Raven", "Storm", "Tundra", "Umbra", "Viper", "Wraith", "Xenon",
            "Yeti", "Zephyr", "Blaze"
    };

    // One gamemode a player has been tested in
    public record Entry(int mode, String tier, String peak, boolean retired) {}

    // One line of the leaderboard
    public record Row(String name, int points, Entry best, int modes) {}

    public static int modeColor(String modeName) {
        int i = Arrays.asList(MODE_NAMES).indexOf(modeName);
        return i < 0 ? 0xFFFFFF : MODE_COLORS[i];
    }

    public static int modeIndex(String modeName) {
        return Arrays.asList(MODE_NAMES).indexOf(modeName);
    }

    public static int pointsFor(String tier) {
        int i = Arrays.asList(TIERS).indexOf(tier);
        return i < 0 ? 0 : POINTS[i];
    }

    // "LT5 = 1, HT5 = 5, ... HT1 = 45", built from the points table
    public static String pointsLegend() {
        StringBuilder sb = new StringBuilder();
        for (int i = TIERS.length - 1; i >= 0; i--) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(TIERS[i]).append(" = ").append(POINTS[i]);
        }
        return sb.toString();
    }

    // The points table split in two short lines so it fits on the screen
    public static String[] pointsLegendLines() {
        StringBuilder a = new StringBuilder();
        StringBuilder b = new StringBuilder();
        for (int i = TIERS.length - 1; i >= 0; i--) {
            StringBuilder target = i >= TIERS.length / 2 ? a : b;
            if (target.length() > 0) target.append("   ");
            target.append(TIERS[i]).append(" = ").append(POINTS[i]);
        }
        return new String[]{a.toString(), b.toString()};
    }

    // ---------- real data (from the bot's API) or fake sample data ----------

    private static boolean sample() {
        return TierConfig.get().useSampleData;
    }

    // The player's real profile if we have it right now (never waits), otherwise null
    private static TierApi.Profile profile(String playerName) {
        TierApi.Lookup l = TierApi.lookup(playerName);
        return l.state() == TierApi.State.FOUND ? l.profile() : null;
    }

    // LOADING, FOUND, NOT_FOUND or ERROR (sample data always counts as found)
    public static TierApi.State state(String playerName) {
        if (sample()) return rawEntries(playerName).isEmpty() ? TierApi.State.NOT_FOUND : TierApi.State.FOUND;
        return TierApi.lookup(playerName).state();
    }

    private static List<Entry> fromProfile(TierApi.Profile p) {
        List<Entry> list = new ArrayList<>();
        for (java.util.Map.Entry<String, TierApi.ModeData> e : p.rankings().entrySet()) {
            int mode = -1;
            for (int i = 0; i < MODE_NAMES.length; i++) {
                if (MODE_NAMES[i].equalsIgnoreCase(e.getKey())) mode = i;
            }
            if (mode < 0) continue; // a gamemode the mod doesn't know about
            TierApi.ModeData d = e.getValue();
            list.add(new Entry(mode, d.tier(), d.peak(), d.retired()));
        }
        return list;
    }

    // Every gamemode the player has been tested in, ignoring the user's settings.
    private static List<Entry> rawEntries(String playerName) {
        if (!sample()) {
            TierApi.Profile p = profile(playerName);
            return p == null ? new ArrayList<>() : fromProfile(p);
        }
        return fakeEntries(playerName);
    }

    // FAKE data for testing (Sample data setting)
    private static List<Entry> fakeEntries(String playerName) {
        List<Entry> list = new ArrayList<>();
        String key = playerName.toLowerCase();
        if (Math.floorMod(key.hashCode(), 5) == 0) return list; // fake: "never tiertested"
        for (int m = 0; m < MODE_NAMES.length; m++) {
            int hash = (key + "#" + MODE_NAMES[m]).hashCode();
            int h = Math.floorMod(hash, TIERS.length + 4);
            if (h >= TIERS.length) continue; // not tested in this gamemode
            int peakIdx = Math.floorMod(hash / 31, h + 1); // peak is the same or better than current
            // Only HT1, LT1, HT2, LT2 (the first 4 tiers) can retire
            boolean retired = h < 4 && Math.floorMod(hash / 7, 4) == 0;
            list.add(new Entry(m, TIERS[h], TIERS[peakIdx], retired));
        }
        return list;
    }

    // Has this player been tier tested at all?
    public static boolean exists(String playerName) {
        return !rawEntries(playerName).isEmpty();
    }

    // The gamemodes to show for a player: respects the Retired setting, in the user's priority order
    public static List<Entry> entriesFor(String playerName) {
        TierConfig cfg = TierConfig.get();
        List<Entry> list = new ArrayList<>();
        for (Entry e : rawEntries(playerName)) {
            if (e.retired() && !cfg.retired) continue; // "Retired" setting is OFF
            list.add(e);
        }
        List<String> priority = cfg.priority;
        list.sort(Comparator.comparingInt((Entry e) -> priority.indexOf(MODE_NAMES[e.mode()])));
        return list;
    }

    // The player's entry in one gamemode, or null if they haven't been tested there
    public static Entry entryFor(String playerName, int mode) {
        for (Entry e : entriesFor(playerName)) {
            if (e.mode() == mode) return e;
        }
        return null;
    }

    // The first retired gamemode of a player, or null if they have none
    public static Entry retiredEntry(String playerName) {
        for (Entry e : entriesFor(playerName)) {
            if (e.retired()) return e;
        }
        return null;
    }

    // Total points: every gamemode's current tier added up (retired tiers count too)
    public static int totalPoints(String playerName) {
        if (!sample()) {
            TierApi.Profile p = profile(playerName);
            return p == null ? 0 : p.points();
        }
        int total = 0;
        for (Entry e : rawEntries(playerName)) total += pointsFor(e.tier());
        return total;
    }

    // Global position: 1 + how many sample players have more points (FAKE until the bot's API exists)
    public static int rankFor(String playerName) {
        if (!sample()) {
            TierApi.Profile p = profile(playerName);
            return p == null ? 0 : p.rank();
        }
        int points = totalPoints(playerName);
        int rank = 1;
        for (Row r : leaderboard()) {
            if (r.points() > points) rank++;
        }
        return rank;
    }

    // Rank title ("Rookie" ... "Combat Grandmaster") for a points total
    public static int titleIndex(int points) {
        int idx = 0;
        for (int i = 0; i < TITLE_MIN.length; i++) {
            if (points >= TITLE_MIN[i]) idx = i;
        }
        return idx;
    }

    public static String titleName(int points) {
        return TITLE_NAMES[titleIndex(points)];
    }

    public static int titleColor(int points) {
        return TITLE_COLORS[titleIndex(points)];
    }

    // Banner colour behind a leaderboard rank: gold, silver, bronze, then dark
    public static int rankColor(int rank) {
        if (rank == 1) return 0xE5B84A;
        if (rank == 2) return 0xB8C4D0;
        if (rank == 3) return 0xD9822B;
        return 0x1E2434;
    }

    // FAKE region for testing
    public static String regionFor(String playerName) {
        if (!sample()) {
            TierApi.Profile p = profile(playerName);
            String code = p == null || p.region() == null ? "" : p.region().toUpperCase(Locale.ROOT);
            return switch (code) {
                case "NA" -> "North America";
                case "EU" -> "Europe";
                case "AS" -> "Asia";
                case "AU" -> "Oceania";
                case "SA" -> "South America";
                case "AF" -> "Africa";
                default -> "Unknown";
            };
        }
        return REGIONS[Math.floorMod(playerName.toLowerCase().hashCode() / 3, REGIONS.length)];
    }

    // "NA", "EU", ...
    public static String regionCode(String playerName) {
        return switch (regionFor(playerName)) {
            case "North America" -> "NA";
            case "Europe" -> "EU";
            case "Asia" -> "AS";
            case "Oceania" -> "OC";
            case "Africa" -> "AF";
            case "Unknown" -> "?";
            default -> "SA";
        };
    }

    public static int regionTextColor(String code) {
        return switch (code) {
            case "NA" -> 0xFF5A5A;
            case "EU" -> 0x5ADC78;
            case "AS" -> 0xC08CFF;
            case "OC" -> 0x5AC8DC;
            default -> 0xFFD25A;
        };
    }

    public static int regionBgColor(String code) {
        return switch (code) {
            case "NA" -> 0x3A1A24;
            case "EU" -> 0x16331F;
            case "AS" -> 0x2B2040;
            case "OC" -> 0x153037;
            default -> 0x38301A;
        };
    }

    // The sample leaderboard, highest points first
    public static List<Row> leaderboard() {
        List<String> order = Arrays.asList(TIERS);
        List<Row> rows = new ArrayList<>();
        if (!sample()) {
            TierApi.PageLookup pl = TierApi.pageLookup(1, 100); // top 100, refreshed every 20 seconds
            if (pl.state() != TierApi.State.FOUND) return rows;
            for (TierApi.Profile p : pl.page().players()) {
                Entry best = null;
                int count = 0;
                for (Entry e : fromProfile(p)) {
                    count++;
                    if (best == null || order.indexOf(e.tier()) < order.indexOf(best.tier())) best = e;
                }
                if (best != null) rows.add(new Row(p.name(), p.points(), best, count));
            }
            return rows;
        }
        for (String name : SAMPLE_PLAYERS) {
            List<Entry> entries = rawEntries(name);
            if (entries.isEmpty()) continue;
            int points = 0;
            Entry best = null;
            for (Entry e : entries) {
                points += pointsFor(e.tier());
                if (best == null || order.indexOf(e.tier()) < order.indexOf(best.tier())) best = e;
            }
            rows.add(new Row(name, points, best, entries.size()));
        }
        rows.sort(Comparator.comparingInt((Row r) -> -r.points()).thenComparing(Row::name));
        return rows;
    }

    // LOADING while the leaderboard is being fetched, ERROR if the bot can't be reached
    public static TierApi.State leaderboardState() {
        if (sample()) return TierApi.State.FOUND;
        return TierApi.pageLookup(1, 100).state();
    }

    // One player in a gamemode board (Tier 1 to Tier 5 columns)
    public record BoardEntry(String name, String tier, boolean retired) {}

    // LOADING, FOUND or ERROR for one gamemode's board
    public static TierApi.State boardState(String modeName) {
        if (sample()) return TierApi.State.FOUND;
        return TierApi.boardLookup(modeName).state();
    }

    // The 5 columns (Tier 1 to Tier 5) of one gamemode. HT players come first and LT players last in each column.
    public static List<List<BoardEntry>> board(String modeName) {
        boolean showRetired = TierConfig.get().retired;
        List<List<BoardEntry>> cols = new ArrayList<>();
        for (int i = 0; i < 5; i++) cols.add(new ArrayList<>());

        if (!sample()) {
            TierApi.BoardLookup bl = TierApi.boardLookup(modeName);
            if (bl.state() != TierApi.State.FOUND) return cols;
            for (int t = 0; t < 5 && t < bl.board().tiers().size(); t++) {
                for (TierApi.BoardPlayer p : bl.board().tiers().get(t)) {
                    if (p.retired() && !showRetired) continue;
                    cols.get(t).add(new BoardEntry(p.name(), p.tier(), p.retired()));
                }
            }
            return cols;
        }

        int mode = modeIndex(modeName);
        for (String name : SAMPLE_PLAYERS) {
            for (Entry e : rawEntries(name)) {
                if (e.mode() != mode || (e.retired() && !showRetired)) continue;
                int n = Integer.parseInt(e.tier().substring(2));
                cols.get(n - 1).add(new BoardEntry(name, e.tier(), e.retired()));
            }
        }
        for (List<BoardEntry> col : cols) {
            col.sort(Comparator.comparingInt((BoardEntry b) -> b.tier().startsWith("HT") ? 0 : 1).thenComparing(BoardEntry::name));
        }
        return cols;
    }

    // "HT1" or "RHT1"
    public static String label(Entry e) {
        return e.retired() ? "R" + e.tier() : e.tier();
    }

    // Colour for a label like "HT1" or "RLT2", from the settings
    private static int colorFor(String label) {
        return TierConfig.color(label.startsWith("R") ? "Retired" : label);
    }

    // Colour of an entry's tier label, from the settings
    public static int colorOf(Entry e) {
        return colorFor(label(e));
    }

    private static MutableComponent colored(String text, int color) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(color));
    }

    // Just the gamemode icon (always shown, ignores the Icons setting)
    public static MutableComponent modeIcon(int mode) {
        return Component.literal(String.valueOf(ICONS[mode])).withStyle(Style.EMPTY.withFont(ICON_FONT));
    }

    // Adds "[icon] " to a line, unless icons are turned off
    private static void addIcon(MutableComponent target, int mode) {
        if (!TierConfig.get().icons) return;
        target.append(modeIcon(mode));
        target.append(Component.literal(" "));
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    // Text that fades from one colour to another, left to right, one letter at a time
    public static MutableComponent gradient(String text, int from, int to) {
        MutableComponent out = Component.literal("");
        int n = text.length();
        for (int i = 0; i < n; i++) {
            float t = n <= 1 ? 0f : (float) i / (n - 1);
            int r = lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
            int g = lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
            int b = lerp(from & 0xFF, to & 0xFF, t);
            out.append(colored(String.valueOf(text.charAt(i)), (r << 16) | (g << 8) | b));
        }
        return out;
    }

    // The ocean-blue to sky-cyan gradient used by /tiertagger
    public static MutableComponent gradient(String text) {
        return gradient(text, GRADIENT_START, GRADIENT_END);
    }

    // The player's highest tier. On a tie, the gamemode higher in the priority list wins.
    private static Entry bestEntry(String playerName) {
        List<String> order = Arrays.asList(TIERS);
        Entry best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Entry e : entriesFor(playerName)) { // already sorted by priority
            int score = order.indexOf(e.tier());
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    // "[icon] HT3 | " shown in the tab list, or null if the player has no tag (or the tab list is off)
    public static MutableComponent tagFor(String playerName) {
        TierConfig cfg = TierConfig.get();
        if (!cfg.modEnabled || !cfg.tablist) return null;

        Entry best = bestEntry(playerName);
        if (best == null) return null;

        // Empty parent, so nothing inherits the icon font
        MutableComponent tag = Component.literal("");
        addIcon(tag, best.mode());
        tag.append(colored(label(best), colorFor(label(best))));
        tag.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
        return tag;
    }

    // The chat lines for /tiertagger <name>
    public static List<Component> infoLines(String playerName) {
        TierConfig cfg = TierConfig.get();
        List<Component> lines = new ArrayList<>();

        List<Entry> entries = entriesFor(playerName);
        if (entries.isEmpty()) {
            TierApi.State st = state(playerName);
            if (st == TierApi.State.LOADING) {
                lines.add(Component.literal("Still loading, try again in a second.").withStyle(ChatFormatting.GRAY));
            } else if (st == TierApi.State.ERROR) {
                lines.add(Component.literal("Could not reach the tier server.").withStyle(ChatFormatting.RED));
            } else {
                lines.add(Component.literal("Could not find player " + playerName).withStyle(ChatFormatting.RED));
            }
            return lines;
        }

        lines.add(gradient("=== Rankings for " + playerName + " ==="));
        for (Entry e : entries) {
            MutableComponent line = Component.literal("");
            addIcon(line, e.mode());
            line.append(colored(MODE_NAMES[e.mode()], MODE_COLORS[e.mode()]));
            line.append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
            line.append(colored(label(e), colorFor(label(e))));
            if (cfg.showPeaks && !e.peak().equals(e.tier())) {
                line.append(Component.literal(" (peak: ").withStyle(ChatFormatting.GRAY));
                line.append(colored(e.peak(), colorFor(e.peak())));
                line.append(Component.literal(")").withStyle(ChatFormatting.GRAY));
            }
            lines.add(line);
        }
        return lines;
    }

    // Like infoLines, but waits (in the background) for the answer from the bot first
    public static CompletableFuture<List<Component>> infoLinesAsync(String playerName) {
        if (sample()) return CompletableFuture.completedFuture(infoLines(playerName));
        return TierApi.fetch(playerName).thenApply(l -> infoLines(playerName));
    }
}