# cc-freight-claim

跨多个承运段的货运损失索赔、责任认定与结算服务。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1（WebMVC + Data JPA + Validation）
- H2 内存数据库（可替换为其他关系库）

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 领域模型

| 实体 | 说明 |
| --- | --- |
| `Shipment` 运输单 | 聚合根，持有按顺序发生的承运段与交接证据；维护交接证据版本 `evidenceVersion` |
| `CarrierSegment` 承运段 | 运输单上按 `sequenceNo` 排序的一段运输，由某承运商承担 |
| `HandoverEvidence` 交接证据 | 段间交接凭据；每追加一条，运输单证据版本 +1 |
| `Claim` 索赔 | 外部索赔号、损失金额、损失类型、证据；维护索赔内容版本 `contentVersion` |
| `LiabilityDecision` 责任决定 | 把认可金额按比例分摊到多个承运段；草拟 → 确认 |
| `LiabilityEntry` 责任分录 | 单个承运段的分摊占比与金额，确认时一次性生成 |
| `Settlement` 结算 | 决定确认后一次性结算，结算后决定冻结 |
| `Adjustment` 调整 | 结算后追加的追偿（RECOVERY）或冲回（REVERSAL）台账记录 |

## 主要业务规则

### 1. 运输单、承运段与交接证据

- 承运段只能按顺序追加，段序号自动递增。
- 每追加一条交接证据，运输单 `evidenceVersion` 加 1。

### 2. 索赔登记

- 外部索赔号（`externalClaimNo`）全局唯一，是登记接口的**幂等键**：
  同号重试且运输单/损失事件一致时返回既有索赔（HTTP 200），不重复建单；
  同号但运输单或损失事件不同返回 409。
- **同一运输单 + 同一损失事件（`lossEventRef`）只能存在一笔活动索赔**，
  由数据库唯一约束 `(shipment_id, active_slot)` 兜底并发；索赔关闭后释放槽位，可重新登记。
- 索赔金额必须为正数。

### 3. 责任认定与按比例分摊

- 认可金额必须为正数且**不得超过索赔金额**。
- 认可金额按各承运段**权重**归一化后按比例分摊：
  - 全部金额使用 `BigDecimal`，统一保留 2 位小数、`HALF_UP` 舍入；
  - 尾差由最后一段承担，保证**各分录金额之和恰好等于认可金额**；
  - 占比保留 4 位小数仅作留痕展示。
- 分摊目标必须是同一运输单下的承运段，同一承运段不能在一次分摊中重复出现。
- 决定分两步：
  - `draft` 草拟：可反复修改方案；
  - `confirm` 确认：**一次性生成全部责任分录**。
- 一笔索赔在数据库层面只允许存在一行责任决定（`claim_id` 唯一），
  重做即重开（`reopen`）复用该行，从根本上杜绝两套结果。
- 对已确认决定再次 `confirm`：方案一致时幂等返回既有结果；方案不同返回 409，需先 `reopen`。

### 4. 幂等与并发、版本冲突

- 所有对同一索赔责任决定的写操作先以 `SELECT … FOR UPDATE` 锁定索赔行串行化，
  配合唯一约束，**并发责任决定不会产生两套结果**（竞争失败方收到 409）。
- 决定记录所基于的 `evidenceVersion` 与索赔 `contentVersion`：
  - 请求可携带期望版本（`expectedEvidenceVersion` / `expectedClaimVersion`），
    过期版本的提交直接返回 409；
  - **新交接证据到达**（证据版本前进）或**索赔内容变化**（金额/类型/证据变更，内容版本前进）后，
    基于旧版本的已确认决定变为 `stale`，确认/结算一律返回 409，
    必须 `reopen` 后基于最新版本重做；
- 索赔内容只能在结算前变更，每次变更内容版本 +1。

### 5. 结算与结算后调整

