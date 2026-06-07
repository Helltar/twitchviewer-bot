<p align="center">
  <a href="https://t.me/twitchviewer_bot">
    <img src="https://helltar.com/projects/twitchviewer-bot/img/t-me-qr-code-1.png" alt="Telegram bot QR code" width="35%"/>
  </a>
</p>

## Installation

### Docker Compose

Download the configuration files:

```bash
mkdir twitchbot && cd twitchbot && \
wget https://raw.githubusercontent.com/Helltar/twitchviewer-bot/master/{.env.example,compose.yaml} && \
mv .env.example .env
```

Edit `.env` and fill in your values:

- `CREATOR_ID`: your Telegram user ID
- `BOT_TOKEN`: Telegram bot token ([BotFather](https://t.me/BotFather))
- `BOT_USERNAME`: Telegram bot username ([BotFather](https://t.me/BotFather))
- `TWITCH_CLIENT_ID`: Twitch app client ID ([Twitch Developer Console](https://dev.twitch.tv/console/apps))
- `TWITCH_CLIENT_SECRET`: Twitch app client secret ([Twitch Developer Console](https://dev.twitch.tv/console/apps))
- `POSTGRESQL_*` + `DATABASE_*`: PostgreSQL connection settings

Start the bot:

```bash
docker compose up -d
```

> **Note:**
> `compose.yaml` includes a PostgreSQL container, so no external database is required.
> To use your own PostgreSQL instance instead, remove the `postgres` service from
> `compose.yaml` and point the `POSTGRESQL_*` / `DATABASE_*` values in `.env` to it.

## Commands

- `/clip` - Record clips from all tracked channels or a specific channel
- `/screenshot` - Capture screenshots from all tracked channels or a specific channel
- `/add` - Add channel to favorites
- `/list` - Show your favorite channels
- `/cancel` - Cancel your active background tasks

## Notes

- The bot requires `ffmpeg` and `streamlink` (already included in the provided Docker image).
