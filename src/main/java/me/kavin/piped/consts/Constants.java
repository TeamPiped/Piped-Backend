package me.kavin.piped.consts;

import java.io.FileReader;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.util.Properties;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Constants {

    public static final boolean debug = false;

    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:78.0) Gecko/20100101 Firefox/78.0";

    public static final int PORT;

    public static final String PROXY_PART;

    public static final StreamingService YOUTUBE_SERVICE;

    public static final HttpClient h2client = HttpClient.newBuilder().followRedirects(Redirect.NORMAL)
	    .version(Version.HTTP_2).build();
//    public static final HttpClient h3client = Http3ClientBuilder.newBuilder().followRedirects(Redirect.NORMAL).build();

    public static final ObjectMapper mapper = new ObjectMapper();

    static {
	Properties prop = new Properties();
	try {
	    YOUTUBE_SERVICE = NewPipe.getService(0);
	    prop.load(new FileReader("config.properties"));

	    PORT = Integer.parseInt(prop.getProperty("PORT"));
	    PROXY_PART = prop.getProperty("PROXY_PART");
	} catch (Exception e) {
	    throw new RuntimeException(e);
	}
    }
}
