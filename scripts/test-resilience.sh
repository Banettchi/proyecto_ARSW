#!/bin/bash
set -e

echo "============================================================"
echo "    Iniciando prueba de resiliencia (Outbox Pattern)"
echo "============================================================"

RANDOM_ID=$RANDOM
USERNAME="player_$RANDOM_ID"
EMAIL="player_$RANDOM_ID@test.com"

# a. Levantar todo con docker compose up -d
echo "⏳ [Paso A] Levantando contenedores (docker compose up -d --build)..."
docker compose up -d --build

# b. Esperar a que auth-service y profile-service respondan en /actuator/health
echo "⏳ [Paso B] Esperando que auth-service y profile-service estén UP..."
for i in {1..30}; do
    AUTH_STATUS=$(curl -s http://localhost:8081/actuator/health | grep UP || echo "DOWN")
    PROFILE_STATUS=$(curl -s http://localhost:8082/actuator/health | grep UP || echo "DOWN")
    
    if [ "$AUTH_STATUS" != "DOWN" ] && [ "$PROFILE_STATUS" != "DOWN" ]; then
        echo "✅ Ambos servicios están UP."
        break
    fi
    echo "   ... esperando 5 segundos (intento $i de 30)"
    sleep 5
done

if [ "$AUTH_STATUS" == "DOWN" ] || [ "$PROFILE_STATUS" == "DOWN" ]; then
    echo "❌ Falló en el paso B: Los servicios no levantaron a tiempo."
    exit 1
fi

# c. Detener RabbitMQ
echo "🔥 [Paso C] Deteniendo RabbitMQ para simular caída..."
docker compose stop rabbitmq

# d. Hacer un POST /api/auth/register vía curl
echo "📩 [Paso D] Registrando usuario ($USERNAME) sin RabbitMQ..."
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8081/api/auth/register \
     -H "Content-Type: application/json" \
     -d "{\"username\":\"$USERNAME\",\"email\":\"$EMAIL\",\"password\":\"password123\"}")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -eq 201 ]; then
    echo "✅ Registro exitoso (HTTP 201). El servicio funcionó sin RabbitMQ."
    USER_ID=$(echo $BODY | grep -o '"userId":"[^"]*' | cut -d'"' -f4 || echo "")
else
    echo "❌ Falló en el paso D: El registro retornó HTTP $HTTP_CODE"
    echo "Body: $BODY"
    exit 1
fi

# e. Verificar en auth_db que el OutboxEvent quedó en PENDING
echo "🔍 [Paso E] Verificando estado PENDING en auth_db..."
# Usamos un retry rápido por si la base de datos demora un milisegundo en confirmar el insert
for i in {1..3}; do
    DB_STATUS=$(docker compose exec -T auth_db psql -U auth_user -d auth_db -t -c "SELECT status FROM outbox_events WHERE aggregate_type = 'User' ORDER BY created_at DESC LIMIT 1;" | xargs)
    if [ "$DB_STATUS" == "0" ] || [ "$DB_STATUS" == "PENDING" ]; then
        break
    fi
    sleep 1
done

if [ "$DB_STATUS" == "0" ] || [ "$DB_STATUS" == "PENDING" ]; then
    echo "✅ OutboxEvent en estado PENDING confirmado (Status=$DB_STATUS)."
else
    echo "❌ Falló en el paso E: Se esperaba PENDING (o ordinal 0) pero se encontró '$DB_STATUS'"
    exit 1
fi

# f. Volver a levantar RabbitMQ
echo "♻️ [Paso F] Levantando RabbitMQ nuevamente..."
docker compose start rabbitmq

# g. Esperar a que OutboxRelayScheduler actúe y RabbitMQ suba
echo "⏳ [Paso G] Esperando 20s para que RabbitMQ arranque y OutboxRelayScheduler publique..."
sleep 20

DB_STATUS2=$(docker compose exec -T auth_db psql -U auth_user -d auth_db -t -c "SELECT status FROM outbox_events WHERE aggregate_type = 'User' ORDER BY created_at DESC LIMIT 1;" | xargs)

if [ "$DB_STATUS2" == "1" ] || [ "$DB_STATUS2" == "PUBLISHED" ]; then
    echo "✅ OutboxEvent en estado PUBLISHED confirmado (Status=$DB_STATUS2)."
else
    echo "❌ Falló en el paso G: Se esperaba PUBLISHED (o ordinal 1) pero se encontró '$DB_STATUS2'"
    exit 1
fi

# h. Verificar con GET /api/profiles/me
echo "🔍 [Paso H] Verificando creación del perfil vía consumer de RabbitMQ..."
# Dar 5 segundos extra para que el consumer termine de guardar en profile_db
sleep 5
PROFILE_HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X GET http://localhost:8082/api/profiles/me -H "X-User-Id: $USER_ID")

if [ "$PROFILE_HTTP_CODE" -eq 200 ]; then
    echo "✅ Perfil creado exitosamente en profile-service."
else
    echo "❌ Falló en el paso H: El perfil no fue encontrado en profile-service (HTTP $PROFILE_HTTP_CODE)"
    exit 1
fi

# i. Imprimir resumen
echo "============================================================"
echo "✅ Prueba de resiliencia exitosa"
echo "============================================================"
