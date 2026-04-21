---
trigger: always_on
---

# 🌊 Nemo Project Rules & AI Behavior Protocol
> **Active Supabase Project**: `https://fzzkxymwcambugbxfsvj.supabase.co`

This document serves as the **Supreme Protocol** for the joint development of Nemo (Android) and Nemo_web (Next.js). All AI-assisted development must strictly adhere to these rules.

## 1. Core Vision: Seamless Learning Environment
*   **Zero-Friction Sync**: Learning progress must sync in real-time across Android and Web. The legacy "Manual Sync" button is **strictly forbidden**.
*   **Android Remodeling**: Since the legacy Android project is deprecated and backward compatibility is not required, the goal is to **completely refactor the data layer** to be a "native mirror" of the Web project's logic.

## 2. AI Behavior Redlines
*   **🚫 Strictly No UI Changes**: Modifying Jetpack Compose layouts or Web CSS/React components is forbidden. AI work is limited to bottom-level logic, data flow, and algorithm parity.
*   **🚫 No Guessing**: If SRS parameters or sync conflict rules are ambiguous, **you must ask the user for confirmation**. Guessing-based programming is prohibited.
*   **✅ Web as Source of Truth**: Whenever a logic discrepancy exists between Android (Kotlin) and Web (TS), the **Web implementation prevails**. Do not extend backend schemas independently.

## 3. Data & Sync Protocol ❗
The following technical requirements are mandatory for maintaining "Seamless Sync":

### A. Strict Schema Alignment (Table Schema)
*   **Unified Table**: All user progress must be stored in the Supabase `user_progress` table (unifying word and grammar progress).
*   **Primary Key**: Mandatory use of UUID as the primary key (mapping to `id: string` in Web). Android's local Room DB must sync this UUID.
*   **Field Parity**: Must include `lapses`, `state`, `item_type`, `item_id`, `learning_step`, and other core fields from the Web schema.

### B. Logic Authority (Logic Sharing via RPC)
*   **Offloaded Calculation**: Clients (Android/Web) are **forbidden** from independently calculating stability, difficulty, or intervals.
*   **Mandatory RPC**: All review operations must invoke the Supabase RPC `fn_process_review_atomic`. Clients only send the "Rating"; the server-side function handles all SRS math. This is the primary method for "Logic Sharing."
*   **Offline Compensation**: Local mirroring of the algorithm is allowed for previewing while offline, but the RPC response is the final authority once reconnected.

### C. Data Messenger (Realtime/WebSocket)
*   **Silent Updates**: Android must implement `Supabase Realtime` to listen for changes on the `user_progress` table.
*   **Reactive UI**: Use reactive programming (e.g., Kotlin Flow / React Hooks). When WebSocket pushes a remote change, the local DB must update silently and trigger a UI refresh automatically.

### D. Algorithm Precision (FSRS Parity)
*   **Data Types**: FSRS calculations must use **Double** (Kotlin) to match Web's `number` (JS). **Float is strictly forbidden**.
*   **Fuzz Alignment**: Must implement a 1:1 port of Web's `constrainedFuzzBounds` logic. Legacy Android step-based random fuzzing is prohibited.

## 4. Auth & Security
*   **Unified Auth**: Both platforms must use Supabase Email/Password authentication.
*   **Data Isolation**: All queries must include `user_id` or rely on Supabase RLS (Row Level Security).

## 5. AI Mandatory Self-Check
> AI, before submitting any code, ask yourself:
> 1. Does the schema match the Web version exactly?
> 2. Is the FSRS fuzzing algorithm identical to the Web version?
> 3. Am I using Double precision for stability calculations?

---
**Last Updated**: 2026-04-21
**Status**: Active / Mandatory / Production Ready
