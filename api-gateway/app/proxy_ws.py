import asyncio
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from websockets.client import connect as ws_connect
from websockets.exceptions import ConnectionClosed
from app.config import LOBBY_SERVICE_URL, GAME_SERVICE_URL
from app.security import verify_jwt_token

router = APIRouter()

async def forward_ws(client_ws: WebSocket, target_url: str):
    """
    IMPORTANTE DECISION ARQUITECTÓNICA:
    Este proxy WS es "dumb". No interpreta frames STOMP individuales, solo hace 
    forwarding de bytes bidireccionalmente. La validación de negocio fina 
    (autorización por sala, Principal injection) la hace cada microservicio 
    internamente en su propio interceptor STOMP (Defensa en profundidad). 
    El Gateway aquí solo actúa como barrera perimetral para garantizar 
    que nadie pueda siquiera establecer conexión TCP si no posee un JWT válido.
    """
    try:
        async with ws_connect(target_url) as backend_ws:
            async def client_to_backend():
                try:
                    while True:
                        data = await client_ws.receive_text()
                        await backend_ws.send(data)
                except (WebSocketDisconnect, ConnectionClosed):
                    pass

            async def backend_to_client():
                try:
                    while True:
                        data = await backend_ws.recv()
                        await client_ws.send_text(data)
                except (WebSocketDisconnect, ConnectionClosed):
                    pass
            
            await asyncio.gather(client_to_backend(), backend_to_client())
    except Exception as e:
        # Falla la conexion al backend (puede que esté caído)
        await client_ws.close(code=1011)

@router.websocket("/ws-lobby")
async def websocket_lobby_proxy(websocket: WebSocket, token: str = None):
    # Se recomienda usar query param '?token=' para la inicialización WS porque
    # la API nativa de WebSockets (new WebSocket()) de los navegadores 
    # NO permite setear headers custom (como Authorization).
    await websocket.accept()
    
    if not token:
        await websocket.close(code=4001, reason="Token requerido")
        return
        
    try:
        verify_jwt_token(token)
    except Exception:
        await websocket.close(code=4001, reason="Token invalido")
        return

    # ws:// en lugar de http://
    target_url = LOBBY_SERVICE_URL.replace("http://", "ws://") + "/ws-lobby"
    await forward_ws(websocket, target_url)

@router.websocket("/ws-game")
async def websocket_game_proxy(websocket: WebSocket, token: str = None):
    await websocket.accept()
    
    if not token:
        await websocket.close(code=4001, reason="Token requerido")
        return
        
    try:
        verify_jwt_token(token)
    except Exception:
        await websocket.close(code=4001, reason="Token invalido")
        return

    target_url = GAME_SERVICE_URL.replace("http://", "ws://") + "/ws-game"
    await forward_ws(websocket, target_url)
