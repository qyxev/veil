# Veil Wiki: System Architecture & Design

Welcome to the official developer documentation for **Veil**, a low-latency, hybrid edge-cloud on-screen translation SaaS platform for Android.

---

## 1. System Architecture Overview

Veil uses a **Hybrid Edge + Cloud** architecture designed to minimize latency, reduce bandwidth overhead, and protect user privacy by processing heavy operations on-device while leveraging scalable cloud services for translation and rate-limiting.

```text
+-----------------------------------------------------------------+
|                         ANDROID CLIENT                          |
|  +-------------------+      +--------------------------------+  |
|  | WindowManager     | ---> | MediaProjection                |  |
|  | (Floating Bubble) |      | (Frame Capture)                |  |
|  +-------------------+      +--------------------------------+  |
|                                           |                     |
|                                           v                     |
|  +-------------------+      +--------------------------------+  |
|  | WindowManager     | <--- | Google ML Kit Text Recognition |  |
|  | (Overlay Renderer)|      | (On-Device OCR & Bounding Box) |  |
|  +-------------------+      +--------------------------------+  |
+-------------------------------------------|---------------------+
                                            | Compact JSON Payload (Texts + Rects)
                                            v (HTTPS + Firebase JWT)
+-----------------------------------------------------------------+
|                       GO API GATEWAY (`veil-api`)               |
|  +-----------------------------------------------------------+  |
|  | Firebase Auth Middleware (Token Verification & Claims)    |  |
|  +-----------------------------------------------------------+  |
|                               |                                 |
|                               v                                 |
|  +-----------------------------------------------------------+  |
|  | Redis Layer (Sliding-Window Rate Limiting & Hash Caching) |  |
|  +-----------------------------------------------------------+  |
|                               |                                 |
|                               v                                 |
|  +-----------------------------------------------------------+  |
|  | Translation Engine (External API / DeepL / LLM Routing)   |  |
|  +-----------------------------------------------------------+  |
+-----------------------------------------------------------------+