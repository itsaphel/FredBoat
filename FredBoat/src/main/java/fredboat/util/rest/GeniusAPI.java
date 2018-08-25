package fredboat.util.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import fredboat.config.property.Credentials;
import fredboat.feature.metrics.Metrics;
import fredboat.metrics.OkHttpEventMetrics;
import fredboat.util.rest.models.lyrics.Lyrics;
import io.prometheus.client.guava.cache.CacheMetricsCollector;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GeniusAPI {

    private static final String TAG = "Genius";
    private static final Logger log = LoggerFactory.getLogger(GeniusAPI.class);
    private static final String GENIUS_BASE_URL = "https://genius.com";
    private static final String GENIUS_API_BASE_URL = "https://api.genius.com";
    private static final int MAX_CACHE_HOUR = 3;
    private final Credentials credentials;
    private Cache<String, Lyrics> lyricsCache;

    protected OkHttpClient geniusApiClient;
    private ObjectMapper objectMapper;


    public GeniusAPI(CacheMetricsCollector cacheMetrics, Credentials credentials) {
        this.credentials = credentials;
        geniusApiClient = Http.DEFAULT_BUILDER.newBuilder()
          .eventListener(new OkHttpEventMetrics("geniusApi", Metrics.httpEventCounter))
          .build();
        objectMapper = new ObjectMapper();

        lyricsCache = CacheBuilder.newBuilder()
          .recordStats()
          .maximumSize(10000)
          .expireAfterAccess(MAX_CACHE_HOUR, TimeUnit.HOURS)
          .build();
        cacheMetrics.addCache("geniusApi", lyricsCache);

        testGeniusApiCredentials();
    }

    private void testGeniusApiCredentials() {
        if ("".equals(credentials.getGeniusClientId()) || "".equals(credentials.getGeniusAccessToken())) {
            log.warn(TAG + ": API credentials not found. Lyrics command will not work.");
            return;
        }

        // todo fetch sample track
    }

    public Optional<Lyrics> getLyrics(AudioTrack track) {
        Lyrics lyrics = null;

        log.debug("Lyrics requested for " + track.getIdentifier());
        log.debug("Entry found in cache? " + String.valueOf(lyricsCache.asMap().containsKey(track.getIdentifier())));

        try {
            lyrics = lyricsCache.get(track.getIdentifier(), () -> fetchLyrics(track));
        } catch (ExecutionException e) {
            log.warn(TAG + ": " + e.getMessage(), e);
        }

        return Optional.ofNullable(lyrics);
    }

    private Lyrics fetchLyrics(AudioTrack track) {
        String lyricsUrl = getLyricsUrlFromGenius(track.getInfo().title, track.getInfo().author);
        Lyrics lyrics = null;

        try {
            Document doc = Jsoup.connect(GENIUS_BASE_URL + lyricsUrl).get();

            Element lyricsElement = doc.selectFirst(".lyrics");
            String lyricsString = lyricsElement.wholeText().trim();

            lyrics = new Lyrics();
            lyrics.setIdentifier(track.getIdentifier());
            lyrics.setTitle(track.getInfo().title);
            lyrics.setAuthor(track.getInfo().author);
            lyrics.setGeniusLyricsKey(lyricsUrl);
            lyrics.setLyrics(lyricsString);
        } catch (IOException e) {
            log.warn(TAG + ": " + e.getMessage() + " while fetching lyrics for track: " + track.getIdentifier(), e); // todo remove exception throw
        }

        return lyrics;
    }

    private String getLyricsUrlFromGenius(String title, String artist) {
        HttpUrl requestUrl = HttpUrl.parse(GENIUS_API_BASE_URL + "/search");
        String lyricsPath = "";
        String searchTerm = title + " " + artist;

        if (requestUrl == null) {
            return lyricsPath;
        }

        HttpUrl.Builder urlBuilder = requestUrl.newBuilder();
        urlBuilder.addQueryParameter("q", searchTerm);

        HttpUrl url = urlBuilder.build();
        Request request = new Request.Builder()
          .url(url)
          .addHeader("Authorization", "Bearer " + credentials.getGeniusAccessToken())
          .build();

        try (Response response = geniusApiClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();

            switch (response.code()) {
                case 200:
                    if (responseBody != null) {
                        JsonParser parser = new JsonParser();
                        String responseJson = responseBody.string();

                        Object obj = parser.parse(responseJson);

                        // use first result and pray
                        JsonObject songInfo = ((JsonObject) obj).get("response").getAsJsonObject().get("hits").getAsJsonArray().get(0).getAsJsonObject().get("result").getAsJsonObject();
                        lyricsPath = songInfo.get("path").getAsString();
                    }
                    break;

                default:
                    log.warn(TAG + " error code during search: " + response.code());
                    if (responseBody != null) {
                        log.warn(responseBody.string());
                    }
                    break;
            }
        } catch (IOException e) {
            log.warn(TAG + ": " + e.getMessage(), e);
        }

        return lyricsPath;
    }
}
