# 🦈 Hungry Shark Royale - Backend Architecture

¡Bienvenido al repositorio del backend de **Hungry Shark Royale**! Este proyecto es un sistema multijugador masivo en tiempo real basado en una arquitectura de microservicios resiliente, reactiva y escalable.

---

## 🏗️ Diagrama de Arquitectura Lógica

El backend está diseñado utilizando el estilo arquitectónico de **Microservicios** y el patrón **Event-Driven Architecture (EDA)**. El Gateway actúa como barrera Zero Trust, mientras que los microservicios se comunican asíncronamente mediante colas de mensajes para garantizar el desacoplamiento temporal y espacial.

```mermaid
graph TD
    Client[Cliente Web / Móvil] -->|HTTP/REST & WebSockets| Gateway(API Gateway - FastAPI)
    
    subgraph Microservicios Spring Boot
        Gateway -->|Proxy REST| Auth[Auth Service]
        Gateway -->|Proxy REST| Profile[Profile Service]
        Gateway -->|Proxy WS| Lobby[Lobby Service]
        Gateway -->|Proxy WS| Game[Game Engine Service]
    end

    Auth -->|Guarda Credentials| AuthDB[(Auth DB - PostgreSQL)]
    Profile -->|Guarda Perfiles| ProfileDB[(Profile DB - PostgreSQL)]
    Game -->|Guarda Resultados| GameDB[(Game DB - PostgreSQL)]

    %% Eventos Asíncronos
    Auth -->|Usuario Registrado| RabbitMQ{RabbitMQ}
    Game -->|Partida Finalizada| RabbitMQ
    Lobby -->|Partida Creada| RabbitMQ
    
    RabbitMQ -->|Consume Eventos| Profile

    %% Observabilidad
    Promtail[Promtail] -->|Scrapea Logs| Auth
    Promtail -->|Scrapea Logs| Profile
    Promtail -->|Scrapea Logs| Lobby
    Promtail -->|Scrapea Logs| Game
    
    Promtail -->|Envía Logs| Loki[(Grafana Loki)]
    Loki --> Grafana[Grafana Dashboards]
```

---

## ☁️ Diagrama de Despliegue Estratégico (AWS)

Para llevar este proyecto a producción en Amazon Web Services (AWS) asegurando alta disponibilidad (HA) y tolerancia a fallos, se sugiere la siguiente topología de despliegue:

```mermaid
graph TB
    Client([Cliente]) --> ALB

    subgraph VPC[Amazon VPC - Región AWS]
        
        subgraph Public[Subredes Públicas]
            ALB[Application Load Balancer]
            NAT[NAT Gateway]
        end

        subgraph Private[Subredes Privadas]
            Gateway[API Gateway Pods - FastAPI]
            Microservices[Spring Boot Pods - EKS/ECS]
        end
        
        subgraph Data[Subredes de Datos Privadas]
            RDS[(Amazon RDS - PostgreSQL)]
            RabbitMQ{Amazon MQ - RabbitMQ}
        end

        ALB --> Gateway
        Gateway --> Microservices
        Microservices --> RDS
        Microservices --> RabbitMQ
        Microservices -.->|Egress| NAT
    end
```

### Componentes AWS Sugeridos:
- **EKS / ECS Fargate:** Para orquestar los microservicios (Spring Boot y FastAPI).
- **Amazon RDS (Aurora PostgreSQL):** Bases de datos administradas y escalables.
- **Amazon MQ (RabbitMQ):** Para la mensajería asíncrona totalmente gestionada.
- **ALB (Application Load Balancer):** Punto de entrada único que enruta tráfico REST y mantiene sesiones persistentes para WebSockets.
- **Route 53:** Gestión de DNS.

---

## 🚀 Cómo Empezar (Getting Started)

### Prerrequisitos
- **Docker** y **Docker Compose** instalados.
- Java 17 y Maven (Opcional, si deseas compilar manualmente).
- Python 3.10+ (Opcional, para el Gateway).

