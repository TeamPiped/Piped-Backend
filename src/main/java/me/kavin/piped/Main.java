package me.kavin.piped;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.Localization;

import io.activej.inject.Injector;
import me.kavin.piped.utils.DatabaseSessionFactory;
import me.kavin.piped.utils.DownloaderImpl;

public class Main {

    public static void main(String[] args) throws Exception {

        NewPipe.init(new DownloaderImpl(), new Localization("en", "US"));

        Injector.useSpecializer();

        new Thread(() -> {
            DatabaseSessionFactory.createSession().close();
        }).start();

        new ServerLauncher().launch(args);

    }
}
