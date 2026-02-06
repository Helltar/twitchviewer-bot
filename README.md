<p align="center">
    <a href="https://t.me/twitchviewer_bot">
        <img src="https://helltar.com/projects/twitchviewer-bot/img/t-me-qr-code.png" alt="qr_code" width="35%"/>
    </a>
</p>

## Installation

### Docker Compose

```bash
mkdir twitchbot && cd twitchbot && \
wget https://raw.githubusercontent.com/Helltar/twitchviewer_bot/master/{.env,compose.yaml,compose.with-postgres.yaml}
```

Edit the **.env** file and specify the required tokens, database address, and credentials for **PostgreSQL**:

- `CREATOR_ID`: your Telegram user-ID
- `BOT_TOKEN` & `BOT_USERNAME`: [BotFather](https://t.me/BotFather)
- `TWITCH_CLIENT_ID` & `TWITCH_CLIENT_SECRET`: [Twitch Developer Console](https://dev.twitch.tv/console/apps/create)

If you already have an external PostgreSQL database, use the `compose.yaml` file.
In this configuration, PostgreSQL is **not** included as a container, and the bot will connect to your external database using the details provided in the **.env** file:

```bash
docker compose up -d
```

If you do not have an external PostgreSQL instance, use the `compose.with-postgres.yaml` file.
This configuration will set up a separate PostgreSQL container, running alongside the bot:

```bash
docker compose -f compose.with-postgres.yaml up -d
```

## Commands

- `/clip` - Start recording clips from all channels or a specific channel
- `/screenshot` - Take a screenshot from all channels or a specific channel
- `/list` - View your favorite channels
- `/add` - Add a new channel to your favorites
- `/cancel` - End the recording process started using the `/clip` command