### Ejecución Local

1. **Clona el repositorio:**
   ```bash
   git clone https://github.com/tu-usuario/hungry-shark-backend.git
   cd hungry-shark-backend
   ```

2. **Configura el entorno:**
   Verifica el archivo `.env` en la raíz (generado o proporcionado en la configuración).

3. **Levanta la infraestructura completa:**
   Utiliza Docker Compose para levantar Bases de Datos, RabbitMQ, Servicios y Observabilidad:
   ```bash
   docker-compose up --build -d
   ```

4. **Verificación:**
   - API Gateway: `http://localhost:8080`
   - Grafana: `http://localhost:3000` (El dashboard de KPIs ya está pre-configurado).
   - RabbitMQ Management: `http://localhost:15672` (guest/guest).

---

## 📐 Principios de Arquitectura

El diseño de Hungry Shark Royale está guiado por patrones modernos para sistemas de alta carga:

1. **Desacoplamiento (Microservicios):**
   Las responsabilidades están divididas. `auth-service` maneja exclusivamente identidad, `profile-service` maneja estadísticas, y `game-engine-service` procesa las físicas en tiempo real.
   
2. **Arquitectura Orientada a Eventos (EDA):**
   Para evitar cuellos de botella y llamadas síncronas costosas (HTTP REST), los microservicios comunican actualizaciones de estado (ej: fin de partida) emitiendo eventos a **RabbitMQ**.

3. **Patrón Transaccional Outbox (Resiliencia):**
   Implementado en `auth-service` y `game-engine-service`. Si RabbitMQ se cae, los eventos no se pierden en la memoria volátil; se guardan atómicamente en PostgreSQL y un *Relay Scheduler* reintenta el envío cuando el broker vuelve a estar en línea.

4. **Circuit Breaker (Tolerancia a Fallos):**
   El **API Gateway** cuenta con cortacircuitos por microservicio. Si el servidor de perfiles sufre latencia extrema o caída, su circuito se abre (`OPEN`), protegiendo la red de una tormenta de retries, pero permitiendo que el juego (WebSockets) siga operando.

5. **Server-Authoritative Game Engine:**
   *Nunca confíes en el cliente.* El frontend envía solicitudes de movimiento; el backend en memoria (`ActiveGameState`, `MovementValidator`) calcula velocidades máximas y tiempos. Cualquier intento de *Speed Hack* es ignorado silenciosamente.

---

## 💎 Principios S.O.L.I.D. Aplicados

El código fuente en Java y Python fue escrito rigurosamente respetando SOLID:

- **S - Single Responsibility Principle (SRP):** 
  Las clases están especializadas. En `game-engine-service`, las colisiones están en `CollisionDetector`, el puntaje en `ScoreCalculator`, y el routing STOMP en `GameController`.
  
- **O - Open/Closed Principle (OCP):** 
  El uso de interfaces y manejadores de eventos (ej: `@EventListener`) permite agregar nuevos listeners (como notificaciones push) sin tocar el código central que emite el evento.
  
- **L - Liskov Substitution Principle (LSP):** 
  Los interceptores y manejadores de excepciones globales (`@ControllerAdvice`) extienden y reemplazan limpiamente comportamientos base sin romper contratos HTTP, retornando siempre `ErrorResponseDto` estandarizados.
  
- **I - Interface Segregation Principle (ISP):** 
  En vez de repositorios monolíticos, JPA se utiliza con repositorios atómicos (`GameSessionRepository`, `OutboxEventRepository`), evitando inyectar métodos innecesarios a los servicios.
  
- **D - Dependency Inversion Principle (DIP):** 
  El acoplamiento se maneja mediante Inyección de Dependencias. Todo, desde clientes REST (`httpx` en Python) hasta `ActiveGameManager` en Java, se inyecta por constructor, facilitando pruebas unitarias exhaustivas con Mocks.

---

## 📌 Estrategia de Versionamiento

El proyecto adopta **Semantic Versioning (SemVer 2.0.0)** con el formato `MAJOR.MINOR.PATCH`:

