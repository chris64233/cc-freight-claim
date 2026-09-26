# cc-freight-claim

跨多个承运段的货运损失索赔、责任认定与结算服务。

覆盖运输单与按序承运段、交接证据、损失索赔、责任决定（按承运段比例分摊）、
结算以及结算后追偿/冲回调整的完整业务闭环。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1（Spring Data JPA + H2，Jackson 3）

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 领域模型

| 实体 | 说明 |
| --- | --- |
| `Shipment` | 运输单，维护交接证据版本 `evidenceVersion` |
| `CarrierSegment` | 承运段，按 `seq` 顺序归属运输单 |
| `HandoverEvidence` | 交接证据，按到达顺序编号，追加即递增证据版本 |
| `Claim` | 损失索赔（外部索赔号、损失事件、损失类型、损失金额、内容版本） |
| `ClaimEvidence` | 索赔证据 |
| `LiabilityDecision` | 责任决定（草稿/已确认），记录依据的两个版本 |
| `DecisionAllocationLine` | 草稿分摊方案（承运段 + 权重） |
| `LiabilityEntry` | 确认时一次性生成的正式责任分录 |
| `Settlement` / `SettlementLine` | 结算单及其分录行 |
| `AdjustmentRecord` | 结算后追加的追偿/冲回台账记录 |

## 主要业务规则

1. **运输单与索赔**
   - 运输单记录按顺序发生的承运段和交接证据；交接证据按到达顺序编号。
   - 索赔包含外部索赔号、损失金额、损失类型和证据。
2. **活动索赔唯一**
   - 同一运输单 + 同一损失事件只能存在一笔活动索赔（`ACTIVE`）。
   - 应用层先校验，数据库唯一锁键列 `active_lock_key`（活动时为业务键，关闭后为 NULL）
     在数据库层兜底，并发创建也不会出现两笔活动索赔。
3. **外部索赔号幂等**
   - 重复提交相同外部索赔号直接返回既有索赔，不重复创建、不覆盖内容。
4. **责任决定与比例分摊**
   - 认可金额不得超过索赔损失金额。
   - 认可金额可按承运段权重比例分摊给多个承运段；使用最大余额法分摊，
     保证每个分录为 2 位小数且**各分录之和严格等于认可金额**，没有舍入尾差。
   - 确认决定时在同一事务内**一次性生成全部责任分录**；草稿阶段只有方案行，不产生正式分录。
   - 每笔索赔至多一笔已确认决定。确认后决定不可修改、不可重复确认。
5. **版本冲突控制**
   - 责任决定记录其依据的「索赔内容版本」与「运输单交接证据版本」。
   - 新交接证据到达（证据版本递增）或索赔内容/证据变化（内容版本递增）后，
     基于旧版本的决定确认时返回 `409 Conflict`；重新拟定草稿刷新依据版本后方可确认。
   - 确认流程对索赔行、运输单行、决定行加悲观锁，并以 `confirmed_lock_key`
     唯一约束兜底：**并发责任决定只有一方成功，绝不产生两套分录**。
6. **结算**
   - 仅已确认决定可结算，支持分次结算；每个承运段累计已结算不得超过其认可分摊金额。
   - 结算后责任决定仍不可修改。
7. **结算后调整（只能追加）**
   - 只能追加 `RECOVERY`（追偿）或 `REVERSAL`（冲回）台账记录，不能改单。
   - 追偿累计不得超过该承运段认可分摊金额。
   - **冲回累计不得超过对应已结算责任**（已结算 − 已冲回）。
8. **金额规范**
   - 全部金额使用 `BigDecimal`，统一保留 2 位小数，统一 `RoundingMode.HALF_UP`
     （见 `Money`）；比例分摊采用最大余额法消除尾差。

## HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/shipments` | 创建运输单（可带按序承运段） |
| GET | `/api/shipments/{shipmentNo}` | 查询运输单（含承运段） |
| GET | `/api/shipments/{shipmentNo}/segments` | 承运段台账 |
| POST/GET | `/api/shipments/{shipmentNo}/handover-evidences` | 追加/查询交接证据 |
| POST | `/api/claims` | 创建索赔（外部索赔号幂等） |
| GET | `/api/claims/{id}`、`/api/claims/by-external/{no}` | 查询索赔 |
| GET | `/api/claims?shipmentNo=` | 按运输单查询索赔 |
| PUT | `/api/claims/{id}` | 修改索赔内容（内容版本 +1） |
| POST | `/api/claims/{id}/close` | 关闭索赔 |
| POST/GET | `/api/claims/{id}/evidences` | **索赔证据台账** |
| POST | `/api/decisions/for-claim/{claimId}` | 创建责任决定草稿 |
| PUT | `/api/decisions/{id}` | 修改草稿（刷新依据版本） |
| POST | `/api/decisions/{id}/confirm` | 确认决定，一次性生成分录 |
| GET | `/api/decisions/{id}` | 决定详情（草稿方案/已确认分录） |
| GET | `/api/decisions/for-claim/{claimId}` | **责任分摊台账** |
| POST | `/api/settlements/for-decision/{decisionId}` | 结算（可分次） |
| GET | `/api/settlements/{settlementNo}`、`/for-decision/{id}` | 结算单查询 |
| GET | `/api/settlements/ledger?claimId=` | **结算台账（按索赔）** |
| POST | `/api/adjustments/for-decision/{decisionId}` | 追加追偿/冲回 |
| GET | `/api/adjustments/for-entry/{liabilityEntryId}` | 按分录查调整 |
| GET | `/api/adjustments/ledger?claimId=` | **调整台账（按索赔）** |

错误码：参数/业务规则失败 `400`，资源不存在 `404`，版本过期、重复活动索赔、
并发确认等冲突 `409`。

## 自动化测试

- `MoneyTest`：统一舍入、最大余额法分摊精确性（100/3、0.10/7 等）。
- `ClaimWorkflowServiceTest`：完整业务闭环、外部号幂等、活动索赔唯一、
  认可金额上限、新交接证据/索赔内容变更导致旧版本决定冲突、
  **并发确认只产生一套分录**、结算与追偿/冲回限额。
- `FreightClaimApiIntegrationTest`：端到端 HTTP 流程与 400/404/409 状态码。
