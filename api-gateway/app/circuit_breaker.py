import time
import asyncio
from enum import Enum

class CircuitState(Enum):
    CLOSED = "CLOSED"
    OPEN = "OPEN"
    HALF_OPEN = "HALF_OPEN"

class CircuitBreakerOpenException(Exception):
    def __init__(self, service_name: str):
        self.service_name = service_name
        super().__init__(f"Servicio {service_name} no disponible temporalmente (Circuit Breaker OPEN)")

class SimpleCircuitBreaker:
    def __init__(self, name: str, threshold: int = 5, recovery_timeout: int = 30):
        self.name = name
        self.threshold = threshold
        self.recovery_timeout = recovery_timeout
        self.state = CircuitState.CLOSED
        self.failures = 0
        self.last_failure_time = 0.0

    async def call_with_breaker(self, func, *args, **kwargs):
        if self.state == CircuitState.OPEN:
            if time.time() - self.last_failure_time >= self.recovery_timeout:
                self.state = CircuitState.HALF_OPEN
            else:
                raise CircuitBreakerOpenException(self.name)

        try:
            result = await func(*args, **kwargs)
            
            # Si tuvo exito y estaba HALF_OPEN, se recupera el servicio
            if self.state == CircuitState.HALF_OPEN:
                self.state = CircuitState.CLOSED
                self.failures = 0
                
            return result
        except Exception as e:
            self.failures += 1
            self.last_failure_time = time.time()
            if self.failures >= self.threshold or self.state == CircuitState.HALF_OPEN:
                self.state = CircuitState.OPEN
            raise e

    def get_status(self) -> dict:
        return {
            "name": self.name,
            "state": self.state.value,
            "failures": self.failures
        }

# Instanciamos uno por microservicio descendente para aislar las fallas
auth_cb = SimpleCircuitBreaker("auth-service")
profile_cb = SimpleCircuitBreaker("profile-service")
lobby_cb = SimpleCircuitBreaker("lobby-service")
game_cb = SimpleCircuitBreaker("game-engine-service")
