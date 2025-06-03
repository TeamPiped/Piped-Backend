package me.kavin.piped.consts;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.minio.MinioClient;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.kavin.piped.utils.PageMixin;
import me.kavin.piped.utils.resp.ListLinkHandlerMixin;
import okhttp3.OkHttpClient;
import okhttp3.brotli.BrotliInterceptor;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.localization.ContentCountry;

import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit; // Added import

public class Constants {

    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0";

    public static final int PORT;
    public static final String HTTP_WORKERS;

    public static final String PROXY_PART;

    public static final String IMAGE_PROXY_PART;

    public static final byte[] PROXY_HASH_SECRET;

    public static final String CAPTCHA_BASE_URL, CAPTCHA_API_KEY;

    public static final StreamingService YOUTUBE_SERVICE;

    public static final String PUBLIC_URL;

    public static final String PUBSUB_URL;

    public static final String PUBSUB_HUB_URL;

    public static final String REQWEST_PROXY;
    public static final String REQWEST_PROXY_USER;
    public static final String REQWEST_PROXY_PASS;

    public static final String FRONTEND_URL;

    public static final OkHttpClient h2client;
    public static final OkHttpClient h2_no_redir_client;

    public static final boolean COMPROMISED_PASSWORD_CHECK;

    public static final boolean DISABLE_REGISTRATION;

    public static final int FEED_RETENTION;

    public static final boolean DISABLE_TIMERS;

    public static final String RYD_PROXY_URL;

    public static final List<String> SPONSORBLOCK_SERVERS;

    public static final boolean DISABLE_RYD;

    public static final boolean DISABLE_SERVER;

    public static final boolean DISABLE_LBRY;

    public static final int SUBSCRIPTIONS_EXPIRY;

    public static final boolean CONSENT_COOKIE;

    public static final String SENTRY_DSN;

    public static final String S3_ENDPOINT;

    public static final String S3_ACCESS_KEY;

    public static final String S3_SECRET_KEY;

    public static final String S3_BUCKET;

    public static final MinioClient S3_CLIENT;

    public static final String MATRIX_ROOM = "#piped-events:matrix.org";

    public static final String MATRIX_SERVER;

    public static final String MATRIX_TOKEN;

    public static final String GEO_RESTRICTION_CHECKER_URL;

    public static final String BG_HELPER_URL;

    public static String YOUTUBE_COUNTRY;

    public static final String VERSION;

    // --- Polling Configuration ---
    public static final boolean ENABLE_FEED_POLLING;
    public static final int POLLING_INTERVAL_MINUTES;
    public static final int POLLING_FETCH_LIMIT_PER_CHANNEL;
    // --- End Polling Configuration ---

    public static final ObjectMapper mapper = JsonMapper.builder()
            .addMixIn(Page.class, PageMixin.class)
            .addMixIn(ListLinkHandler.class, ListLinkHandlerMixin.class)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    public static final Object2ObjectOpenHashMap<String, String> hibernateProperties = new Object2ObjectOpenHashMap<>();

    public static final ObjectNode frontendProperties = mapper.createObjectNode();

