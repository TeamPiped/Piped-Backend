package me.kavin.piped.server.handlers;

import io.sentry.Sentry;
import me.kavin.piped.utils.ExceptionHandler;
import me.kavin.piped.utils.obj.ContentItem;
import me.kavin.piped.utils.obj.SearchResults;
import me.kavin.piped.utils.resp.InvalidRequestResponse;
import org.apache.commons.lang3.StringUtils;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.search.SearchInfo;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static me.kavin.piped.consts.Constants.YOUTUBE_SERVICE;
import static me.kavin.piped.consts.Constants.mapper;
import static me.kavin.piped.utils.CollectionUtils.collectRelatedItems;

public class SearchHandlers {
    public static byte[] suggestionsResponse(String query)
            throws IOException, ExtractionException {

        if (StringUtils.isEmpty(query))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("query is a required parameter"));

        return mapper.writeValueAsBytes(YOUTUBE_SERVICE.getSuggestionExtractor().suggestionList(query));

    }

    public static byte[] opensearchSuggestionsResponse(String query)
            throws IOException, ExtractionException {

        if (StringUtils.isEmpty(query))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("query is a required parameter"));

        return mapper.writeValueAsBytes(Arrays.asList(
                query,
                YOUTUBE_SERVICE.getSuggestionExtractor().suggestionList(query)
        ));

    }

    public static byte[] searchResponse(String q, String filter)
            throws IOException, ExtractionException {

        if (StringUtils.isEmpty(q) || StringUtils.isEmpty(filter))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("query and filter are required parameters"));

        Sentry.setExtra("query", q);

        final SearchInfo info = SearchInfo.getInfo(YOUTUBE_SERVICE,
                YOUTUBE_SERVICE.getSearchQHFactory().fromQuery(q, Collections.singletonList(filter), null));

        List<ContentItem> items = collectRelatedItems(info.getRelatedItems());

        Page nextpage = info.getNextPage();

        return mapper.writeValueAsBytes(new SearchResults(items,
                mapper.writeValueAsString(nextpage), info.getSearchSuggestion(), info.isCorrectedSearch()));

    }

    public static byte[] searchPageResponse(String q, String filter, String prevpageStr)
            throws IOException, ExtractionException {

        if (StringUtils.isEmpty(q) || StringUtils.isEmpty(prevpageStr))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("query and nextpage are required parameter"));

        Page prevpage = mapper.readValue(prevpageStr, Page.class);

        if (prevpage == null)
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("nextpage is a required parameter"));

        ListExtractor.InfoItemsPage<InfoItem> pages = SearchInfo.getMoreItems(YOUTUBE_SERVICE,
                YOUTUBE_SERVICE.getSearchQHFactory().fromQuery(q, Collections.singletonList(filter), null), prevpage);

        List<ContentItem> items = collectRelatedItems(pages.getItems());

        Page nextpage = pages.getNextPage();

        return mapper
                .writeValueAsBytes(new SearchResults(items, mapper.writeValueAsString(nextpage)));

    }
}