- 只有已确认且依据版本为最新的决定才能结算；结算重试幂等。
- **结算后责任决定永久不可修改**（草拟/重开/再确认全部 409），只能追加调整：
  - 追偿（RECOVERY）：向责任承运段追回，不设上限；
  - 冲回（REVERSAL）：冲减责任，**单条分录累计冲回不得超过其已结算责任金额**，
    分录行级悲观锁防止并发冲回超额。
- 台账视图中每条分录给出累计追偿、累计冲回与净责任
  （净责任 = 分摊金额 + 累计追偿 − 累计冲回）。

### 金额规则

统一见 `support/Money.java`：精度 2 位、`HALF_UP`；不允许输入超过 2 位小数的金额
（避免隐式吞掉金额）。

## REST 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/shipments` | 创建运输单 |
| GET | `/api/shipments/{shipmentNo}` | 运输单信息（含证据版本） |
| POST | `/api/shipments/{shipmentNo}/segments` | 追加承运段 |
| GET | `/api/shipments/{shipmentNo}/segments` | 承运段台账 |
| POST | `/api/shipments/{shipmentNo}/evidences` | 追加交接证据（证据版本 +1） |
| GET | `/api/shipments/{shipmentNo}/evidences` | 交接证据台账 |
| POST | `/api/claims` | 登记索赔（索赔号幂等：新建 201 / 命中 200） |
| GET | `/api/claims/{externalClaimNo}` | 索赔详情 |
| POST | `/api/claims/{externalClaimNo}/revise` | 变更索赔内容（内容版本 +1） |
| POST | `/api/claims/{externalClaimNo}/close` | 关闭索赔 |
| GET | `/api/claims/shipment/{shipmentNo}` | 按运输单列索赔 |
| GET | `/api/claims/shipment/{shipmentNo}/evidence-ledger` | **索赔证据台账**（交接证据 + 索赔证据） |
| POST | `/api/claims/{externalClaimNo}/decision/draft` | 保存草拟分摊方案 |
| POST | `/api/claims/{externalClaimNo}/decision/confirm` | 确认并一次性生成分录 |
| POST | `/api/claims/{externalClaimNo}/decision/reopen` | 结算前重开已确认决定 |
| GET | `/api/claims/{externalClaimNo}/decision` | **责任分摊台账**（含 stale/settled 与净额） |
| POST | `/api/claims/{externalClaimNo}/settlement` | 结算 |
| GET | `/api/claims/{externalClaimNo}/settlement` | **结算台账** |
| POST | `/api/claims/{externalClaimNo}/adjustments` | 追加追偿/冲回 |
| GET | `/api/claims/{externalClaimNo}/adjustments` | **调整台账** |

责任决定请求示例：

```json
{
  "approvedAmount": 100.00,
  "allocations": [
    {"segmentId": 1, "weight": 1},
    {"segmentId": 2, "weight": 1},
    {"segmentId": 3, "weight": 1}
  ],
  "expectedEvidenceVersion": 1,
  "expectedClaimVersion": 1
}
```

### 状态码约定

- `201` 创建成功；`200` 幂等命中或查询成功；
- `404` 资源不存在；
- `409` 业务冲突：活动索赔重复、并发竞争、版本过期、结算后修改、冲回超额；
- `422` 规则/参数校验失败（金额、分摊规则等）。

## 测试

`src/test/java` 下的自动化测试覆盖：

- `MoneyTest`：按权重分摊、尾差兜底、舍入与非法金额；
- `ClaimServiceTest`：索赔号幂等、活动索赔唯一（含关闭后重开）、内容版本；
- `LiabilityServiceTest`：分摊之和等于认可金额、认可金额上限、跨运输单承运段拒绝、
  确认幂等/不同方案冲突、新证据与内容变更致 stale、结算冻结、冲回上限、调整前置条件；
- `ConcurrencyTest`：并发确认只有一套分录、并发冲回不超额、并发登记同事件只有一笔活动索赔；
- `FreightClaimApiTest`：完整 HTTP 生命周期与 201/200/404/409/422 状态码、四类台账查询。
