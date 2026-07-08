import httpx
import pytest
import uuid
import time

"""
======================== PRUEBAS EXTREMO A EXTREMO (E2E) ========================

Tipo: End-to-End (E2E)

Justificación:
- Se simula el flujo COMPLETO de un usuario real atacando el sistema 
  desde el API Gateway (http://localhost:8080), sin conocimiento alguno 
  de la arquitectura interna.
- El flujo atraviesa: Cliente -> API Gateway -> Auth Service -> PostgreSQL
  -> RabbitMQ -> Profile Service -> PostgreSQL.
- Valida que toda la infraestructura Docker Compose funciona en conjunto.

Prerrequisitos:
- docker-compose up --build -d (toda la infraestructura levantada)
- pip install -r requirements.txt
- pytest test_auth_flow.py -v
"""

GATEWAY_URL = "http://localhost:8080"


class TestAuthFlowE2E:
    """Flujo E2E: Registro -> Login -> Consulta de Perfil"""

    def test_01_register_new_user(self):
        """E2E: Registrar un usuario nuevo devuelve JWT válido"""
        unique_username = f"e2e_shark_{uuid.uuid4().hex[:8]}"
        payload = {
            "username": unique_username,
            "email": f"{unique_username}@test.com",
            "password": "SecureTestPass123"
        }

        response = httpx.post(f"{GATEWAY_URL}/api/auth/register", json=payload, timeout=10.0)

        assert response.status_code == 200, f"Esperado 200, recibido {response.status_code}: {response.text}"
        data = response.json()
        assert "token" in data, "La respuesta debe contener un token JWT"
        assert data["username"] == unique_username
        assert "userId" in data

        # Guardar token para los siguientes tests
        TestAuthFlowE2E.jwt_token = data["token"]
        TestAuthFlowE2E.user_id = data["userId"]
        TestAuthFlowE2E.username = unique_username

    def test_02_register_duplicate_username_returns_409(self):
        """E2E: Registrar con username duplicado retorna 409 Conflict"""
        payload = {
            "username": TestAuthFlowE2E.username,
            "email": f"duplicate_{uuid.uuid4().hex[:6]}@test.com",
            "password": "AnotherPass123"
        }

        response = httpx.post(f"{GATEWAY_URL}/api/auth/register", json=payload, timeout=10.0)

        assert response.status_code == 409, f"Esperado 409 Conflict, recibido {response.status_code}"

    def test_03_login_with_valid_credentials(self):
        """E2E: Login con credenciales correctas retorna nuevo JWT"""
        payload = {
            "email": f"{TestAuthFlowE2E.username}@test.com",
            "password": "SecureTestPass123"
        }

        response = httpx.post(f"{GATEWAY_URL}/api/auth/login", json=payload, timeout=10.0)

        assert response.status_code == 200
        data = response.json()
        assert "token" in data

    def test_04_login_with_wrong_password_returns_401(self):
        """E2E: Login con password incorrecto retorna 401"""
        payload = {
            "email": f"{TestAuthFlowE2E.username}@test.com",
            "password": "WrongPasswordHere"
        }

        response = httpx.post(f"{GATEWAY_URL}/api/auth/login", json=payload, timeout=10.0)

        assert response.status_code == 401

    def test_05_profile_created_via_rabbitmq_event(self):
        """E2E: Después del registro, el perfil existe (creado vía RabbitMQ, no HTTP directo)
        
        Este test valida la cadena completa:
        Auth -> OutboxRelay -> RabbitMQ -> ProfileService.UserRegisteredListener -> PostgreSQL
        """
        # Esperar propagación del evento async (Outbox -> RabbitMQ -> Consumer)
        time.sleep(3)

        headers = {"Authorization": f"Bearer {TestAuthFlowE2E.jwt_token}"}
        response = httpx.get(f"{GATEWAY_URL}/api/profiles/me", headers=headers, timeout=10.0)

        assert response.status_code == 200, f"Perfil debería existir. Status: {response.status_code}, Body: {response.text}"
        data = response.json()
        assert data["sharkName"] == TestAuthFlowE2E.username
        assert data["level"] == 1
        assert data["colorHex"] == "#00D2FF"

    def test_06_access_profile_without_token_returns_401(self):
        """E2E: Acceder al perfil sin JWT retorna 401 (Gateway lo bloquea)"""
        response = httpx.get(f"{GATEWAY_URL}/api/profiles/me", timeout=10.0)

        assert response.status_code == 401


class TestGatewayHealthE2E:
    """E2E: Verificación del estado del Gateway y Circuit Breakers"""

    def test_health_endpoint_returns_circuit_breaker_status(self):
        """E2E: /health retorna estado de los 4 circuit breakers"""
        response = httpx.get(f"{GATEWAY_URL}/health", timeout=10.0)

        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "up"
        assert len(data["circuit_breakers"]) == 4

        for cb in data["circuit_breakers"]:
            assert "name" in cb
            assert "state" in cb
            assert cb["state"] in ["CLOSED", "OPEN", "HALF_OPEN"]


class TestWeakPasswordE2E:
    """E2E: Validaciones de seguridad de contraseña"""

    def test_register_weak_password_returns_400(self):
        """E2E: Contraseña débil (< 8 chars) es rechazada por el sistema completo"""
        payload = {
            "username": f"weak_{uuid.uuid4().hex[:8]}",
            "email": f"weak_{uuid.uuid4().hex[:6]}@test.com",
            "password": "short"
        }

        response = httpx.post(f"{GATEWAY_URL}/api/auth/register", json=payload, timeout=10.0)

        assert response.status_code == 400
