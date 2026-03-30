> **⚠️ Known Issue:** Claude's refresh token is consistently failing to renew. Investigating workarounds.

# CodexBar for Android

> Android port of [**CodexBar**](https://github.com/steipete/CodexBar) by [@steipete](https://github.com/steipete) — the macOS menu bar app for monitoring AI service quotas.

Monitor your AI service quotas from your Android device. Track remaining usage for Claude, Codex (ChatGPT), and Gemini in one place.

<p align="center">
  <img src="docs/Screenshot_20260305_025201_CodexBar.jpg" width="320" alt="Dashboard" />
  &nbsp;&nbsp;
  <img src="docs/Screenshot_20260305_025207_CodexBar.jpg" width="320" alt="Settings" />
</p>

## Features

- Real-time quota monitoring for Claude, Codex, and Gemini (more services coming soon)
- Animated gauge bars showing remaining usage percentage
- Quick Settings tile for at-a-glance status
- Background refresh with configurable intervals
- Persistent notification with per-service breakdown
- Push alert when quota resets (fully replenished)
- Encrypted credential storage
- Material 3 with Dynamic Color

## Download

Pre-built APKs are available on the [Releases](https://github.com/hyunnnchoi/codexbar-android/releases) page. 

I do **system software** for a living — I'm not smart enough to sneak viruses into an Android app. 

No backend server — all tokens are processed and stored strictly on-device. 


## Setup

1. Install [OpenJDK 17](https://formulae.brew.sh/formula/openjdk@17) (or any JDK 17+)
2. Clone and open in Android Studio
3. Build and install the debug APK
4. Open the app and go to **Settings** to enter your API tokens

## Getting Your Tokens

### Claude (Anthropic)

Claude uses OAuth tokens from Claude Code CLI. Extract both tokens from macOS Keychain:

```bash
security find-generic-password -s "Claude Code-credentials" -w \
  | python3 -c "
import sys, json
d = json.loads(sys.stdin.read())['claudeAiOauth']
print('Access Token:', d['accessToken'])
print('Refresh Token:', d['refreshToken'])
"
```

Paste **both** tokens into the Claude fields in Settings. The refresh token is required — access tokens expire every 8 hours, and the app uses the refresh token to renew them automatically in the background.

### Codex (OpenAI / ChatGPT)

If you have the [Codex CLI](https://github.com/openai/codex) installed and logged in, extract tokens from `~/.codex/auth.json`:

```bash
# Access token
cat ~/.codex/auth.json | python3 -c "import sys,json; print(json.loads(sys.stdin.read())['tokens']['access_token'])"

# Refresh token
cat ~/.codex/auth.json | python3 -c "import sys,json; print(json.loads(sys.stdin.read())['tokens']['refresh_token'])"
```

Paste both into the Codex fields in Settings.

<details>
<summary>Alternative: Extract from browser (if CLI is not installed)</summary>

1. Open [chatgpt.com](https://chatgpt.com) in your browser
2. Open DevTools (F12) > Network tab
3. Look for requests to `https://chatgpt.com/backend-api/`
4. Copy the `Authorization: Bearer ...` token from request headers

</details>

### Gemini (Google)

Gemini requires **4 values**: access token, refresh token, OAuth client ID, and OAuth client secret. The client ID/secret are needed because Gemini uses Google OAuth for token refresh.

If you have the [Gemini CLI](https://github.com/google-gemini/gemini-cli) installed and logged in:

```bash
# 1. Access token
python3 -c "import json; print(json.load(open('$HOME/.gemini/oauth_creds.json'))['access_token'])"

# 2. Refresh token
python3 -c "import json; print(json.load(open('$HOME/.gemini/oauth_creds.json'))['refresh_token'])"

# 3. OAuth Client ID & Secret (from Gemini CLI source)
oauth_js="$(dirname "$(which gemini)")/../lib/node_modules/@google/gemini-cli/node_modules/@google/gemini-cli-core/dist/src/code_assist/oauth2.js"
python3 -c "
import re, sys
text = open('$oauth_js').read()
cid = re.search(r\"OAUTH_CLIENT_ID\s*=\s*'([^']+)'\", text)
sec = re.search(r\"OAUTH_CLIENT_SECRET\s*=\s*'([^']+)'\", text)
print('Client ID:', cid.group(1) if cid else 'not found')
print('Client Secret:', sec.group(1) if sec else 'not found')
"
```

Paste all four values into the Gemini fields in Settings.

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Tech Stack

- Kotlin 2.1.0, Jetpack Compose, Material 3
- Hilt (DI), Retrofit2 + OkHttp (networking)
- WorkManager (background sync), EncryptedSharedPreferences (security)
- KSP, kotlinx.serialization

## Acknowledgments

Based on [CodexBar](https://github.com/steipete/CodexBar) by Peter Steinberger.

## License

[MIT](LICENSE)
