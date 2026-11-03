"""Read-only checks of the deployed API and its anonymous access boundary."""
import json
import base64
from pathlib import Path
import time
import urllib.error
import urllib.request

root = Path(__file__).resolve().parents[2]
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")


def config(name):
    line = next(line for line in gradle.splitlines() if f'"{name}"' in line)
    return line.split('\\"')[1]


url = config("SUPABASE_URL")
key = config("SUPABASE_PUBLISHABLE_KEY")


def check(label, path, expected, body=None, bearer=None):
    headers = {"apikey": key, "Content-Type": "application/json"}
    if bearer:
        headers["Authorization"] = f"Bearer {bearer}"
    request = urllib.request.Request(url + path, headers=headers,
                                    data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(request, timeout=40) as response:
            status, data = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, data = error.code, error.read()
    assert status in expected, f"{label}: expected {expected}, got {status}: {data[:300]!r}"
    print(f"PASS {label} ({status})")
    return json.loads(data) if data else None


endpoint = "/functions/v1/bbco-data"
result = check("Exact pre-auth username lookup", endpoint, {200},
               {"action": "read", "request": {"collection": "users", "filters": {"username": "__migration_smoke_test__"}, "limit": 1}})
assert result == {"documents": []}
check("Anonymous collection browsing is denied", endpoint, {403}, {"action": "read", "request": {"collection": "users"}})
check("Anonymous writes are denied", endpoint, {401}, {"action": "commit", "request": {"writes": []}})
check("Anonymous account access is denied", endpoint, {401}, {"action": "access", "request": {}})
check("Forged Firebase tokens are denied", endpoint, {401}, {"action": "read", "request": {"collection": "users"}}, "forged.token.signature")

# Exercise the deployed function's actual Google JWKS fetch and RSA verification,
# without using a real user's credentials or creating any account.
with urllib.request.urlopen("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com", timeout=20) as response:
    public_jwk = json.load(response)["keys"][0]


def base64url(value):
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode()


now = int(time.time())
header = base64url(json.dumps({"alg": "RS256", "kid": public_jwk["kid"]}).encode())
claims = base64url(json.dumps({"iss": "https://securetoken.google.com/bbco-3c6ca", "aud": "bbco-3c6ca",
                              "sub": "migration-test-forged-identity", "email": "test@migration.invalid",
                              "iat": now, "exp": now + 3600}).encode())
forged_jwt = f"{header}.{claims}.{base64url(bytes(256))}"
result = check("A real Google key ID cannot bypass signature verification", endpoint, {401},
               {"action": "read", "request": {"collection": "users"}}, forged_jwt)
assert result["error"] == "Invalid sign-in signature.", result
check("Direct table access is denied", "/rest/v1/bbco_documents?select=path&limit=1", {401, 403})
check("Direct RPC actor forgery is denied", "/rest/v1/rpc/bbco_read", {401, 403},
      {"p_actor": {"uid": "forged", "email": "superadmin@gmail.com"}, "p_request": {"collection": "users"}})
check("Direct account-access actor forgery is denied", "/rest/v1/rpc/bbco_access", {401, 403},
      {"p_actor": {"uid": "forged", "email": "superadmin@gmail.com"}, "p_request": {}})
check("Direct private chat view access is denied", "/rest/v1/chat_messages?select=id&limit=1", {401, 403})
