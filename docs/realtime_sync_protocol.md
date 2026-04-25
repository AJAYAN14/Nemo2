# 🌊 Nemo Project Realtime Sync Protocol (Nemo 项目实时同步技术指南)

> **Goal**: Ensure seamless data synchronization between Android and Web platforms using Supabase Realtime and persistent local storage.

## 1. Core Architecture (核心架构)

We follow the **"Cloud as Source of Truth"** principle. Local databases (Room) and DataStore serve as caches.

### Data Flow Logic:
1. **Active Sync (Local -> Cloud)**: 
   - User modifies data locally.
   - Update local storage and set `lastSettingsModifiedTime` to `System.currentTimeMillis()`.
   - Trigger asynchronous `push` to Supabase via `Postgrest`.
2. **Passive Sync (Cloud -> Local)**:
   - Remote client (Web/Other) updates data in Supabase.
   - Supabase emits a WebSocket event.
   - Android `Realtime` listener captures the event.
   - `SupabaseSyncManager` triggers a `pull` and updates local storage if the remote timestamp is newer.

---

## 2. Database Prerequisites (数据库前置要求)

For a table to support Realtime Sync, it **MUST** meet these requirements:

1. **Enable Realtime Publication**:
   By default, tables are not broadcasted. You must add them to the publication:
   ```sql
   ALTER PUBLICATION supabase_realtime ADD TABLE your_table_name;
   ```
2. **Primary Key Requirement**:
   The table must have a Primary Key (e.g., `user_id` or `id`) for Realtime to track `UPDATE` and `DELETE` actions.
3. **RLS (Row Level Security)**:
   RLS must be enabled with proper `SELECT` policies for the authenticated user, or the Realtime signal will be blocked.

---

## 3. Implementation Guidelines (开发规范)

### A. Model Definition (模型定义)
Always use `@SerialName` and provide **default values** for all fields. This prevents synchronization from breaking when one platform adds a field that the other hasn't implemented yet.

```kotlin
@Serializable
data class SyncModel(
    @SerialName("web_field_name") val androidFieldName: Type = defaultValue,
    @SerialName("lastSettingsModifiedTime") val lastSettingsModifiedTime: Long = 0L
)
```

### B. Realtime Subscription (实时订阅)
In `SupabaseSyncManager`, create a separate `postgresChangeFlow` for each synchronized table:

```kotlin
val changeFlow = realtimeChannel!!.postgresChangeFlow<PostgresAction>(schema = "public") {
    table = "your_table_name"
    filter(FilterOperation("user_id", FilterOperator.EQ, userId))
}

launch {
    changeFlow.collect { action ->
        Log.d(TAG, "Realtime change received: $action")
        syncYourFeature(userId) // Trigger sync logic
    }
}
```

### C. Conflict Resolution (冲突解决)
Always use the **`lastSettingsModifiedTime`** timestamp included within the data payload, NOT the database's `updated_at`.
- If `local.timestamp > remote.timestamp` -> **Push**
- If `remote.timestamp > local.timestamp` -> **Pull/Apply**

---

## 4. Common Pitfalls (避坑指南)

| Issue (问题) | Symptom (现象) | Solution (解决方案) |
| :--- | :--- | :--- |
| **Parsing Error** | Sync stops, Log shows `SerializationException`. | **MUST** provide default values for all model fields. |
| **Sync Loop** | Infinite pushing/pulling between devices. | Compare timestamps before writing; only write if data is strictly newer. |
| **Missing Signals** | Web updates but Android doesn't react. | Verify the table is added to `supabase_realtime` publication. |
| **Precision Loss** | Values like `0.95` becoming `0.9`. | Use `Double` (Kotlin) to match `Number` (JS). Avoid `Float`. |

---

## 5. Summary (总结)

To ensure sync reliability:
1. **Model**: Defensive parsing (default values).
2. **Sync**: Timestamp-based conflict resolution.
3. **Realtime**: Database publication + Client subscription.

*Document Version: 1.0.0*
*Last Updated: 2026-04-24*
