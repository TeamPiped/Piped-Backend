package me.kavin.piped.utils;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.kavin.piped.utils.obj.federation.FederatedGeoBypassResponse;

import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class GeoRestrictionBypassHelper {

    private static final Map<String, Long> requestsMap = new Object2LongOpenHashMap<>();
    private static final Map<String, Response> responsesMap = new Object2ObjectOpenHashMap<>();
    private static final List<ListenerRequest> waitingListeners = new ObjectArrayList<>();

    public static void makeRequest(String id, WaitingListener listener) {
        synchronized (requestsMap) {
            if (!requestsMap.containsKey(id))
                requestsMap.put(id, System.currentTimeMillis());
            else {
                synchronized (responsesMap) {
                    if (responsesMap.containsKey(id)) {
                        listener.done();
                        return;
                    }
                }
            }
        }
        synchronized (waitingListeners) {
            waitingListeners.add(new ListenerRequest(id, listener));
        }
    }

    public static void addResponse(FederatedGeoBypassResponse response) {
        String id = response.getVideoId();
        synchronized (requestsMap) {
            if (requestsMap.containsKey(id)) {
                synchronized (responsesMap) {
                    responsesMap.put(id, new Response(response));
                }
                synchronized (waitingListeners) {
                    for (ListenerRequest waitingListener : waitingListeners) {
                        if (waitingListener.id.equals(id)) {
                            waitingListener.listener.done();
                        }
                    }
                }
            }
        }
    }

    public static FederatedGeoBypassResponse getResponse(String id) {
        synchronized (responsesMap) {
            if (responsesMap.containsKey(id)) {
                return responsesMap.get(id).response;
            }
        }
        return null;
    }

    private static final class ListenerRequest {
        private final String id;
        private final long creationTime = System.currentTimeMillis();
        private final WaitingListener listener;

        public ListenerRequest(String id, WaitingListener listener) {
            this.id = id;
            this.listener = listener;
        }
    }

    private static final class Response {
        private final FederatedGeoBypassResponse response;
        private final long time = System.currentTimeMillis();

        public Response(FederatedGeoBypassResponse response) {
            this.response = response;
        }
    }

    static {
        long time = TimeUnit.SECONDS.toMillis(60);

        // Start evictor timer to remove old requests, responses and listeners
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                synchronized (requestsMap) {
                    requestsMap.entrySet().removeIf(e -> e.getValue() < System.currentTimeMillis() - time);
                }
                synchronized (responsesMap) {
                    responsesMap.entrySet().removeIf(e -> e.getValue().time < System.currentTimeMillis() - time);
                }
                synchronized (waitingListeners) {
                    waitingListeners.removeIf(e -> e.creationTime < System.currentTimeMillis() - time);
                }
            }
        }, time, time);
    }
}
