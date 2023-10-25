package me.kavin.piped.server.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;
import io.sentry.ITransaction;
import io.sentry.Sentry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.kavin.piped.consts.Constants;
import me.kavin.piped.utils.*;
import me.kavin.piped.utils.obj.*;
import me.kavin.piped.utils.obj.federation.FederatedGeoBypassRequest;
import me.kavin.piped.utils.obj.federation.FederatedGeoBypassResponse;
import me.kavin.piped.utils.obj.federation.FederatedVideoInfo;
import me.kavin.piped.utils.resp.InvalidRequestResponse;
import me.kavin.piped.utils.resp.VideoResolvedResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.utils.JsonUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static me.kavin.piped.consts.Constants.YOUTUBE_SERVICE;
import static me.kavin.piped.consts.Constants.mapper;
import static me.kavin.piped.utils.URLUtils.*;
import static org.schabi.newpipe.extractor.NewPipe.getPreferredContentCountry;
import static org.schabi.newpipe.extractor.NewPipe.getPreferredLocalization;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getJsonPostResponse;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.prepareDesktopJsonBuilder;

public class StreamHandlers {
    public static byte[] streamsResponse(String videoId) throws Exception {

        Sentry.setExtra("videoId", videoId);

        final var futureStream = Multithreading.supplyAsync(() -> {
            Sentry.setExtra("videoId", videoId);
            ITransaction transaction = Sentry.startTransaction("StreamInfo fetch", "fetch");
            try {
                return StreamInfo.getInfo("https://www.youtube.com/watch?v=" + videoId);
            } catch (Exception e) {
                if (e instanceof GeographicRestrictionException) {
                    return null;
                }
                transaction.setThrowable(e);
                ExceptionUtils.rethrow(e);
            } finally {
                transaction.finish();
            }
            return null;
        });

        final var futureLbryId = Multithreading.supplyAsync(() -> {
            Sentry.setExtra("videoId", videoId);
            try {
                return LbryHelper.getLBRYId(videoId);
            } catch (Exception e) {
                ExceptionHandler.handle(e);
            }
            return null;
        });

        final var futureLBRY = Multithreading.supplyAsync(() -> {
            Sentry.setExtra("videoId", videoId);
            ITransaction transaction = Sentry.startTransaction("LBRY Stream fetch", "fetch");
            try {
                var childTask = transaction.startChild("fetch", "LBRY ID fetch");
                String lbryId = futureLbryId.get(2, TimeUnit.SECONDS);
                Sentry.setExtra("lbryId", lbryId);
                childTask.finish();

                return LbryHelper.getLBRYStreamURL(lbryId);
            } catch (TimeoutException ignored) {
            } catch (Exception e) {
                ExceptionHandler.handle(e);
            } finally {
                transaction.finish();
            }
            return null;
        });

        final var futureLBRYHls = Multithreading.supplyAsync(() -> {
            Sentry.setExtra("videoId", videoId);
            ITransaction transaction = Sentry.startTransaction("LBRY HLS fetch", "fetch");
            try {
                var childTask = transaction.startChild("fetch", "LBRY Stream URL fetch");
                String lbryUrl = futureLBRY.get(2, TimeUnit.SECONDS);
                Sentry.setExtra("lbryUrl", lbryUrl);
                childTask.finish();

                return LbryHelper.getLBRYHlsUrl(lbryUrl);
            } catch (TimeoutException ignored) {
            } catch (Exception e) {
                ExceptionHandler.handle(e);
            } finally {
                transaction.finish();
            }
            return null;
        });

        final var futureDislikeRating = Multithreading.supplyAsync(() -> {
            Sentry.setExtra("videoId", videoId);
            ITransaction transaction = Sentry.startTransaction("Dislike Rating", "fetch");
            try {
                return RydHelper.getDislikeRating(videoId);
            } catch (Exception e) {
                ExceptionHandler.handle(e);
            } finally {
                transaction.finish();
            }
            return null;
        });

        StreamInfo info = null;
        Throwable exception = null;

        try {
            info = futureStream.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            exception = e.getCause();
            if (
                // Some videos, like topic channel videos are not available everywhere
                    !(exception instanceof ContentNotAvailableException contentNotAvailableException && (contentNotAvailableException.getMessage().equals("This video is not available") || contentNotAvailableException.getMessage().equals("Got error: \"Video unavailable\""))) &&
                            !(e.getCause() instanceof GeographicRestrictionException)
            ) {
                ExceptionUtils.rethrow(e);
            }
        }

        if (info == null) {
            // We might be geo restricted

            if (Constants.MATRIX_TOKEN != null && Constants.GEO_RESTRICTION_CHECKER_URL != null) {

                List<String> allowedCountries = new ObjectArrayList<>();

                {
                    var restrictedTree = RequestUtils.sendGetJson(Constants.GEO_RESTRICTION_CHECKER_URL + "/api/region/check?video_id=" + videoId).get();
                    if (!restrictedTree.get("restricted").asBoolean()) {
                        assert exception != null;
                        throw (Exception) exception;
                    }
                    var it = restrictedTree.get("regions").elements();
                    while (it.hasNext()) {
                        var region = it.next();
                        allowedCountries.add(region.textValue());
                    }
                }

                if (allowedCountries.isEmpty())
                    throw new GeographicRestrictionException("Federated bypass failed, video not available in any region");

                MatrixHelper.sendEvent("video.piped.stream.bypass.request", new FederatedGeoBypassRequest(videoId, allowedCountries));

                var listener = new WaitingListener(10_000);
                GeoRestrictionBypassHelper.makeRequest(videoId, listener);
                listener.waitFor();
                FederatedGeoBypassResponse federatedGeoBypassResponse = GeoRestrictionBypassHelper.getResponse(videoId);

                if (federatedGeoBypassResponse == null)
                    throw new GeographicRestrictionException("Federated bypass failed, likely not authorized or no suitable instances found for country");

                Streams streams = federatedGeoBypassResponse.getData();

                // re-rewrite image URLs
                streams.chapters.forEach(chapter -> chapter.image = rewriteURL(chapter.image));
                streams.relatedStreams.forEach(contentItem -> {
                    if (contentItem instanceof StreamItem streamItem) {
                        streamItem.thumbnail = rewriteURL(streamItem.thumbnail);
                        streamItem.uploaderAvatar = rewriteURL(streamItem.uploaderAvatar);
                    } else if (contentItem instanceof ChannelItem channelItem) {
                        channelItem.thumbnail = rewriteURL(channelItem.thumbnail);
                    } else if (contentItem instanceof PlaylistItem playlistItem) {
                        playlistItem.thumbnail = rewriteURL(playlistItem.thumbnail);
                    }
                });
                streams.subtitles.forEach(subtitle -> subtitle.url = rewriteURL(subtitle.url));
                streams.thumbnailUrl = rewriteURL(streams.thumbnailUrl);
                streams.uploaderAvatar = rewriteURL(streams.uploaderAvatar);

                String lbryId;

                try {
                    lbryId = futureLbryId.get(2, TimeUnit.SECONDS);
                } catch (Exception e) {
                    lbryId = null;
                }

                if (lbryId != null) {
                    streams.lbryId = lbryId;
                }

                String lbryURL;

                try {
                    lbryURL = futureLBRY.get(3, TimeUnit.SECONDS);
                } catch (Exception e) {
                    lbryURL = null;
                }

                if (lbryURL != null)
                    streams.videoStreams.add(0, new PipedStream(-1, lbryURL, "MP4", "LBRY", "video/mp4", false, -1));

                // Attempt to get dislikes calculating with the RYD API rating
                if (streams.dislikes < 0 && streams.likes >= 0) {
                    double rating;
                    try {
                        rating = futureDislikeRating.get(3, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        rating = -1;
                    }

                    if (rating > 1 && rating <= 5) {
                        streams.dislikes = Math.round(streams.likes * ((5 - rating) / (rating - 1)));
                    }
                }

                return mapper.writeValueAsBytes(streams);
            } else if (Constants.GEO_RESTRICTION_CHECKER_URL == null) {
                throw new GeographicRestrictionException("This instance does not have a geo restriction checker set in its configuration");
            }

            if (exception == null)
                throw new GeographicRestrictionException("Geo restricted content, this instance is not part of the Matrix Federation protocol");
            else
                throw (Exception) exception;
        }

        Streams streams = CollectionUtils.collectStreamInfo(info);

        String lbryURL = null;

        try {
            lbryURL = futureLBRY.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            // ignored
        }

        String lbryHlsURL = null;

        try {
            lbryHlsURL = futureLBRYHls.get(4, TimeUnit.SECONDS);
        } catch (Exception e) {
            // ignored
        }

        if (lbryHlsURL != null)
            streams.videoStreams.add(0, new PipedStream(-1, lbryHlsURL, "HLS", "LBRY HLS", "application/x-mpegurl", false, -1));

        if (lbryURL != null)
            streams.videoStreams.add(0, new PipedStream(-1, lbryURL, "MP4", "LBRY", "video/mp4", false, -1));

        long time = info.getUploadDate() != null ? info.getUploadDate().offsetDateTime().toInstant().toEpochMilli()
                : System.currentTimeMillis();

        if (info.getUploadDate() != null && System.currentTimeMillis() - time < TimeUnit.DAYS.toMillis(Constants.FEED_RETENTION)) {
            VideoHelpers.updateVideo(info.getId(), info, time);
            StreamInfo finalInfo = info;
            Multithreading.runAsync(() -> {
                try {
                    MatrixHelper.sendEvent("video.piped.stream.info", new FederatedVideoInfo(
                            finalInfo.getId(), StringUtils.substring(finalInfo.getUploaderUrl(), -24),
                            finalInfo.getName(),
                            finalInfo.getDuration(), finalInfo.getViewCount())
                    );
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        String lbryId;

        try {
            lbryId = futureLbryId.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            lbryId = null;
        }

        streams.lbryId = lbryId;

        // Attempt to get dislikes calculating with the RYD API rating
        if (streams.dislikes < 0 && streams.likes >= 0) {
            double rating;
            try {
                rating = futureDislikeRating.get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                rating = -1;
            }

            if (rating > 1 && rating <= 5) {
                streams.dislikes = Math.round(streams.likes * ((5 - rating) / (rating - 1)));
            }
        }

        return mapper.writeValueAsBytes(streams);

    }

    public static byte[] resolveClipId(String clipId) throws Exception {

        final byte[] body = JsonWriter.string(prepareDesktopJsonBuilder(
                        getPreferredLocalization(), getPreferredContentCountry())
                        .value("url", "https://www.youtube.com/clip/" + clipId)
                        .done())
                .getBytes(UTF_8);

        final JsonObject jsonResponse = getJsonPostResponse("navigation/resolve_url",
                body, getPreferredLocalization());

        final String videoId = JsonUtils.getString(jsonResponse, "endpoint.watchEndpoint.videoId");

        return mapper.writeValueAsBytes(new VideoResolvedResponse(videoId));
    }

    public static byte[] commentsResponse(String videoId) throws Exception {

        Sentry.setExtra("videoId", videoId);

        CommentsInfo info = CommentsInfo.getInfo("https://www.youtube.com/watch?v=" + videoId);

        List<Comment> comments = new ObjectArrayList<>();

        info.getRelatedItems().forEach(comment -> {
            try {
                String repliespage = null;
                if (comment.getReplies() != null)
                    repliespage = mapper.writeValueAsString(comment.getReplies());

                comments.add(new Comment(comment.getUploaderName(), getLastThumbnail(comment.getUploaderAvatars()),
                        comment.getCommentId(), Optional.ofNullable(comment.getCommentText()).map(Description::getContent).orElse(null), comment.getTextualUploadDate(),
                        substringYouTube(comment.getUploaderUrl()), repliespage, comment.getLikeCount(), comment.getReplyCount(),
                        comment.isHeartedByUploader(), comment.isPinned(), comment.isUploaderVerified(), comment.hasCreatorReply(), comment.isChannelOwner()));
            } catch (JsonProcessingException e) {
                ExceptionHandler.handle(e);
            }
        });

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = mapper.writeValueAsString(page);
        }

        CommentsPage commentsItem = new CommentsPage(comments, nextpage, info.isCommentsDisabled(), info.getCommentsCount());

        return mapper.writeValueAsBytes(commentsItem);

    }

    public static byte[] commentsPageResponse(String videoId, String prevpageStr) throws Exception {

        if (StringUtils.isEmpty(prevpageStr))
            ExceptionHandler.throwErrorResponse(new InvalidRequestResponse("nextpage is a required parameter"));

        Page prevpage = mapper.readValue(prevpageStr, Page.class);

        ListExtractor.InfoItemsPage<CommentsInfoItem> info = CommentsInfo.getMoreItems(YOUTUBE_SERVICE, "https://www.youtube.com/watch?v=" + videoId, prevpage);

        List<Comment> comments = new ObjectArrayList<>();

        info.getItems().forEach(comment -> {
            try {
                String repliespage = null;
                if (comment.getReplies() != null)
                    repliespage = mapper.writeValueAsString(comment.getReplies());

                comments.add(new Comment(comment.getUploaderName(), getLastThumbnail(comment.getUploaderAvatars()),
                        comment.getCommentId(), Optional.ofNullable(comment.getCommentText()).map(Description::getContent).orElse(null), comment.getTextualUploadDate(),
                        substringYouTube(comment.getUploaderUrl()), repliespage, comment.getLikeCount(), comment.getReplyCount(),
                        comment.isHeartedByUploader(), comment.isPinned(), comment.isUploaderVerified(), comment.hasCreatorReply(), comment.isChannelOwner()));
            } catch (JsonProcessingException e) {
                ExceptionHandler.handle(e);
            }
        });

        String nextpage = null;
        if (info.hasNextPage()) {
            Page page = info.getNextPage();
            nextpage = mapper.writeValueAsString(page);
        }

        CommentsPage commentsItem = new CommentsPage(comments, nextpage, false, -1);

        return mapper.writeValueAsBytes(commentsItem);

    }
}
