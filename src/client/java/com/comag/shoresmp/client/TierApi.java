package com.comag.shoresmp.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

// Talks to the bot's web API and remembers the answers for a while (a "cache").
// Nothing here ever waits on the game thread: lookups start in the background and
// the screens show "Loading..." until the answer arrives.
public final class TierApi {

    private TierApi() {}

    public enum State { LOADING, FOUND, NOT_FOUND, ERROR }

    // One player's tier in one gamemode
    public record ModeData(String tier, String peak, boolean retired) {}

    // Everything the API knows about one player (gamemode name -> tier)
    public record Profile(String name, String uuid, String region, int points, int rank,
                          Map<String, ModeData> rankings) {}

    public record Lookup(State state, Profile profile, long at) {}

    public record Page(int page, int pages, int total, List<Profile> players) {}

    public record PageLookup(State state, Page page, long at) {}

    // One gamemode as Tier 1 to Tier 5 columns (HT players first, LT players last in every column)
    public record BoardPlayer(String name, String tier, boolean retired, String region) {}

    public record Board(String gamemode, int total, List<List<BoardPlayer>> tiers) {}

    public record BoardLookup(State state, Board board, long at) {}

    private static final Lookup LOADING_LOOKUP = new Lookup(State.LOADING, null, 0);
    private static final PageLookup LOADING_PAGE = new PageLookup(State.LOADING, null, 0);
    private static final BoardLookup LOADING_BOARD = new BoardLookup(State.LOADING, null, 0);

    private static final long OK_MS = 5 * 60_000L;         // keep a found player for 5 minutes
    private static final long NOT_FOUND_MS = 2 * 60_000L;  // remember "not found" for 2 minutes
    private static final long ERROR_MS = 15_000L;          // try again after a failure in 15 seconds
    private static final long PAGE_OK_MS = 20_000L;        // refresh the leaderboard every 20 seconds

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private static final Map<String, Lookup> PROFILES = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Lookup>> PROFILE_FETCHES = new ConcurrentHashMap<>();
    private static final Map<String, PageLookup> PAGES = new ConcurrentHashMap<>();
    private static final Set<String> PAGE_FETCHES = ConcurrentHashMap.newKeySet();
    private static final Map<String, BoardLookup> BOARDS = new ConcurrentHashMap<>();
    private static final Set<String> BOARD_FETCHES = ConcurrentHashMap.newKeySet();

    // ---------- settings ----------

