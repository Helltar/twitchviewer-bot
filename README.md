<p align="center">
  <a href="https://t.me/twitchviewer_bot">
    <img src="https://helltar.com/projects/twitchviewer-bot/img/t-me-qr-code-1.png" alt="Telegram bot QR code" width="35%"/>
  </a>
</p>

## Installation

### Docker Compose

Download the configuration files:

```bash
mkdir twitchbot && cd twitchbot && curl -fsSLO \
  "https://github.com/Helltar/twitchviewer-bot/raw/master/{compose.yaml,.env.example}" && \
  mv .env.example .env
```

Edit `.env` and fill in your values:

- `CREATOR_ID`: your Telegram user ID
- `BOT_TOKEN` and `BOT_USERNAME`: Telegram bot token and username
- `TWITCH_CLIENT_ID` and `TWITCH_CLIENT_SECRET`: Twitch app credentials ([Twitch Developer Console](https://dev.twitch.tv/console/apps))
- `POSTGRESQL_*` + `DATABASE_*`: PostgreSQL connection settings

Start the bot:

```bash
docker compose up -d
```

> **Note:**
> `compose.yaml` includes a PostgreSQL container, so no external database is required.
> To use your own PostgreSQL instance instead, remove the `postgres` service from
> `compose.yaml` and point the `POSTGRESQL_*` / `DATABASE_*` values in `.env` to it.

### Build from source

To run your own build instead of the published image, clone the repository, create `.env` the
same way, and add `compose.local.yaml` on top — it builds the image from the checkout on every
start:

```bash
git clone https://github.com/Helltar/twitchviewer-bot.git && cd twitchviewer-bot
cp .env.example .env
docker compose -f compose.yaml -f compose.local.yaml up -d
```

## Commands

**`/clip`** - record clips

- `/clip` - from all tracked channels
- `/clip <channel>` - from a specific channel (even if it isn't in your list)
- `/clip <prefix>.` - only from tracked channels whose name starts with `<prefix>`, e.g. `/clip em.`
- `/clip !<prefix>.` - from all tracked channels **except** those starting with `<prefix>`, e.g. `/clip !em.`

**`/screenshot`** - capture screenshots

- `/screenshot` - from all tracked channels
- `/screenshot <channel>` - from a specific channel

**Other**

- `/add` - Add channel to favorites
- `/list` - Show your favorite channels
- `/cancel` - Cancel your active background tasks

## Notes

- The bot requires `ffmpeg` and `streamlink` (already included in the provided Docker image).
