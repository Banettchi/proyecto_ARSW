import uuid
import time
from locust import HttpUser, task, between, events

"""
======================== PRUEBAS NO FUNCIONALES (Rendimiento / Carga) ========================

Tipo: No Funcionales (Non-Functional Tests) - Performance / Load Testing

Justificación:
- Se evalúa el comportamiento del sistema bajo carga concurrente.
- Se miden: latencia (p50, p95, p99), throughput (TPS), tasa de errores.
- Se valida que los Circuit Breakers del Gateway se comporten correctamente
  cuando un microservicio se degrada bajo estrés.
- Permite detectar cuellos de botella en la cadena Gateway -> Auth -> DB.

Ejecución:
  cd performance-tests
  pip install -r requirements.txt
  locust -f locustfile.py --host=http://localhost:8080

  Luego abrir http://localhost:8089 para la UI de Locust.

Configuración sugerida para prueba de carga:
  - Usuarios: 100
  - Spawn rate: 10/s
  - Duración: 60s
"""


class AuthLoadUser(HttpUser):
    """Simula un usuario que se registra y luego hace login repetidamente."""
    
    wait_time = between(0.5, 2.0)
    
    def on_start(self):
        """Se ejecuta una vez al inicio: registra un usuario único."""
        self.username = f"load_{uuid.uuid4().hex[:10]}"
        self.email = f"{self.username}@loadtest.com"
        self.password = "LoadTestPass123"
        
        with self.client.post(
            "/api/auth/register",
            json={
                "username": self.username,
                "email": self.email,
                "password": self.password
            },
            catch_response=True,
            name="/api/auth/register"
        ) as response:
            if response.status_code == 200:
                data = response.json()
                self.token = data.get("token", "")
                response.success()
            else:
                response.failure(f"Registro falló: {response.status_code}")
                self.token = ""

    @task(3)
    def login(self):
        """Tarea principal: login repetido (simula sesiones recurrentes)."""
        with self.client.post(
            "/api/auth/login",
            json={
                "email": self.email,
                "password": self.password
            },
            catch_response=True,
            name="/api/auth/login"
        ) as response:
            if response.status_code == 200:
                self.token = response.json().get("token", self.token)
                response.success()
            else:
                response.failure(f"Login falló: {response.status_code}")

    @task(2)
    def get_profile(self):
        """Consulta el perfil del usuario autenticado."""
        if not self.token:
            return
            
        with self.client.get(
            "/api/profiles/me",
            headers={"Authorization": f"Bearer {self.token}"},
            catch_response=True,
            name="/api/profiles/me"
        ) as response:
            if response.status_code in [200, 404]:
                response.success()
            else:
                response.failure(f"Profile falló: {response.status_code}")

    @task(1)
    def health_check(self):
        """Verifica el estado del Gateway y los circuit breakers."""
        self.client.get("/health", name="/health")


class GatewayStressUser(HttpUser):
    """Simula tráfico agresivo al health endpoint para evaluar el throughput puro del Gateway."""
    
    wait_time = between(0.1, 0.5)

    @task
    def rapid_health(self):
        self.client.get("/health", name="/health [stress]")

    @task
    def invalid_auth_attempt(self):
        """Intenta acceder sin token para medir el overhead de validación JWT."""
        with self.client.get(
            "/api/profiles/me",
            catch_response=True,
            name="/api/profiles/me [no-auth]"
        ) as response:
            if response.status_code == 401:
                response.success()
            else:
                response.failure(f"Esperado 401, recibido {response.status_code}")