    static {
        Properties prop = new Properties();
        try {
            YOUTUBE_SERVICE = NewPipe.getService(0);

            if (new File("config.properties").exists()) {
                prop.load(new FileReader("config.properties"));
            }

            PORT = Integer.parseInt(getProperty(prop, "PORT", "8080"));
            HTTP_WORKERS = getProperty(prop, "HTTP_WORKERS",
                    String.valueOf(Runtime.getRuntime().availableProcessors()));
            PROXY_PART = getProperty(prop, "PROXY_PART");
            IMAGE_PROXY_PART = getProperty(prop, "IMAGE_PROXY_PART", PROXY_PART);
            PROXY_HASH_SECRET = Optional.ofNullable(getProperty(prop, "PROXY_HASH_SECRET")).map(s -> s.getBytes(StandardCharsets.UTF_8)).orElse(null);
            CAPTCHA_BASE_URL = getProperty(prop, "CAPTCHA_BASE_URL");
            CAPTCHA_API_KEY = getProperty(prop, "CAPTCHA_API_KEY");
            PUBLIC_URL = getProperty(prop, "API_URL");
            PUBSUB_URL = getProperty(prop, "PUBSUB_URL", PUBLIC_URL);
            PUBSUB_HUB_URL = getProperty(prop, "PUBSUB_HUB_URL", "https://pubsubhubbub.appspot.com/subscribe");
            REQWEST_PROXY = getProperty(prop, "REQWEST_PROXY");
            REQWEST_PROXY_USER = getProperty(prop, "REQWEST_PROXY_USER");
            REQWEST_PROXY_PASS = getProperty(prop, "REQWEST_PROXY_PASS");
            FRONTEND_URL = getProperty(prop, "FRONTEND_URL", "https://piped.video");
            COMPROMISED_PASSWORD_CHECK = Boolean.parseBoolean(getProperty(prop, "COMPROMISED_PASSWORD_CHECK", "true"));
            DISABLE_REGISTRATION = Boolean.parseBoolean(getProperty(prop, "DISABLE_REGISTRATION", "false"));
            FEED_RETENTION = Integer.parseInt(getProperty(prop, "FEED_RETENTION", "30"));
            DISABLE_TIMERS = Boolean.parseBoolean(getProperty(prop, "DISABLE_TIMERS", "false"));
            RYD_PROXY_URL = getProperty(prop, "RYD_PROXY_URL", "https://ryd-proxy.kavin.rocks");
            SPONSORBLOCK_SERVERS = List.of(getProperty(prop, "SPONSORBLOCK_SERVERS", "https://sponsor.ajay.app,https://sponsorblock.kavin.rocks")
                    .split(","));
            DISABLE_RYD = Boolean.parseBoolean(getProperty(prop, "DISABLE_RYD", "false"));
            DISABLE_SERVER = Boolean.parseBoolean(getProperty(prop, "DISABLE_SERVER", "false"));
            DISABLE_LBRY = Boolean.parseBoolean(getProperty(prop, "DISABLE_LBRY", "false"));
            SUBSCRIPTIONS_EXPIRY = Integer.parseInt(getProperty(prop, "SUBSCRIPTIONS_EXPIRY", "30"));
            CONSENT_COOKIE = Boolean.parseBoolean(getProperty(prop, "CONSENT_COOKIE", "true"));
            SENTRY_DSN = getProperty(prop, "SENTRY_DSN", "");
            S3_ENDPOINT = getProperty(prop, "S3_ENDPOINT");
            S3_ACCESS_KEY = getProperty(prop, "S3_ACCESS_KEY");
            S3_SECRET_KEY = getProperty(prop, "S3_SECRET_KEY");
            S3_BUCKET = getProperty(prop, "S3_BUCKET");
            if (S3_ENDPOINT != null) {
                S3_CLIENT = MinioClient.builder()
                        .endpoint(S3_ENDPOINT)
                        .credentials(S3_ACCESS_KEY, S3_SECRET_KEY)
                        .build();
            } else {
                S3_CLIENT = null;
            }
            System.getenv().forEach((key, value) -> {
                if (key.startsWith("hibernate"))
                    hibernateProperties.put(key, value);
            });
            MATRIX_SERVER = getProperty(prop, "MATRIX_SERVER", "https://matrix-client.matrix.org");
            MATRIX_TOKEN = getProperty(prop, "MATRIX_TOKEN");
            GEO_RESTRICTION_CHECKER_URL = getProperty(prop, "GEO_RESTRICTION_CHECKER_URL");
            BG_HELPER_URL = getProperty(prop, "BG_HELPER_URL");

            // --- Polling Configuration ---
            ENABLE_FEED_POLLING = Boolean.parseBoolean(getProperty(prop, "ENABLE_FEED_POLLING", "false"));
            POLLING_INTERVAL_MINUTES = Integer.parseInt(getProperty(prop, "POLLING_INTERVAL_MINUTES", "15"));
            POLLING_FETCH_LIMIT_PER_CHANNEL = Integer.parseInt(getProperty(prop, "POLLING_FETCH_LIMIT_PER_CHANNEL", "10"));
            // --- End Polling Configuration ---

            prop.forEach((_key, _value) -> {
                String key = String.valueOf(_key), value = String.valueOf(_value);
                if (key.startsWith("hibernate"))
                    hibernateProperties.put(key, value);
                else if (key.startsWith("frontend."))
                    frontendProperties.put(StringUtils.substringAfter(key, "frontend."), value);
            });
            frontendProperties.put("imageProxyUrl", IMAGE_PROXY_PART);
            frontendProperties.putArray("countries").addAll(
                    YOUTUBE_SERVICE.getSupportedCountries().stream().map(ContentCountry::getCountryCode)
                            .map(JsonNodeFactory.instance::textNode).toList()
            );
            frontendProperties.put("s3Enabled", S3_CLIENT != null);
            frontendProperties.put("registrationDisabled", DISABLE_REGISTRATION);

            // transform hibernate properties for legacy configurations
            hibernateProperties.replace("hibernate.dialect",
                    "org.hibernate.dialect.PostgreSQL10Dialect",
                    "org.hibernate.dialect.PostgreSQLDialect"
            );

            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .followRedirects(true)
                    .addInterceptor(BrotliInterceptor.INSTANCE);
            OkHttpClient.Builder builder_noredir = new OkHttpClient.Builder()
                    .followRedirects(false)
                    .addInterceptor(BrotliInterceptor.INSTANCE);
            h2client = builder.build();
            h2_no_redir_client = builder_noredir.build();
            VERSION = new File("VERSION").exists() ?
                    IOUtils.toString(new FileReader("VERSION")) :
                    "unknown";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String getProperty(final Properties prop, String key) {
        return getProperty(prop, key, null);
    }

    private static String getProperty(final Properties prop, String key, String def) {

        final String envVal = System.getenv(key);

        if (envVal != null)
            return envVal;

        return prop.getProperty(key, def);
    }
}