- **MAJOR (1.x.x):** Cambios arquitectónicos grandes o rotura de contratos de API REST / WebSockets.
- **MINOR (x.1.x):** Nuevas funcionalidades (ej: nuevo tipo de obstáculo en el mapa) que son retrocompatibles.
- **PATCH (x.x.1):** Corrección de bugs, ajustes de *Circuit Breakers* o *Throttling*.

### Git Flow Simplificado:
- `main`: Rama estable, siempre desplegable a Producción.
- `develop`: Integración continua de nuevas features.
- `feature/*`: Ramas efímeras para desarrollo de HU/KAN.

---
*Desarrollado con arquitectura resiliente para soportar el apetito insaciable de miles de tiburones simultáneos.* 🦈🌊

---

## 🧪 Suite de Pruebas Completa

El proyecto cuenta con una suite integral que cubre todos los niveles de la pirámide de pruebas.

### Mapa de Cobertura

| Tipo de Prueba | Caja | Archivo | Microservicio | Herramienta |
|---|---|---|---|---|
| Unitaria + Caja Blanca | ⬜ Blanca | `AuthServiceTest.java` | `auth-service` | JUnit 5 + Mockito |
| Unitaria + Caja Blanca | ⬜ Blanca | `GameEngineWhiteBoxTest.java` | `game-engine-service` | JUnit 5 |
| Integración + Caja Gris | 🔲 Gris | `LobbyManagerIntegrationTest.java` | `lobby-service` | JUnit 5 |
| Integración + Caja Gris | 🔲 Gris | `ProfileServiceGrayBoxTest.java` | `profile-service` | JUnit 5 + Mockito |
| Funcional + Caja Negra | ⬛ Negra | `ProfileControllerBlackBoxTest.java` | `profile-service` | MockMvc |
| Extremo a Extremo (E2E) | ⬛ Negra | `test_auth_flow.py` | Toda la infra | pytest + httpx |
| No Funcional (Carga) | ⬛ Negra | `locustfile.py` | API Gateway | Locust |

---

### ▶️ Cómo Ejecutar las Pruebas

#### 1. Pruebas Unitarias y de Integración (Spring Boot — sin dependencias externas)

```bash
# Auth Service
cd auth-service && mvn test

# Profile Service
cd profile-service && mvn test

# Lobby Service
cd lobby-service && mvn test

# Game Engine Service
cd game-engine-service && mvn test
```

#### 2. Pruebas Funcionales (Caja Negra con MockMvc — sin levantar Docker)

```bash
# Estas corren como parte del 'mvn test' de cada microservicio
cd profile-service && mvn test -Dtest=ProfileControllerBlackBoxTest
```

#### 3. Pruebas E2E (Requiere toda la infraestructura levantada)

```bash
# 1. Primero levanta la infraestructura completa
docker-compose up --build -d

# 2. Instala dependencias Python
cd e2e-tests && pip install -r requirements.txt

# 3. Ejecuta los tests E2E
pytest test_auth_flow.py -v
```

**Flujo E2E validado:**
```
Cliente (pytest) → API Gateway :8080 → Auth Service → PostgreSQL → RabbitMQ → Profile Service → PostgreSQL
```

#### 4. Pruebas No Funcionales (Carga — Requiere infraestructura levantada)

```bash
# Instala Locust
cd performance-tests && pip install -r requirements.txt

# Modo interactivo (UI en http://localhost:8089)
locust -f locustfile.py --host=http://localhost:8080

# Modo headless (100 usuarios, spawn 10/s, 60 segundos)
locust -f locustfile.py --host=http://localhost:8080 \
       --users 100 --spawn-rate 10 --run-time 60s --headless
```

**Métricas NFR esperadas bajo 100 usuarios concurrentes:**
- Latencia P95 de login: `< 500ms`
- Latencia P99 de health: `< 100ms`
- Tasa de error: `< 1%`
- Throughput mínimo: `> 50 RPS`


