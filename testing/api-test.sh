#!/bin/bash

CURLOPTS=(-i -s -S -o /dev/null -f -w "%{http_code}\tTime:\t%{time_starttransfer}\t%{url_effective}\n")
HOST=127.0.0.1:8080

# Healthcheck Test
curl ${CURLOPTS[@]} $HOST/healthcheck || exit 1

# Version Test
curl ${CURLOPTS[@]} $HOST/version || exit 1

# Trending Page
curl ${CURLOPTS[@]} $HOST/trending?region=US || exit 1

# Channel Pages
curl ${CURLOPTS[@]} $HOST/channel/UCsXVk37bltHxD1rDPwtNM8Q || exit 1
curl ${CURLOPTS[@]} $HOST/c/inanutshell || exit 1
curl ${CURLOPTS[@]} $HOST/user/Kurzgesagt || exit 1

# Channel Nextpage
CHANNEL_NEXTPAGE=$(curl -s -o - -f $HOST/channel/UCsXVk37bltHxD1rDPwtNM8Q | jq -r .nextpage)
curl ${CURLOPTS[@]} $HOST/nextpage/channel/UCsXVk37bltHxD1rDPwtNM8Q -G --data-urlencode "nextpage=$CHANNEL_NEXTPAGE" || exit 1

# Playlist
curl ${CURLOPTS[@]} $HOST/playlists/PLQSoWXSpjA3-egtFq45DcUydZ885W7MTT || exit 1

# Playlist Nextpage
PLAYLIST_NEXTPAGE=$(curl -s -o - -f $HOST/playlists/PLQSoWXSpjA3-egtFq45DcUydZ885W7MTT | jq -r .nextpage)
curl ${CURLOPTS[@]} $HOST/nextpage/playlists/UCsXVk37bltHxD1rDPwtNM8Q -G --data-urlencode "nextpage=$PLAYLIST_NEXTPAGE" || exit 1

# Clips
curl ${CURLOPTS[@]} $HOST/clips/Ugkx71jS31nwsms_Cc65oi7yXF1mILflhhrO || exit 1

# Streams
curl ${CURLOPTS[@]} $HOST/streams/BtN-goy9VOY || exit 1

# Comments
curl ${CURLOPTS[@]} $HOST/comments/BtN-goy9VOY || exit 1

# Comments Nextpage
COMMENTS_NEXTPAGE=$(curl -s -o - -f $HOST/comments/BtN-goy9VOY | jq -r .nextpage)
curl ${CURLOPTS[@]} $HOST/nextpage/comments/BtN-goy9VOY -G --data-urlencode "nextpage=$COMMENTS_NEXTPAGE" || exit 1

# Comments Repliespage
COMMENTS_REPLIESPAGE=$(curl -s -o - -f $HOST/comments/BtN-goy9VOY | jq -r .comments[0].repliesPage)
curl ${CURLOPTS[@]} $HOST/nextpage/comments/BtN-goy9VOY -G --data-urlencode "nextpage=$COMMENTS_REPLIESPAGE" || exit 1

# Comments Replies Nextpage
COMMENTS_REPLIES_NEXTPAGE=$(curl -s -o - -f $HOST/nextpage/comments/BtN-goy9VOY -G --data-urlencode "nextpage=$COMMENTS_REPLIESPAGE" | jq -r .nextpage)
curl ${CURLOPTS[@]} $HOST/nextpage/comments/BtN-goy9VOY -G --data-urlencode "nextpage=$COMMENTS_REPLIES_NEXTPAGE" || exit 1

USER=admin
PASS=$(openssl rand -base64 12)

AUTH_REQ=$(jq -n --compact-output --arg username "$USER" --arg password "$PASS" '{"username": $username, "password": $password}')

# Register Account
curl ${CURLOPTS[@]} $HOST/register -X POST -H "Content-Type: application/json" -d $AUTH_REQ || exit 1

# Login Account
curl ${CURLOPTS[@]} $HOST/login -X POST -H "Content-Type: application/json" -d $AUTH_REQ || exit 1

AUTH_TOKEN=$(curl -s -o - -f $HOST/login -X POST -H "Content-Type: application/json" -d $AUTH_REQ | jq -r .token)

if [[ -z "$AUTH_TOKEN" || $AUTH_TOKEN == "null" ]]; then
    echo "Failed to get auth token"
    exit 1
fi

# Check Subscription Status
curl ${CURLOPTS[@]} $HOST/subscribed -G --data-urlencode "channelId=UCsXVk37bltHxD1rDPwtNM8Q" -H "Authorization: $AUTH_TOKEN" || exit 1

# Subscribe to a Channel
curl ${CURLOPTS[@]} $HOST/subscribe -X POST -H "Content-Type: application/json" -H "Authorization: $AUTH_TOKEN" -d $(jq -n --compact-output --arg channelId "UCfdNM3NAhaBOXCafH7krzrA" '{"channelId": $channelId}') || exit 1

# Unsubscribe from the Channel
curl ${CURLOPTS[@]} $HOST/unsubscribe -X POST -H "Content-Type: application/json" -H "Authorization: $AUTH_TOKEN" -d $(jq -n --compact-output --arg channelId "UCfdNM3NAhaBOXCafH7krzrA" '{"channelId": $channelId}') || exit 1

# Resubscribe to the Channel
curl ${CURLOPTS[@]} $HOST/subscribe -X POST -H "Content-Type: application/json" -H "Authorization: $AUTH_TOKEN" -d $(jq -n --compact-output --arg channelId "UCfdNM3NAhaBOXCafH7krzrA" '{"channelId": $channelId}') || exit 1

# Wait 2s to allow the subscription request to be processed
sleep 2

# Check Feed
curl ${CURLOPTS[@]} $HOST/feed -G --data-urlencode "authToken=$AUTH_TOKEN" || exit 1
