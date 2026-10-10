# DarkGPT Android (MVP)

Native Android starter app for Pixel/Android. It builds a debug APK in GitHub Actions; no PC is required if you use GitHub in your phone browser.

## What it does
- Native portrait Android app named DarkGPT
- Chat UI and settings for an OpenAI-compatible API
- Stores API URL, model, and key in the app's local preferences
- Supports generating code and explaining errors through the selected model

## Important
- This source ZIP is not itself an APK.
- You must configure an API Base URL, API key, and model from a provider compatible with `/chat/completions`.
- API usage may cost money. Check the provider's pricing first.
- This MVP stores the key in local app preferences and is intended for personal testing. Do not share an APK configured with your API key.
- Do not use it for credential theft, malware, anti-cheat evasion, or unauthorized cheating in online games.

## Build APK using GitHub on Android
1. Create a GitHub account or sign in in your phone browser.
2. Create a new repository named `DarkGPT-Android`.
3. Upload the contents of this ZIP (upload files/folders; keep `.github/workflows/build-apk.yml` in its exact path).
4. Open the repository's **Actions** tab and enable Actions if prompted.
5. Choose **Build DarkGPT APK** and tap **Run workflow**. If you don't see it, push/commit the uploaded files to the `main` branch first.
6. When the run succeeds, open the run, scroll to **Artifacts**, and download `DarkGPT-debug-apk`.
7. Extract the downloaded artifact ZIP and install `app-debug.apk`. Android may ask permission to install unknown apps for the browser/files app you used.

## API settings
- API Base URL: provider's documented base URL, usually ending in `/v1`
- API Key: from the provider
- Model: exact model name supported by the provider
- Do not paste an API key into a public GitHub repository or source file.
