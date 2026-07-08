import httpx
from fastapi import APIRouter, Request, Response, Depends
from fastapi.responses import JSONResponse
from app.config import AUTH_SERVICE_URL, PROFILE_SERVICE_URL
from app.circuit_breaker import auth_cb, profile_cb, CircuitBreakerOpenException
from app.security import get_current_user_id

router = APIRouter()
http_client = httpx.AsyncClient(timeout=10.0)

async def forward_request(client: httpx.AsyncClient, method: str, url: str, headers: dict, body: bytes) -> Response:
    req = client.build_request(method, url, headers=headers, content=body)
    resp = await client.send(req)
    return Response(
        content=resp.content,
        status_code=resp.status_code,
        headers=dict(resp.headers)
    )

@router.api_route("/api/auth/{path:path}", methods=["GET", "POST", "PUT", "DELETE", "PATCH"])
async def proxy_auth(path: str, request: Request):
    url = f"{AUTH_SERVICE_URL}/api/auth/{path}"
    headers = dict(request.headers)
    headers.pop("host", None)
    
    body = await request.body()
    try:
        return await auth_cb.call_with_breaker(
            forward_request, http_client, request.method, url, headers, body
        )
    except CircuitBreakerOpenException as e:
        return JSONResponse(status_code=503, content={"message": str(e)})

@router.api_route("/api/profiles/{path:path}", methods=["GET", "POST", "PUT", "DELETE", "PATCH"])
async def proxy_profiles(path: str, request: Request, user_id: str = Depends(get_current_user_id)):
    url = f"{PROFILE_SERVICE_URL}/api/profiles/{path}"
    headers = dict(request.headers)
    headers.pop("host", None)
    
    # Inyectar header interno para el profile-service (Zero Trust pattern)
    headers["X-User-Id"] = user_id
    
    body = await request.body()
    try:
        return await profile_cb.call_with_breaker(
            forward_request, http_client, request.method, url, headers, body
        )
    except CircuitBreakerOpenException as e:
        return JSONResponse(status_code=503, content={"message": str(e)})

@router.api_route("/api/rankings/{path:path}", methods=["GET", "POST", "PUT", "DELETE", "PATCH"])
async def proxy_rankings(path: str, request: Request, user_id: str = Depends(get_current_user_id)):
    url = f"{PROFILE_SERVICE_URL}/api/rankings/{path}"
    headers = dict(request.headers)
    headers.pop("host", None)
    
    headers["X-User-Id"] = user_id
    
    body = await request.body()
    try:
        return await profile_cb.call_with_breaker(
            forward_request, http_client, request.method, url, headers, body
        )
    except CircuitBreakerOpenException as e:
        return JSONResponse(status_code=503, content={"message": str(e)})
