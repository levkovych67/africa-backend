#!/usr/bin/env python3
"""
Lightweight GitHub webhook receiver for auto-deploy.
Listens on port 9000, triggers deploy on push to main.

Setup on VPS:
  1. Copy to /opt/africa/webhook-server.py
  2. pip3 install flask
  3. Create systemd service (see deploy/webhook.service)
  4. Add webhook in GitHub repo settings:
     - URL: http://YOUR_VPS_IP:9000/deploy
     - Content type: application/json
     - Secret: (set WEBHOOK_SECRET env var)
     - Events: Just the push event
"""

import hashlib
import hmac
import os
import subprocess
import threading
from flask import Flask, request, jsonify

app = Flask(__name__)
SECRET = os.environ.get("WEBHOOK_SECRET", "")
DEPLOY_LOCK = threading.Lock()


def verify_signature(payload, signature):
    if not SECRET:
        return True  # No secret configured — skip verification
    expected = "sha256=" + hmac.new(
        SECRET.encode(), payload, hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(expected, signature or "")


def deploy_backend():
    subprocess.run(
        ["bash", "-c", """
            cd /opt/africa/africa-backend
            git fetch origin main
            git reset --hard origin/main
            chmod +x gradlew
            ./gradlew clean :app:bootJar
            sudo systemctl restart africa-backend
        """],
        capture_output=True, text=True, timeout=600
    )


def deploy_frontend():
    subprocess.run(
        ["bash", "-c", """
            cd /opt/africa/africa-frontend
            git fetch origin main
            git reset --hard origin/main
            npm ci
            NEXT_PUBLIC_API_URL=http://$(hostname -I | awk '{print $1}') npm run build
            rm -rf .next/standalone/.next/static
            cp -r .next/static .next/standalone/.next/
            rm -rf .next/standalone/public
            cp -r public .next/standalone/
            sudo systemctl restart africa-frontend
        """],
        capture_output=True, text=True, timeout=600
    )


@app.route("/deploy", methods=["POST"])
def handle_webhook():
    payload = request.get_data()
    signature = request.headers.get("X-Hub-Signature-256")

    if SECRET and not verify_signature(payload, signature):
        return jsonify({"error": "Invalid signature"}), 403

    data = request.get_json(silent=True) or {}
    ref = data.get("ref", "")
    repo_name = data.get("repository", {}).get("name", "")

    if ref != "refs/heads/main":
        return jsonify({"status": "skipped", "reason": "not main branch"})

    if not DEPLOY_LOCK.acquire(blocking=False):
        return jsonify({"status": "skipped", "reason": "deploy already running"})

    def run_deploy():
        try:
            if repo_name == "africa-backend":
                deploy_backend()
            elif repo_name == "africa-frontend":
                deploy_frontend()
            else:
                deploy_backend()
                deploy_frontend()
        finally:
            DEPLOY_LOCK.release()

    thread = threading.Thread(target=run_deploy)
    thread.start()

    return jsonify({"status": "deploying", "repo": repo_name})


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=9000)
