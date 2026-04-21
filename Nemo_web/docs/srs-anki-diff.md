# SRS / 调度 与 Anki 不一致点与影响（大白话）

## 本次已落地对齐（2026-04-20）

这次我先做了“最小但关键”的一块对齐：队列选择顺序。

- 已把 Web 的选题顺序改为：`intraday_now（可立即学习） -> main（主队列到期项） -> intraday_ahead（学习卡提前窗口）`。
- 含义变化：
  - 学习/重学卡（state=1/3）只要已到期，会优先于普通主队列卡片。
  - 主队列有到期项时，会先做主队列，不会被“提前学习卡”抢走顺序。
  - 非学习卡（比如普通复习卡）即使在 `learnAhead` 时间窗内，也不会被提前放出来，默认继续等待。
  - 只有在用户手动“继续学习”（manual override）窗口内，才允许突破等待直接继续当前等待项。

- 这部分改动对应文件：
  - `src/lib/services/queueManager.ts`
  - `src/lib/services/queueManager.test.ts`

- 已通过测试：
  - `npm run test -- src/lib/services/queueManager.test.ts`
  - 结果：9/9 全通过。

## 第二阶段已落地对齐（2026-04-20）

这次继续补了“间隔计算节奏”与“参数来源”两块关键对齐：

- 学习/复学第一步的 Hard 延迟，改成 Anki 语义：
  - 若有下一步：`Hard = (Again 第一步 + Good 下一步) / 2`。
  - 若只有一步：`Hard = min(Again * 1.5, Again + 1天)`，并在超过 1 天时按整天取整。
- 默认参数更贴近 Anki 习惯：
  - `leechThreshold` 默认从 5 调整为 8。
  - `relearningSteps` 默认从 `[1, 10]` 调整为 `[10]`。
- 新增 FSRS `targetRetention` 运行时配置：
  - 配置项：`fsrsTargetRetention`（范围 0.7 ~ 0.99，默认 0.9）。
  - 在“真实评分写库”和“间隔预览”两条路径上都已接入。
  - 设置页“记忆算法配置”已支持直接修改该参数。
- 数据库原子过程语义补强：
  - `review_logs` 新增前后状态记录字段：`prev_state / prev_learning_step / prev_buried_until / next_state / next_learning_step / next_buried_until`。
  - `fn_process_review_atomic` 现在会把本次评分前后的关键状态一并写入日志，便于回放与问题追踪。

## 第三阶段（不考虑兼容）已落地（2026-04-20）

你要求“不考虑兼容问题”后，我把后端 RPC 改成了**破坏性新签名**：

- `fn_process_review_atomic` 不再使用旧的 `p_state / p_learning_step / p_buried_until` 这类“混合语义”参数。
- 新签名改为显式 prev/next 语义：
  - `p_prev_state / p_prev_learning_step / p_prev_buried_until`
  - `p_next_state / p_next_learning_step / p_next_buried_until`
  - 以及 `p_next_*` 的稳定度、难度、间隔、复习时间等字段。
- 函数内新增严格快照校验：若数据库当前值与 `p_prev_*` 快照不一致，直接抛 `STALE_DATA_CONFLICT`。
- 前端 `studyService.processReview` 已同步改为发送新参数，不再按旧签名调用。

- 这部分改动对应文件：
  - `supabase/migrations/20260420103000_process_review_atomic_breaking_signature.sql`
  - `src/lib/services/studyService.ts`

- 已通过测试：
  - `npm run test -- src/lib/services/studyService.buryItem.test.ts src/lib/services/srsService.test.ts src/lib/services/ratingProcessor.test.ts src/lib/services/queueManager.test.ts`
  - 结果：33/33 全通过。

- 这部分改动对应文件：
  - `src/lib/services/srsService.ts`
  - `src/lib/srs/fsrs.ts`
  - `src/lib/services/studyService.ts`
  - `src/lib/services/settingsService.ts`
  - `src/app/settings/page.tsx`
  - `src/types/study.ts`
  - `src/lib/services/srsService.test.ts`
  - `supabase/migrations/20260420101000_review_logs_state_semantics.sql`

- 已通过测试：
  - `npm run test -- src/lib/services/srsService.test.ts src/lib/services/ratingProcessor.test.ts src/lib/services/queueManager.test.ts`
  - 结果：25/25 全通过。