    private static String baseUrl() {
        String url = TierConfig.get().apiUrl;
        if (url == null) return "";
        url = url.trim();
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    public static void clearCache() {
        PROFILES.clear();
        PAGES.clear();
        BOARDS.clear();
    }

    // ---------- one player ----------

    private static boolean fresh(Lookup l) {
        long age = System.currentTimeMillis() - l.at();
        return switch (l.state()) {
            case FOUND -> age < OK_MS;
            case NOT_FOUND -> age < NOT_FOUND_MS;
            case ERROR -> age < ERROR_MS;
            default -> false;
        };
    }

    // Never waits. Returns what we know right now and starts a refresh in the background if needed.
    public static Lookup lookup(String name) {
        Lookup cached = PROFILES.get(name.toLowerCase(Locale.ROOT));
        if (cached != null && fresh(cached)) return cached;
        fetch(name);
        if (cached != null && cached.state() == State.FOUND) return cached; // show old data while refreshing
        return LOADING_LOOKUP;
    }

    // Asks the API (or uses a fresh cached answer). The future finishes with FOUND, NOT_FOUND or ERROR.
    public static CompletableFuture<Lookup> fetch(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        Lookup cached = PROFILES.get(key);
        if (cached != null && fresh(cached)) return CompletableFuture.completedFuture(cached);

        CompletableFuture<Lookup> mine = new CompletableFuture<>();
        CompletableFuture<Lookup> running = PROFILE_FETCHES.putIfAbsent(key, mine);
        if (running != null) return running; // already on its way

        String path = "/api/profile/" + URLEncoder.encode(name, StandardCharsets.UTF_8);
        request(path).handle((response, error) -> {
            long now = System.currentTimeMillis();
            Lookup result;
            if (error != null) {
                result = new Lookup(State.ERROR, null, now);
            } else if (response.statusCode() == 200) {
                try {
                    Profile p = parseProfile(JsonParser.parseString(response.body()).getAsJsonObject());
                    result = new Lookup(State.FOUND, p, now);
                } catch (Exception e) {
                    result = new Lookup(State.ERROR, null, now);
                }
            } else if (response.statusCode() == 404) {
                result = new Lookup(State.NOT_FOUND, null, now);
            } else {
                result = new Lookup(State.ERROR, null, now);
            }
            PROFILES.put(key, result);
            PROFILE_FETCHES.remove(key);
            mine.complete(result);
            return null;
        });
        return mine;
    }

    // ---------- leaderboard ----------

    public static PageLookup pageLookup(int page, int perPage) {
        String key = page + "/" + perPage;
        PageLookup cached = PAGES.get(key);
        if (cached != null) {
            long age = System.currentTimeMillis() - cached.at();
            long limit = cached.state() == State.FOUND ? PAGE_OK_MS : ERROR_MS;
            if (age < limit) return cached;
        }
        fetchPage(page, perPage, key);
        if (cached != null && cached.state() == State.FOUND) return cached; // show the old page while refreshing
        return LOADING_PAGE;
    }

    private static void fetchPage(int page, int perPage, String key) {
        if (!PAGE_FETCHES.add(key)) return; // already on its way

        request("/api/leaderboard?page=" + page + "&per_page=" + perPage).handle((response, error) -> {
            long now = System.currentTimeMillis();
            PageLookup result;
            if (error != null || response.statusCode() != 200) {
                result = new PageLookup(State.ERROR, null, now);
            } else {
                try {
                    JsonObject o = JsonParser.parseString(response.body()).getAsJsonObject();
                    List<Profile> players = new ArrayList<>();
                    JsonArray arr = o.getAsJsonArray("players");
                    for (JsonElement e : arr) players.add(parseProfile(e.getAsJsonObject()));
                    Page p = new Page(o.get("page").getAsInt(), o.get("pages").getAsInt(),
                            o.get("total").getAsInt(), players);
                    // Remember each player too, so their rows can show tiers without extra requests
                    for (Profile pl : players) {
                        PROFILES.put(pl.name().toLowerCase(Locale.ROOT), new Lookup(State.FOUND, pl, now));
                    }
                    result = new PageLookup(State.FOUND, p, now);
                } catch (Exception e) {
                    result = new PageLookup(State.ERROR, null, now);
                }
            }
            PAGES.put(key, result);
            PAGE_FETCHES.remove(key);
            return null;
        });
    }

    // ---------- one gamemode (Tier 1 to Tier 5 columns) ----------

    public static BoardLookup boardLookup(String gamemode) {
        String key = gamemode.toLowerCase(Locale.ROOT);
        BoardLookup cached = BOARDS.get(key);
        if (cached != null) {
            long age = System.currentTimeMillis() - cached.at();
            long limit = cached.state() == State.FOUND ? PAGE_OK_MS : ERROR_MS;
            if (age < limit) return cached;
        }
        fetchBoard(gamemode, key);
        if (cached != null && cached.state() == State.FOUND) return cached; // show the old board while refreshing
        return LOADING_BOARD;
    }

    private static void fetchBoard(String gamemode, String key) {
        if (!BOARD_FETCHES.add(key)) return; // already on its way

        request("/api/gamemode/" + URLEncoder.encode(gamemode, StandardCharsets.UTF_8)).handle((response, error) -> {
            long now = System.currentTimeMillis();
            BoardLookup result;
            if (error != null || response.statusCode() != 200) {
                result = new BoardLookup(State.ERROR, null, now);
            } else {
                try {
                    JsonObject o = JsonParser.parseString(response.body()).getAsJsonObject();
                    List<List<BoardPlayer>> tiers = new ArrayList<>();
                    for (JsonElement t : o.getAsJsonArray("tiers")) {
                        List<BoardPlayer> column = new ArrayList<>();
                        for (JsonElement pe : t.getAsJsonObject().getAsJsonArray("players")) {
                            JsonObject po = pe.getAsJsonObject();
                            column.add(new BoardPlayer(
                                    po.get("name").getAsString(),
                                    po.get("tier").getAsString(),
                                    po.get("retired").getAsBoolean(),
                                    text(po, "region")));
                        }
                        tiers.add(column);
                    }
                    result = new BoardLookup(State.FOUND, new Board(o.get("gamemode").getAsString(), o.get("total").getAsInt(), tiers), now);
                } catch (Exception e) {
                    result = new BoardLookup(State.ERROR, null, now);
                }
            }
            BOARDS.put(key, result);
            BOARD_FETCHES.remove(key);
            return null;
        });
    }

    // ---------- plumbing ----------

    private static CompletableFuture<HttpResponse<String>> request(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                    .timeout(Duration.ofSeconds(6))
                    .header("Accept", "application/json")
                    .header("User-Agent", "ShoreSMP-TierTagger")
                    .header("ngrok-skip-browser-warning", "true")
                    .GET()
                    .build();
            return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            // A bad address ends up here
            return CompletableFuture.failedFuture(e);
        }
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static Profile parseProfile(JsonObject o) {
        Map<String, ModeData> rankings = new LinkedHashMap<>();
        JsonObject r = o.getAsJsonObject("rankings");
        for (Map.Entry<String, JsonElement> e : r.entrySet()) {
            JsonObject m = e.getValue().getAsJsonObject();
            rankings.put(e.getKey(), new ModeData(
                    m.get("tier").getAsString(),
                    m.get("peak").getAsString(),
                    m.get("retired").getAsBoolean()));
        }
        return new Profile(
                o.get("name").getAsString(),
                text(o, "uuid"),
                text(o, "region"),
                o.get("points").getAsInt(),
                o.get("rank").getAsInt(),
                rankings);
    }
}