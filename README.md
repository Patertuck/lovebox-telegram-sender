# Lovebox Telegram Sender

This application sends a Lovebox message scheduled for the current date at 21:30 in the `Europe/Zurich` time zone. It also accepts manual text and photo messages from one authorized Telegram chat.

It does not poll Lovebox delivery states, receive hearts, or track whether a message arrived.

## Scheduled-message database

The SQLite database has one table with exactly two columns:

```sql
CREATE TABLE messages (
  send_date TEXT PRIMARY KEY,
  message TEXT NOT NULL
);
```

`send_date` is an exact ISO date (`YYYY-MM-DD`). Only one message can be scheduled per date.

## Create the database from Word

Use a `.docx` document with sections in this form:

```text
24.12.26
My message for this date.

Another paragraph is preserved.

25.12.26
Another message.
```

Each `DD.MM.YY` heading schedules the following paragraphs until the next heading, one calendar year after the date in the document. The original date heading is included as the first line of the sent message. The importer rejects duplicate scheduled dates or empty sections and only replaces the old database after the entire document has validated.

Set the document and database paths in `.env`:

```properties
MESSAGES_DOCUMENT_PATH=message.docx
MESSAGES_DATABASE_PATH=data/messages.db
```

From the project directory, run:

```powershell
.\mvnw.cmd --% -Dmaven.test.skip=true spring-boot:run -Dspring-boot.run.profiles=import
```

The command exits after creating `data/messages.db`. Copy that file to the server's mounted `data/` directory if needed.

## Configuration

Use `.env.example` as the template for `.env`. The relevant database setting is:

```properties
MESSAGES_DATABASE_PATH=/app/data/messages.db
```

The normal schedule is 21:30 Europe/Zurich.

The regular container mounts `./data` at `/app/data`, so the schedule persists across image updates.

## Run and test

```powershell
docker compose up -d
.\mvnw.cmd test
```
