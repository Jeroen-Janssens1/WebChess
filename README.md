# WebChess

WebChess is a browser-based chess application. Players can create accounts, play a local hot-seat game, or join rooms of up to 20 members to request games, play, or spectate other room games. The frontend communicates with a Spring Boot API; MySQL stores user accounts, while active rooms and games are held in server memory.

## Contents

- [Architecture](#architecture)
- [Run with Docker Compose](#run-with-docker-compose)
- [Run for development](#run-for-development)
- [Backend](#backend)
- [Frontend](#frontend)
- [Project layout](#project-layout)
- [Planned features](#planned-features)

## Architecture

| Component | Technology | Responsibility |
| --- | --- | --- |
| Frontend | React 19, Vite 6 | Application screens, user interactions, and API requests |
| Web/API server | Java 17, Spring Boot 4.1 | Authentication, rooms, chess games, and server-sent events |
| Database | MySQL 8 | User account records |
| Production web server | Nginx | Serves the built frontend and proxies `/api/` requests to the backend |

Account data is persisted in MySQL. **Room membership, game state, and event subscriptions are in-memory only**: they are lost when the backend process restarts, and are not shared across multiple backend instances.

## Run with Docker Compose

Docker Compose runs the frontend, backend, and MySQL database. On the first initialization of the MySQL data volume, the official MySQL image automatically runs the root-level `database.sql` mounted into its initialization directory. This creates the `users` table before the backend starts; Hibernate validates the schema at startup.

From the repository root:

1. Build and start the stack:

   ```sh
   docker compose up --build -d
   ```

2. Open [http://localhost:8080](http://localhost:8080). To check the API container, run:

   ```sh
   docker compose exec backend wget -qO- http://localhost:8080/api/health
   ```

The Compose configuration maps the frontend to host port `8080` and MySQL to host port `3307`. The backend listens on port `8080` inside the Compose network and is reached by Nginx at `http://backend:8080`; it is not published directly to the host. The database volume `dbdata` preserves MySQL data when containers are recreated.

MySQL initialization scripts run only when the data directory is empty. Restarting or rebuilding containers with the existing `dbdata` volume does not rerun `database.sql`. The script drops and recreates the `users` table, so it is intended for initial setup only. To initialize a clean database, remove the old data volume before starting Compose again; this deletes all persisted MySQL data:

```sh
docker compose down -v
docker compose up --build -d
```

The Compose file currently contains example database credentials (`dbname`, `user`, `pass`, and `rootpass`). Change them consistently in `docker-compose.yml` before using the setup beyond local development.

Useful Compose commands:

```sh
docker compose logs -f backend
docker compose down
```

`docker compose down` keeps the named database volume. Removing the volume deletes persisted database data; do that only when you intend to reset the database.

## Run for development

### Backend

Requirements: Java 17 and Maven (or use the Maven wrapper in `backendJava`).

Configure a reachable MySQL database using `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, and `DB_PASSWORD`. The backend expects the `users` table to exist; `spring.jpa.hibernate.ddl-auto=validate` checks the schema but does not create it. Then start the application:

```sh
cd backendJava
./mvnw spring-boot:run
```

On Windows, run `.\mvnw.cmd spring-boot:run` instead. The API listens on port `8080` by default. Run backend tests with `./mvnw test` (Windows: `.\mvnw.cmd test`).

### Frontend

Requirements: Node.js 22 or a compatible current Node.js release, and npm.

```sh
cd frontend
npm ci
npm run dev
```

Vite serves the development frontend on port `5173` and proxies `/api` requests to `http://localhost:8080`. Override the proxy destination with `WEBCHESS_API_TARGET` if the backend is hosted elsewhere. Create a production bundle with `npm run build`; the output is written to `frontend/dist`.

## Backend

The API is rooted at `/api`. Unless stated otherwise, endpoints require an authenticated session. Login establishes a server-side HTTP session; the browser uses the session cookie for subsequent requests. Spring Security's CSRF protection applies to state-changing requests, so clients should first obtain the CSRF cookie/token from `GET /api/csrf` and send the token in `X-XSRF-TOKEN`.

JSON request bodies use `Content-Type: application/json`. Error responses generally have the form `{"error":"..."}`. Endpoints return `401` when authentication is missing, and resource-specific `403`, `404`, or `409` errors where noted below.

### Authentication and account endpoints

| Method | Endpoint | Authentication | Description |
| --- | --- | --- | --- |
| `GET` | `/api/health` | No | Health check; returns `{"status":"ok"}`. |
| `GET` | `/api/csrf` | No | Initializes/returns the CSRF token cookie; responds `204 No Content`. |
| `POST` | `/api/register` | No | Create an account. Body: `{"username":"...","password":"..."}`. Returns `201`; duplicate usernames return `409`. Registration does not log the user in. |
| `POST` | `/api/login` | No | Start an authenticated session. Body: `{"username":"...","password":"..."}`. Returns the authenticated username; invalid credentials return `401`. |
| `POST` | `/api/logout` | Yes | Invalidate the current session. |
| `GET` | `/api/user` | Yes | Return the current username as `{"user":{"username":"..."}}`. |
| `DELETE` | `/api/user` | Yes | Delete the authenticated account. |

Passwords are stored using BCrypt hashing.

### Rooms

Rooms have a unique name, a host, and a maximum of 20 members. A room can optionally be password-protected; room passwords are stored as BCrypt hashes. The public room listing indicates whether a room is password-protected but does not expose its password. A member can join while room games are in progress. The current host can transfer host privileges to another member; if the host leaves without transferring them, the room closes.

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/rooms` | List active rooms. Returns public room summaries, without games or game requests. |
| `POST` | `/api/rooms` | Create a room. Body: `{"name":"...","password":"..."}`; password may be omitted or empty. Returns `201` and the new room view. |
| `POST` | `/api/rooms/join` | Join by room ID or exact room name. Body: `{"roomIdOrName":"...","password":"..."}`. |
| `GET` | `/api/rooms/{roomId}` | Get the full room view, including its active games and pending game requests. Caller must be a room member. |
| `POST` | `/api/rooms/{roomId}/host` | Transfer host privileges. Body: `{"username":"..."}`. Only the current host can transfer to another room member. |
| `POST` | `/api/rooms/{roomId}/games` | Host starts a game with the first two room members, if both are not already playing active games. Returns `201` and the initial game state. This endpoint is available to API clients; room members can also start games by accepting requests. |
| `POST` | `/api/rooms/{roomId}/games/requests` | Request a game with another room member. Body: `{"opponent":"..."}`. A member already playing an active game cannot send a request. |
| `POST` | `/api/rooms/{roomId}/games/requests/{requestId}/accept` | Accept an incoming request. Only its invited member can accept; both players must be available. Returns `201` and the initial game state. |
| `POST` | `/api/rooms/{roomId}/games/requests/{requestId}/decline` | Decline an incoming request. Only its invited member can decline. |
| `GET` | `/api/rooms/{roomId}/games/{gameId}` | Get a game in this room. Caller must be a room member; this allows spectators to read the game state. |
| `GET` | `/api/rooms/{roomId}/games/{gameId}/events` | Subscribe to a room game using SSE. Any room member can spectate; subscription ends if the member leaves the room. |
| `DELETE` | `/api/rooms/{roomId}/members/me` | Leave the room. If the current host leaves, the room closes and room members are notified; otherwise only the departing member is removed. |
| `GET` | `/api/rooms/{roomId}/events` | Subscribe to room events using Server-Sent Events (SSE). The caller must be a room member. |

A full room view contains `id`, `name`, `creator` (the current host), `users`, `passwordProtected`, `gameId` (the most recently launched room-started or request-accepted game, or null), `games`, and `gameRequests`. Use `games` as the authoritative list of open games; it contains only games with status `IN_PROGRESS`, including rematches. Finished games remain available to their players through the game API but are omitted from the room's open-games list. Game requests contain an ID, requester username, and invited opponent username. A request disappears when declined, accepted, or when either member leaves. The public room listing indicates whether a room is password-protected but does not expose its password, games, or requests. Typical room errors include `400` for invalid room or member details, `403` for an incorrect password or unauthorized action, `404` for an unknown room or request, and `409` for a duplicate room name, full room, unavailable player, or invalid attempt to start a game.

The room event stream first sends a `room` event containing the current room view. Membership, host, request, game-start, and accepted-rematch changes send `room_updated`, so room members see changes to the open-games list without reloading. The current host leaving sends `room_closed` and closes the room event streams. Moves and other game activity use the game stream.

### Games and chess rules

Games can be started through a room game request or by a room host using the room start endpoint, as well as as local hot-seat games. Every room game has exactly two players, with White assigned randomly. A room member cannot start or accept a game while already playing another in-progress room game; after that game ends they may play again. In a local game, the same authenticated player controls both colors. Each game has its own ID and continues independently of its room, including if that room is later closed.

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/api/games` | Start a local game. Returns `201` and the initial game state. |
| `GET` | `/api/games/{gameId}` | Get the current game state. |
| `POST` | `/api/games/{gameId}/select` | Get legal destinations for a piece. Body: `{"square":"e2"}`. |
| `POST` | `/api/games/{gameId}/move` | Submit a move. Body: `{"from":"e2","to":"e4","promotion":"q"}`; `promotion` is optional. |
| `POST` | `/api/games/{gameId}/forfeit` | Forfeit an in-progress game. |
| `POST` | `/api/games/{gameId}/rematch` | Request a rematch after the game ends. |
| `GET` | `/api/games/{gameId}/events` | Subscribe to game events using SSE. |

Only players in a game can retrieve its state, select pieces, move, or subscribe. In room games, only the player assigned to the color whose turn it is can submit a move. A local game's single player can submit moves for either color. Selection reports legal moves for the selected piece in the current position; it does not enforce the submitting player's turn, meaning a player can check their currently legal moves even if it isn't currently their turn.

Room members who are not players can spectate using `GET /api/rooms/{roomId}/games/{gameId}` and the corresponding room-scoped SSE endpoint listed above. Room members cannot select or move pieces through spectator access. The frontend returns spectators to the room automatically when the game ends, and provides a button to leave the spectator view at any time. Players use the player-authorized `/api/games/{gameId}` routes.

The game engine validates piece movement, captures, king safety, and check. It implements castling (including rights and attacked-square restrictions), en passant, and pawn promotion to queen, rook, bishop, or knight. Promotion defaults to a queen when omitted. Kings cannot be captured. Checkmate and stalemate are detected automatically. The implemented automatic endings are checkmate, stalemate, and forfeit; repetition, the fifty-move rule, and insufficient-material draws are not implemented.

An illegal move is not an HTTP error: it returns `{"moved":false,"gameState":...}` with the unchanged state. A successful move returns `{"moved":true,"gameState":...}`, appends algebraic move notation to `previousTurns`, and emits `game_updated`. Invalid squares or promotion values return `400`; a move submitted out of turn returns `403`, and a move after the game has ended returns `409`.

A game state includes:

| Field | Meaning |
| --- | --- |
| `gameId` | Unique game identifier. |
| `roomId` | Source room ID, or an empty string for a local game. |
| `players` | Usernames of the player(s). |
| `whitePlayer` | Username assigned White. |
| `board` | 8-by-8 array, from rank 8 to rank 1; uppercase pieces are White, lowercase pieces are Black, and empty squares are empty strings. |
| `whiteToMove` | Whether it is White's turn. |
| `previousTurns` | Move history in algebraic notation. |
| `status` | `IN_PROGRESS` or `FINISHED`. |
| `winner`, `winnerColor` | Winner username and `WHITE`/`BLACK`, or null when there is no winner (for example, stalemate). |
| `termination` | `CHECKMATE`, `STALEMATE`, or `FORFEIT` when finished; otherwise null. |
| `rematchRequests` | Players who have requested a rematch. |
| `rematchGameId` | ID of the rematch once started; otherwise null. |

The game event stream immediately sends the current state as `game_started`. It then emits `game_updated` after a legal move, `game_ended` after a forfeit, and `rematch_requested` or `rematch_started` during rematch handling. A room game requires both players to request a rematch; a local game's rematch starts immediately. A rematch is a new game with a new ID. Once a room rematch starts, the room emits `room_updated` so the new active game appears in the open-games list.

## Frontend

The frontend is a Vite single-page application built with React 19 and the Vite React plugin. Vite provides the development server, API proxy, and production build. Production assets are served by Nginx, which also reverse-proxies `/api/` and disables proxy buffering so SSE streams can pass through.

The main React app in `frontend/src/app.jsx` coordinates the current screen, user, room, game, modals, and notifications. Reusable screens and dialogs live under `components`; `hooks` group authentication, room, game, and session-restoration behavior; `utils/api.js` handles JSON requests, CSRF tokens, and following rematch links. Session restoration metadata is kept in browser storage, while the authoritative login, room, and game state is on the server. Chessboard rules and state are authoritative on the backend.

## Project layout

```text
.
|-- backendJava\
|   |-- src\main\java\com\jj\WebChess\   Spring Boot controllers, services, entities, and chess engine
|   |-- src\main\resources\             Backend configuration
|   |-- src\test\                       Backend tests
|   |-- Dockerfile                      Multi-stage backend image
|   \-- pom.xml                         Maven dependencies and build
|-- frontend\
|   |-- src\
|   |   |-- components\                 Game, room, and modal UI
|   |   |-- hooks\                      App workflows and state restoration
|   |   |-- styles\                     Application CSS
|   |   \-- utils\                      API, chess display helpers, browser session storage
|   |-- Dockerfile                      Vite build served by Nginx
|   |-- nginx.conf                      Static hosting and API/SSE proxy
|   |-- package.json                    npm scripts and dependencies
|   \-- vite.config.js                  Vite configuration and development API proxy
|-- database.sql                        MySQL users table schema
|-- docker-compose.yml                  Frontend, backend, and MySQL services
\-- README.md
```

## Feature roadmap

- [ ] **Add player game history**
  - Store each player's 100 most recent games.
  - Save games as a list of turn notations and show the color the player used.
  - Let players browse their history and select a game to review.
  - Provide controls to step forward and backward through the recorded turns.
- [ ] **Prepare the architecture for multiple backend instances**
  - Add a load balancer and support multiple application server instances.
  - Move shared runtime state, including games and rooms, into Redis.
  - Update session management so sessions work across instances.
