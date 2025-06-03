# How to Use

This is a temporary hack to fix new videos not appearing on the feed.

1. Follow self-hosting instructions [here](https://docs.piped.video/docs/self-hosting/) up to `cd Piped-Docker`.
2. Inside `Piped-Docker/template`, modify the relevant template with the following:
```dockerfile
    piped: # This is the backend service
        # image: 1337kavin/piped:latest # Removed this line
        build: # Build the image with the workaround
            context: https://github.com/firejoust/Piped-Backend.git # Tells docker-compose where to get the source
            dockerfile: Dockerfile # Specifies the Dockerfile within the context
```
3. Append the following to `Piped-Docker/config/config.properties`:
```sh
# Enable feed polling workaround
ENABLE_FEED_POLLING=true
POLLING_INTERVAL_MINUTES=15
POLLING_FETCH_LIMIT_PER_CHANNEL=10
FEED_RETENTION=30
```
4. After running `./configure-instance.sh` and selecting the relevant template, run the following to start the container:
```sh
sudo DOCKER_BUILDKIT=1 docker-compose up -d
```

### Workaround Configuration (`config.properties`)

*   **`ENABLE_FEED_POLLING`**:
    *   Set to `true` to enable the backend to periodically check subscribed channels for new videos, bypassing PubSub. Set to `false` (default) to disable this polling.

*   **`POLLING_INTERVAL_MINUTES`**:
    *   Specifies how often (in minutes) the backend should perform the polling cycle for all subscribed channels. Default: `15`.

*   **`POLLING_FETCH_LIMIT_PER_CHANNEL`**:
    *   Limits how many of the *most recent* videos are checked for each channel during a polling cycle. Helps improve performance and reduce load. Default: `10`.

*   **`FEED_RETENTION`**:
    *   Determines how many days old a video can be and still be added to the feed database (by polling or PubSub). Also affects how long videos are kept before cleanup. Default: `30`.

# README.md:

```markdown
# Piped-Backend

An advanced open-source privacy friendly alternative to YouTube, crafted with the help of [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor).

## Official Frontend

-   VueJS frontend - [Piped](https://github.com/TeamPiped/Piped)

## Community Projects

-   See https://github.com/TeamPiped/Piped#made-with-piped
```