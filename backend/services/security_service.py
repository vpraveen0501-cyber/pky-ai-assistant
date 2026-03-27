import os
import logging
import datetime
import asyncio
from typing import Optional, Dict, Any
from jose import JWTError, jwt
from passlib.context import CryptContext
from cryptography.fernet import Fernet
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
import base64
from config.settings import settings

logger = logging.getLogger(__name__)

# Security configuration from settings
SECRET_KEY = settings.secret_key
ALGORITHM = settings.algorithm
ACCESS_TOKEN_EXPIRE_MINUTES = settings.access_token_expire_minutes

class SecurityService:
    def __init__(self, master_key_str: Optional[str] = None):
        # Use encryption_key from settings as the master key if not provided
        key_str = master_key_str or settings.encryption_key
        if not key_str:
            raise RuntimeError(
                "Encryption key is not set. Add PKY_AI_ENCRYPTION_KEY to your .env file."
            )
        salt = self._load_or_create_salt()
        self.master_key = self._derive_key(key_str, salt)
        self.cipher_suite = Fernet(self.master_key)
        self.pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

    def _load_or_create_salt(self) -> bytes:
        """Loads a persistent random salt from disk, or creates one on first run."""
        salt_path = os.path.join(settings.data_dir, ".encryption_salt")
        if os.path.exists(salt_path):
            with open(salt_path, "rb") as f:
                return f.read()
        salt = os.urandom(16)
        os.makedirs(settings.data_dir, exist_ok=True)
        with open(salt_path, "wb") as f:
            f.write(salt)
        logger.info("New encryption salt created and stored.")
        return salt

    def _derive_key(self, password: str, salt: bytes) -> bytes:
        """Derives a 32-byte key from a password and a salt using PBKDF2."""
        kdf = PBKDF2HMAC(
            algorithm=hashes.SHA256(),
            length=32,
            salt=salt,
            iterations=600000,
        )
        key = base64.urlsafe_b64encode(kdf.derive(password.encode()))
        return key

    def create_access_token(self, data: Dict[str, Any], expires_delta: Optional[datetime.timedelta] = None) -> str:
        """Creates a JWT access token."""
        to_encode = data.copy()
        if expires_delta:
            expire = datetime.datetime.now(datetime.timezone.utc) + expires_delta
        else:
            expire = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)

        to_encode.update({"exp": expire, "iat": datetime.datetime.now(datetime.timezone.utc)})
        encoded_jwt = jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)
        return encoded_jwt

    def verify_token(self, token: str, allow_expired: bool = False) -> Optional[Dict[str, Any]]:
        """Verifies a JWT access token. Set allow_expired=True to decode expired tokens for refresh."""
        try:
            options = {"verify_exp": not allow_expired}
            payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM], options=options)
            return payload
        except JWTError as e:
            logger.error(f"JWT Verification failed: {e}")
            return None

    def encrypt_data(self, data: str) -> str:
        """Encrypts string data using AES-256 (Fernet)."""
        if not data:
            return ""
        try:
            encrypted_bytes = self.cipher_suite.encrypt(data.encode())
            return encrypted_bytes.decode()
        except Exception as e:
            logger.error(f"Encryption failed: {e}")
            return ""

    def decrypt_data(self, encrypted_data: str) -> str:
        """Decrypts AES-256 (Fernet) encrypted string data."""
        if not encrypted_data:
            return ""
        try:
            decrypted_bytes = self.cipher_suite.decrypt(encrypted_data.encode())
            return decrypted_bytes.decode()
        except Exception as e:
            logger.error(f"Decryption failed: {e}")
            return ""

    async def get_password_hash(self, password: str) -> str:
        return await asyncio.to_thread(self.pwd_context.hash, password)

    async def verify_password(self, plain_password: str, hashed_password: str) -> bool:
        return await asyncio.to_thread(self.pwd_context.verify, plain_password, hashed_password)