下面用最直白的语言说明你们 Web 项目（以下简称“Web”）和 Anki rslib 调度器（以下简称“Anki”）在 SRS、排队/状态、以及相关行为上的主要不同，以及每个不同会带来的实际影响和建议。

---

## 1. 状态模型差异

- Web：只用一个 `state` 字段表示卡片状态（例如 0 新卡，1 学习，2 复习，3 复习中），并用 `next_review` 单字段表示下次复习时间（加上一些 buried/suspended 字段）。
- Anki：用两个概念：`card type (ctype)` + `queue`（多队列：学习队列、当日内学习、主队列等），并有更多字段（interval、ease_factor、remaining_steps 等）。

影响（大白话）：
- Web 单字段更简单，但不能表达“同一张卡片同时处在不同队列语义下的细节”。比如 Anki 可以把卡片标记为“今天还可以再学（intraday）”或“主队列复习”，两者在调度优先级和 learn-ahead 行为上不同。Web 可能会把这些情况统一成“到期”或“等待”，导致：
  - 会话内混合优先级不一致（用户在一次复习会话里看到的顺序与 Anki 不同）。
  - 统计/回滚/原子更新在极端并发或重放场景下更难精确模拟 Anki 行为。

建议：如果想与 Anki 兼容，需在模型或服务层增加额外字段以表达“当前队列/子队列语义”，或在队列构建层用规则模拟多队列优先级。

---

## 2. 学习步骤（learning steps）与硬延迟（hard delay）处理

- Web：实现了学习步骤与重学逻辑，但与 Anki 的「第一步硬延迟（hard-delay-for-first-step）」及步长缩放策略不完全一致。
- Anki：对第一步常有特殊的硬延迟以防止极短间隔重复出现，并有细致的 step 调整规则。

影响（大白话）：
- 学习/重学时用户会经历不同的短期重复节奏。Web 的步长如果比 Anki 更短或没有硬延迟，会更快让卡片再次出现，导致用户感觉“被刷得太频繁”；反之，步长更长会降低短期巩固机会。

建议：确认学习步骤的具体毫秒/秒级延迟和第一步规则，必要时同步 Anki 的硬延迟实现或在参数上对齐。

---

## 3. Learn-ahead（提前学习）与队列合并逻辑

- Web：在 `selectNextQueueItem` 中使用 `learnAheadMs` 和 WAIT / NEXT 的语义来决定何时从等待变为可取出。
- Anki：在队列构建里明确区分 `intraday_now`（可立即做的学习项）和 `intraday_ahead`（尚不可见的提前学习项），并在迭代顺序上有严格优先级：intraday_now -> main -> intraday_ahead。 

影响（大白话）：
- 在会话切换、暂停、跨设备同步时，Web 的选择逻辑可能导致“提前学习”的项被过早或过晚展示，影响用户当日应出现的题量分布与体验。尤其在复习混合（新卡/复习卡/学习卡夹杂）时，顺序会与 Anki 有可感知差异。

建议：调整 `selectNext` 的排序规则或在服务端模拟 Anki 的队列迭代顺序以匹配优先级。

---

## 4. 重学（relearning）与复习（review）转移语义

- Web：用 `state` 和 `learning_step` 来驱动重学/复习，但没有完全采用 Anki 使用 `queue`/`ctype` 去区分“进入重学流程时的队列变化”。
- Anki：重学时会修改卡片的 queue/ctype，使得它暂时走入学习队列或特定的重学路径。

影响（大白话）：
- 重学后的下一次调度时间、统计（lapses/reps）以及是否被计入当日学习配额，可能和 Anki 的计算不同，导致用户复习记录和未来间隔不一致。

建议：在处理评分时，把重学路径的队列变更明确化，保证 `next_review`、`reps`、`lapses` 与 Anki 相同的更新逻辑。

---

## 5. FSRS 参数与目标保留率（target retention）的来源

- Web：实现了 FSRS，默认 targetRetention=0.9，但参数传递、牌组/用户级覆盖和配置读取可能不如 Anki 的 deck config 丰富。
- Anki：有 deck config 可以为不同牌组设置不同的 desired_retention、衰减参数等。

影响（大白话）：
- 如果 deck/collection 层级参数不同步，Web 上同一套牌在不同用户或不同环境下可能计算出不同的间隔（更难复现 Anki 的间隔结果）。

建议：把 FSRS 的参数暴露到牌组配置，并确保在计算时优先读取牌组/用户自定义参数。

