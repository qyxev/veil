<div align="center">

# Veil

**Zero-Latency On-Screen Translation Overlay for Android**  
*Read anything on your screen—games, manga, social feeds, and apps—without switching contexts.*

<br>

![Kotlin](https://img.shields.io/badge/Kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Go](https://img.shields.io/badge/Go-%2300ADD8.svg?style=for-the-badge&logo=go&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-%23DD0031.svg?style=for-the-badge&logo=redis&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-%23039BE5.svg?style=for-the-badge&logo=firebase&logoColor=white)

</div>

---

### The Problem It Solves

Translating text inside mobile games, unlocalized apps, images, or locked UI elements usually forces users into a clunky workflow: **take a screenshot → exit the app → open a translator → upload the image → switch back**. 

**Veil** eliminates that friction. It drapes a lightweight, translucent glassmorphic layer over any active Android app, capturing on-screen text and rendering translated overlays directly over the original text's exact coordinates in milliseconds.

---

### Architecture & How It Works

Veil uses a **Hybrid Edge + Cloud** pipeline to keep latency near zero while minimizing bandwidth and server overhead:

1. **Floating Trigger & Capture (`Kotlin` / `MediaProjection`):** A persistent floating bubble sits unobtrusively over active apps. Tapping it captures a single clean frame from the device display.
2. **On-Device Edge OCR (`Google ML Kit`):** Raw text and screen bounding coordinates (`Rect`) are extracted locally on the phone's NPU/GPU—avoiding slow, heavy screenshot uploads over mobile networks.
3. **API Gateway & Rate Limiting (`Go` + `Redis`):** Only compact JSON text payloads are sent to the Go backend. Redis handles sliding-window rate limiting and caches previously translated phrases for instant repeat lookups.
4. **Auth & Persistence (`Firebase`):** Firebase Authentication secures API requests via JWT verification, while Firestore manages user preferences and language profiles.
5. **Coordinate-Matched Overlay (`WindowManager`):** Translated text blocks are drawn back onto the screen inside high-contrast glassmorphic cards aligned to the original bounding boxes.

---

### Tech Stack

| Layer | Technology | Role |
| :--- | :--- | :--- |
| **Mobile Client** | **Kotlin (Native Android)** | `WindowManager` floating overlay, `MediaProjection` capture, and local ML Kit OCR. |
| **Backend & Gateway** | **Go (Golang)** | High-concurrency REST API gateway, JWT middleware, and translation orchestration. |
| **Caching & Quotas** | **Redis** | Sub-millisecond translation caching and per-user rate limiting. |
| **Auth & Database** | **Firebase** | User authentication, token verification, and cloud settings sync. |

---

### Repository Structure

```text
veil/
├── veil-android/      # Native Kotlin Android app, overlay service & ML Kit OCR
└── veil-api/          # Go API gateway, Redis cache/rate-limiter & Firebase auth middleware
