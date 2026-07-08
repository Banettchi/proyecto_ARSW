# Contratos de Eventos - Hungry Shark Royale

Este documento es la **ÚNICA fuente de verdad** sobre los eventos que viajan por RabbitMQ entre los microservicios de Hungry Shark Royale. Aquí se define la estructura, enrutamiento y políticas de resiliencia para cada evento, asegurando el bajo acoplamiento y la alta disponibilidad del sistema.

---

## 1. UserRegisteredEvent

- **Exchange**: `shark.events` (topic)
- **Routing Key**: `user.registered`
- **Publicador**: `auth-service` (vía Outbox Pattern, garantizando que el evento solo se publica si la transacción de registro fue exitosa en `auth_db`).
- **Consumidor(es)**: `profile-service`
- **Cola**: `profile.user-registered.queue`
- **Payload JSON**:
  ```json
  {
    "userId": "123e4567-e89b-12d3-a456-426614174000",
    "username": "shark_killer99",
    "email": "player@example.com",
    "registeredAt": "2026-07-08T04:30:00Z"
  }
  ```
- **Efecto en el consumidor**: Crea el perfil inicial (`SharkProfile`) del jugador en la base de datos `profile_db`.
- **Idempotente**: **SÍ**. El `profile-service` debe utilizar una operación *upsert* (basada en el `userId`). Si el mensaje llega duplicado, nunca se debe crear un perfil repetido ni lanzar error, simplemente se ignora o sobrescribe con los mismos datos.
- **Tolerancia a fallos**: Si el consumidor falla al procesarlo, se reintenta 3 veces con *backoff exponencial* (1s, 5s, 15s). Si falla definitivamente, se envía a la Dead Letter Queue (DLQ): `profile.user-registered.dlq`.

---

## 2. GameSessionFinishedEvent

- **Exchange**: `shark.events` (topic)
- **Routing Key**: `game.finished`
- **Publicador**: `game-engine-service` (vía Outbox Pattern en `game_db`).
- **Consumidor(es)**: `profile-service`
- **Cola**: `profile.game-finished.queue`
- **Payload JSON**:
  ```json
  {
    "sessionId": "987fcdeb-51a2-43d7-9012-345678912345",
    "roomId": "room-lobby-abc",
    "results": [
      {
        "userId": "123e4567-e89b-12d3-a456-426614174000",
        "finalPosition": 1,
        "score": 1500,
        "maxSizeReached": 25.5
      }
    ],
    "finishedAt": "2026-07-08T04:45:00Z"
  }
  ```
- **Efecto en el consumidor**: Actualiza el `total_score` acumulado y guarda una entrada del historial de partidas en el perfil de cada jugador incluido en la lista `results`.
- **Idempotente**: **SÍ**. Se debe utilizar el `sessionId` como clave de deduplicación. Si el `profile-service` ya ha procesado una actualización para un `userId` con un `sessionId` específico, debe ignorar el mensaje silenciosamente.
- **Tolerancia a fallos**: Si el consumidor falla al procesarlo, se reintenta 3 veces con *backoff exponencial* (1s, 5s, 15s). Si falla definitivamente, se envía a la Dead Letter Queue (DLQ): `profile.game-finished.dlq`.

---

## 3. RoomCreatedEvent / RoomClosedEvent

> **Nota Excepcional**: Estos eventos existen con fines de **observabilidad y auditoría**, no para la lógica core del negocio transaccional.

- **Exchange**: `shark.events` (topic)
- **Routing Key**: `room.created` / `room.closed`
- **Publicador**: `lobby-service` (Publicación DIRECTA, *no outbox*).
  - *Trade-off*: Como `lobby-service` funciona 100% en memoria y no tiene base de datos propia, no puede implementar el patrón Outbox. Si RabbitMQ cae en el instante exacto de abrir/cerrar una sala, el evento de auditoría se pierde. Sin embargo, la sala continuará funcionando sin problemas en memoria. Aceptamos la degradación de la observabilidad en favor de mantener el lobby rápido y sin estado persistente estricto.
- **Consumidor(es)**: Ningún microservicio funcional lo consume en este momento. Se utilizará para captura de métricas y logs a través de Promtail/Loki.
- **Cola**: N/A (Consumido directamente por agentes de logs si se routea a una cola de auditoría opcional, o simplemente descartado si no hay bindings).
- **Payload JSON (Ejemplo RoomCreated)**:
  ```json
  {
    "roomId": "room-lobby-abc",
    "hostId": "123e4567-e89b-12d3-a456-426614174000",
    "maxPlayers": 10,
    "createdAt": "2026-07-08T04:35:00Z"
  }
  ```
- **Idempotente**: N/A (Eventos puramente informativos).

---

## Tabla Resumen de Enrutamiento

| Exchange | Routing Key | Publicador | Consumidor | Cola | DLQ | Idempotente |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `shark.events` | `user.registered` | `auth-service` | `profile-service` | `profile.user-registered.queue` | `profile.user-registered.dlq` | Sí |
| `shark.events` | `game.finished` | `game-engine-service` | `profile-service` | `profile.game-finished.queue` | `profile.game-finished.dlq` | Sí |
| `shark.events` | `room.created` | `lobby-service` | - (Observabilidad) | - | - | N/A |
| `shark.events` | `room.closed` | `lobby-service` | - (Observabilidad) | - | - | N/A |

---

## Principios de Resiliencia

1. **Transactional Outbox en Servicios con Base de Datos**:
   Implementamos el patrón *Outbox* en `auth-service`, `profile-service` y `game-engine-service` porque poseen bases de datos propias (`auth_db`, `profile_db`, `game_db`). Esto garantiza que la actualización del estado de dominio (ej. registrar un usuario) y la publicación del evento sean atómicas. Si RabbitMQ se cae, los eventos quedan guardados en la tabla `outbox_events` y un *Relay Scheduler* intentará publicarlos asíncronamente cuando el broker vuelva a estar disponible, evitando la pérdida de mensajes críticos de negocio.
   *Excepción*: El `lobby-service` funciona en memoria o redis in-memory, por lo cual no aplica este patrón (ver sección 3 para el trade-off).

2. **Acknowledgment Manual (Manual Ack)**:
   Cada consumidor en el sistema está configurado para usar `manual-ack` (y NO `auto-ack`). Esto asegura que RabbitMQ solo elimina el mensaje de la cola *después* de que el microservicio haya procesado la lógica (como insertar el `SharkProfile`) y confirmado el éxito explícitamente. Si el consumidor falla a mitad del proceso, el mensaje vuelve a la cola y se reintenta.

3. **Tolerancia y Degradación Controlada**:
   Si RabbitMQ sufre una caída prolongada, el sistema se degrada de forma controlada y resiliente:
   - Un jugador que se registra puede acceder a su cuenta y hacer login de inmediato (funcionalidad core activa en `auth-service`).
   - El evento de creación de perfil se retrasa (queda en el Outbox de `auth_db`).
   - El impacto para el negocio es mínimo: el jugador notará un ligero retraso al intentar ver sus estadísticas o perfil (`profile-service` aún no tiene el registro inicial), pero el sistema core sigue operando sin interrupciones. Al volver RabbitMQ, el sistema procesará el backlog logrando la **Consistencia Eventual**.
