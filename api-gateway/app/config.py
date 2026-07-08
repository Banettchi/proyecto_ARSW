import os
# pyrefly: ignore [missing-import]
from dotenv import load_dotenv

load_dotenv()

JWT_SECRET = os.getenv("JWT_SECRET", "8a9b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z")
FRONTEND_URL = os.getenv("FRONTEND_URL", "http://localhost:3000")

AUTH_SERVICE_URL = os.getenv("AUTH_SERVICE_URL", "http://auth-service:8081")
PROFILE_SERVICE_URL = os.getenv("PROFILE_SERVICE_URL", "http://profile-service:8082")
LOBBY_SERVICE_URL = os.getenv("LOBBY_SERVICE_URL", "http://lobby-service:8083")
GAME_SERVICE_URL = os.getenv("GAME_SERVICE_URL", "http://game-engine-service:8084")
