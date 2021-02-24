package me.kavin.piped.ipfs;

import me.kavin.piped.utils.Multithreading;
import me.kavin.piped.utils.obj.Channel;

public class IPFS {

//    private static final io.ipfs.api.IPFS ipfs = new io.ipfs.api.IPFS(new MultiAddress("/ip4/127.0.0.1/tcp/5001"));

    public static void publishData(final Channel channel) {
        Multithreading.runAsync(() -> {
            try {
//                ipfs.pubsub.pub(URLUtils.silentEncode(channel.id),
//                        URLUtils.silentEncode(Constants.mapper.writeValueAsString(channel)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
