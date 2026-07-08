import httpx
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from app.proxy_rest import router as rest_router
from app.proxy_ws import router as ws_router
from app.config import FRONTEND_URL
from app.circuit_breaker import auth_cb, profile_cb, lobby_cb, game_cb

app = FastAPI(title="Hungry Shark Royale API Gateway")

app.add_middleware(
    CORSMiddleware,
    allow_origins=[FRONTEND_URL],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(rest_router)
app.include_router(ws_router)

@app.exception_handler(httpx.ConnectError)
async def http_connect_error_handler(request: Request, exc: httpx.ConnectError):
    return JSONResponse(
        status_code=503,
        content={"message": "Servicio downstream no disponible temporalmente"}
    )

@app.get("/health")
async def health_check():
    """
    Retorna el estado agregado de los 4 circuit breakers, muy útil 
    para diagnosticar rápido qué microservicio backend está caído 
    sin necesidad de sumergirse en los logs individuales de cada uno.
    """
    return {
        "status": "up",
        "circuit_breakers": [
            auth_cb.get_status(),
            profile_cb.get_status(),
            lobby_cb.get_status(),
            game_cb.get_status()
        ]
    }
