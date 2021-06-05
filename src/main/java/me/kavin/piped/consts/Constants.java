package me.kavin.piped.consts;

import java.io.FileReader;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.util.Properties;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;

import me.kavin.piped.utils.PageMixin;

public class Constants {

    public static final boolean debug = false;

    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:78.0) Gecko/20100101 Firefox/78.0";

    public static final int PORT;
    public static final String HTTP_WORKERS;

    public static final String PROXY_PART;

    public static final String CAPTCHA_BASE_URL, CAPTCHA_API_KEY;

    public static final StreamingService YOUTUBE_SERVICE;

    public static final HttpClient h2client = HttpClient.newBuilder().followRedirects(Redirect.NORMAL)
            .version(Version.HTTP_2).build();
    public static final HttpClient h2_no_redir_client = HttpClient.newBuilder().followRedirects(Redirect.NEVER)
            .version(Version.HTTP_2).build();
//    public static final HttpClient h3client = Http3ClientBuilder.newBuilder().followRedirects(Redirect.NORMAL).build();

    public static final MongoClient mongoClient;

    public static final ObjectMapper mapper = new ObjectMapper().addMixIn(Page.class, PageMixin.class);

    static {
        Properties prop = new Properties();
        try {
            YOUTUBE_SERVICE = NewPipe.getService(0);
            prop.load(new FileReader("config.properties"));

            PORT = Integer.parseInt(prop.getProperty("PORT", "8080"));
            HTTP_WORKERS = prop.getProperty("HTTP_WORKERS", String.valueOf(Runtime.getRuntime().availableProcessors()));
            PROXY_PART = prop.getProperty("PROXY_PART");
            CAPTCHA_BASE_URL = prop.getProperty("CAPTCHA_BASE_URL");
            CAPTCHA_API_KEY = prop.getProperty("CAPTCHA_API_KEY");
            mongoClient = null/* MongoClients.create(prop.getProperty("MONGO_URI")) */;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
