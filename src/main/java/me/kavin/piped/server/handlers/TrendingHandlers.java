package me.kavin.piped.server.handlers;

import me.kavin.piped.utils.ExceptionHandler;
import me.kavin.piped.utils.obj.ContentItem;
import me.kavin.piped.utils.resp.InvalidRequestResponse;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.kiosk.KioskList;
import org.schabi.newpipe.extractor.localization.ContentCountry;

import java.io.IOException;
import java.util.List;

import static me.kavin.piped.consts.Constants.YOUTUBE_SERVICE;
import static me.kavin.piped.consts.Constants.mapper;
import static me.kavin.piped.utils.CollectionUtils.collectRelatedItems;

public class TrendingHandlers {
    public static byte[] trendingResponse(String region)
            throws ExtractionException, IOException {

        if (region == null)
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("region is a required parameter"));

        KioskList kioskList = YOUTUBE_SERVICE.getKioskList();
        kioskList.forceContentCountry(new ContentCountry(region));
        KioskExtractor<?> extractor = kioskList.getDefaultKioskExtractor();
        extractor.fetchPage();
        KioskInfo info = KioskInfo.getInfo(extractor);

        final List<ContentItem> relatedStreams = collectRelatedItems(info.getRelatedItems());

        return mapper.writeValueAsBytes(relatedStreams);
    }
}