---

## 6. Leech / 失败次数处理差异（停用 vs 埋入）

- Web：在 `ratingProcessor` 中对 leech 或跳过做了 `state = -1`（suspend）或把 `buried_until` 设为当天结束（bury_today）的处理。
- Anki：有更细的 leech 定义（比如达到阈值会设置为 suspended 或改为标记为 leech），并在界面和统计上有专门处理。

影响（大白话）：
- 用户被标记为 leech 的处理体验不同（比如在 Anki 里可能自动停用并提醒复习策略，而在 Web 里可能只是临时“埋入”或永久停用），这会影响教师/用户查看问题卡、导出/排查问题卡的便利性。

建议：统一 leech 判定阈值与处理动作（停用 vs 埋入），并在 UI 上加入对应提示与导出/处理工具。

---

## 7. 原子性与幂等（数据库层）

- Web：已经用了 `fn_process_review_atomic` 这类 SQL 存储过程做原子更新，并用 `request_id` 做幂等保护。
- Anki：本地数据结构更新由客户端负责，设计上无单独 SQL 原子过程的语义，但行为上保证一次评分只影响一次状态。

影响（大白话）：
- Web 的实现是正确的（原子且可回滚），但当想精确重现 Anki 的行为（特别是队列/ctype 变更）时，需要确保 SQL 存储过程更新的字段集足够表达 Anki 的那些额外语义（例如：额外的队列标志或临时队列）。否则即使原子，也会把数据写成“缺少语义”的状态，导致后续重建队列时偏差。

建议：若要兼容 Anki 行为，扩展数据库更新列（或额外表）以记录更多队列/标志信息，并在 `fn_process_review_atomic` 中一并处理。

---

## 8. 会话撤销（Undo）与回滚语义

- Web：有 undo 快照和后端 `undoReview` 接口。
- 问题点：如果实现的状态模型不能表达 Anki 的队列切换细节，撤销后状态恢复可能与原先的 Anki 状态不同（例如丢失临时队列标签）。

影响（大白话）：
- 用户撤销一次评分后，复原的不仅是 `next_review`，还有可能丢失是否属于“今日学习队列”的语义，造成下一次复习顺序不同。

建议：撤销要回滚全部必要字段（包括新增的队列/标签），并在测试里覆盖复杂情形（重学→撤销→再次评分）。

---

## 9. 统计与指标（reps/lapses/elapsed_days）差异

- Web：在存储过程中更新 `reps`、`lapses`、`elapsed_days` 等字段。
- 但如果状态变更语义不同，统计上会出现偏差（比如一次重学在 Anki 记为 lapse，但在 Web 可能被当作新一次 reps）。

影响（大白话）：
- 报表与学习历史会和 Anki 裁定的“学习质量/难度”不同，影响复盘与导出数据的可比性。

建议：对评分路径与统计更新做好一一映射测试，确保每种评分分支都会更新正确的统计字段。

---

## 10. 用户可感知的体验差异总结（最直白）

- 同一套牌在 Web 上和在 Anki 上做同样的操作（同样的评分序列），最终的下次复习时间、当日出现顺序、以及是否被当作 leech 的判定，很可能会不一致。
- 结果是：用户可能觉得“Web 上的复习更频繁/更稀疏”或“撤销后恢复得不一样”，并且导出的学习统计也不好对齐。

---

## 11. 优先级建议（要做什么先）

1. 把 Web 的队列优先级（intraday_now -> main -> intraday_ahead）在 `selectNext` 层模拟出来，验证会话内顺序是否与 Anki 更接近。
2. 扩展后端的原子更新字段，能记录必要的队列/ctype 语义（或在另一个列里模拟），并修改 `fn_process_review_atomic` 同步写入。
3. 对齐学习步长与第一步硬延迟策略，保证短期重复节奏一致。
4. 统一 leech 判定与处理动作，并在 UI 给出明确提示。
5. 增加针对“重学→撤销→再评分”的集成测试，保证回滚语义完整。

---

## 12. 需要我接着做的事（选一项回复我即可）

- 我把差异点逐行附上精确代码/行号证据（方便发 PR）。
- 我直接实现第 1 步（在 `selectNextQueueItem` 模拟 Anki 队列顺序并写测试）。
- 我修改数据库存储过程以存更多队列语义并写迁移草案。

---

保存位置：`e:\Web\Nemo_web\docs\srs-anki-diff.md`。
