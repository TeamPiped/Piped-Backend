package me.kavin.piped.consts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.kavin.piped.utils.PageMixin;
import okhttp3.OkHttpClient;
import okhttp3.brotli.BrotliInterceptor;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;

import java.io.File;
import java.io.FileReader;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.util.List;
import java.util.Properties;

public class Constants {

    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0";

    public static final int PORT;
    public static final String HTTP_WORKERS;

    public static final String PROXY_PART;

    public static final String IMAGE_PROXY_PART;

    public static final String CAPTCHA_BASE_URL, CAPTCHA_API_KEY;

    public static final StreamingService YOUTUBE_SERVICE;

    public static final String PUBLIC_URL;

    public static final String PUBSUB_URL;

    public static final String PUBSUB_HUB_URL;

    public static final String HTTP_PROXY;

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

    public static final String SENTRY_DSN;

    public static final String VERSION;

    public static final ObjectMapper mapper = JsonMapper.builder()
            .addMixIn(Page.class, PageMixin.class)
            .build();

    public static final Object2ObjectOpenHashMap<String, String> hibernateProperties = new Object2ObjectOpenHashMap<>();

    public static final ObjectNode frontendProperties = mapper.createObjectNode();

    static {
        Properties prop = new Properties();
        try {
            YOUTUBE_SERVICE = NewPipe.getService(0);
            prop.load(new FileReader("config.properties"));

            PORT = Integer.parseInt(getProperty(prop, "PORT", "8080"));
            HTTP_WORKERS = getProperty(prop, "HTTP_WORKERS",
                    String.valueOf(Runtime.getRuntime().availableProcessors()));
            PROXY_PART = getProperty(prop, "PROXY_PART");
            IMAGE_PROXY_PART = getProperty(prop, "IMAGE_PROXY_PART", PROXY_PART);
            CAPTCHA_BASE_URL = getProperty(prop, "CAPTCHA_BASE_URL");
            CAPTCHA_API_KEY = getProperty(prop, "CAPTCHA_API_KEY");
            PUBLIC_URL = getProperty(prop, "API_URL");
            PUBSUB_URL = getProperty(prop, "PUBSUB_URL", PUBLIC_URL);
            PUBSUB_HUB_URL = getProperty(prop, "PUBSUB_HUB_URL", "https://pubsubhubbub.appspot.com/subscribe");
            HTTP_PROXY = getProperty(prop, "HTTP_PROXY");
            FRONTEND_URL = getProperty(prop, "FRONTEND_URL", "https://piped.kavin.rocks");
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
            SENTRY_DSN = getProperty(prop, "SENTRY_DSN", "");
            System.getenv().forEach((key, value) -> {
                if (key.startsWith("hibernate"))
                    hibernateProperties.put(key, value);
            });
            prop.forEach((_key, _value) -> {
                String key = String.valueOf(_key), value = String.valueOf(_value);
                if (key.startsWith("hibernate"))
                    hibernateProperties.put(key, value);
                else if (key.startsWith("frontend."))
                    frontendProperties.put(StringUtils.substringAfter(key, "frontend."), value);
            });

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
            if (HTTP_PROXY != null && HTTP_PROXY.contains(":")) {
                String host = StringUtils.substringBefore(HTTP_PROXY, ":");
                String port = StringUtils.substringAfter(HTTP_PROXY, ":");
                InetSocketAddress sa = new InetSocketAddress(host, Integer.parseInt(port));
                ProxySelector ps = ProxySelector.of(sa);
                ProxySelector.setDefault(ps);
            }
            h2client = builder.build();
            h2_no_redir_client = builder_noredir.build();
            VERSION = new File("VERSION").exists() ?
                    IOUtils.toString(new FileReader("VERSION")) :
                    "unknown";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final String getProperty(final Properties prop, String key) {
        return getProperty(prop, key, null);
    }

    private static final String getProperty(final Properties prop, String key, String def) {

        final String envVal = System.getenv(key);

        if (envVal != null)
            return envVal;

        return prop.getProperty(key, def);
    }
}
