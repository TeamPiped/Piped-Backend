package me.kavin.piped.consts;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.kavin.piped.utils.PageMixin;
import org.apache.commons.lang3.StringUtils;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;

import java.io.FileReader;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.util.Properties;

public class Constants {

    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0";

    public static final int PORT;
    public static final String HTTP_WORKERS;

    public static final String PROXY_PART;

    public static final String CAPTCHA_BASE_URL, CAPTCHA_API_KEY;

    public static final StreamingService YOUTUBE_SERVICE;

    public static final String PUBLIC_URL;

    public static final String HTTP_PROXY;

    public static final String FRONTEND_URL;

    public static final HttpClient h2client;
    public static final HttpClient h2_no_redir_client;

    public static final boolean COMPROMISED_PASSWORD_CHECK;

    public static final boolean DISABLE_REGISTRATION;

    public static final int FEED_RETENTION;

    public static final ObjectMapper mapper = new ObjectMapper().addMixIn(Page.class, PageMixin.class);

    public static final Object2ObjectOpenHashMap<String, String> hibernateProperties = new Object2ObjectOpenHashMap<>();

    static {
        Properties prop = new Properties();
        try {
            YOUTUBE_SERVICE = NewPipe.getService(0);
            prop.load(new FileReader("config.properties"));

            PORT = Integer.parseInt(getProperty(prop, "PORT", "8080"));
            HTTP_WORKERS = getProperty(prop, "HTTP_WORKERS",
                    String.valueOf(Runtime.getRuntime().availableProcessors()));
            PROXY_PART = getProperty(prop, "PROXY_PART");
            CAPTCHA_BASE_URL = getProperty(prop, "CAPTCHA_BASE_URL");
            CAPTCHA_API_KEY = getProperty(prop, "CAPTCHA_API_KEY");
            PUBLIC_URL = getProperty(prop, "API_URL");
            HTTP_PROXY = getProperty(prop, "HTTP_PROXY");
            FRONTEND_URL = getProperty(prop, "FRONTEND_URL", "https://piped.kavin.rocks");
            COMPROMISED_PASSWORD_CHECK = Boolean.parseBoolean(getProperty(prop, "COMPROMISED_PASSWORD_CHECK", "true"));
            DISABLE_REGISTRATION = Boolean.parseBoolean(getProperty(prop, "DISABLE_REGISTRATION", "false"));
            FEED_RETENTION = Integer.parseInt(getProperty(prop, "FEED_RETENTION", "30"));
            System.getenv().forEach((key, value) -> {
                if (key.startsWith("hibernate"))
                    hibernateProperties.put(key, value);
            });
            prop.forEach((_key, _value) -> {
                String key = String.valueOf(_key), value = String.valueOf(_value);
                if (key.startsWith("hibernate"))
                    hibernateProperties.put(key, value);
            });
            Builder builder = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).version(Version.HTTP_2);
            Builder builder_noredir = HttpClient.newBuilder().followRedirects(Redirect.NEVER).version(Version.HTTP_2);
            if (HTTP_PROXY != null && HTTP_PROXY.contains(":")) {
                String host = StringUtils.substringBefore(HTTP_PROXY, ":");
                String port = StringUtils.substringAfter(HTTP_PROXY, ":");
                InetSocketAddress sa = new InetSocketAddress(host, Integer.parseInt(port));
                ProxySelector ps = ProxySelector.of(sa);
                ProxySelector.setDefault(ps);
            }
            h2client = builder.build();
            h2_no_redir_client = builder_noredir.build();
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
