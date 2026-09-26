package com.chris64233.freightclaim.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FreightClaimApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private static String shipmentJson(String no) {
        return """
                {
                  "shipmentNo": "%s",
                  "origin": "上海",
                  "destination": "北京",
                  "segments": [
                    {"carrierCode": "C1", "carrierName": "承运甲", "startLocation": "上海", "endLocation": "南京"},
                    {"carrierCode": "C2", "carrierName": "承运乙", "startLocation": "南京", "endLocation": "济南"},
                    {"carrierCode": "C3", "carrierName": "承运丙", "startLocation": "济南", "endLocation": "北京"}
                  ]
                }
                """.formatted(no);
    }

    private static String claimJson(String extNo, String shipmentNo, String eventNo, String amount) {
        return """
                {
                  "externalClaimNo": "%s",
                  "shipmentNo": "%s",
                  "lossEventNo": "%s",
                  "lossType": "DAMAGE",
                  "lossAmount": %s,
                  "description": "外箱破损",
                  "evidences": [{"evidenceRef": "IMG-1", "evidenceType": "PHOTO", "summary": "破损照片"}]
                }
                """.formatted(extNo, shipmentNo, eventNo, amount);
    }

    private JsonNode postJson(String url, String body, int expectedStatus) throws Exception {
        String response = mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    @Test
    void endToEndHttpWorkflow() throws Exception {
        // 1. 建运输单（含三个按序承运段）
        JsonNode shipment = postJson("/api/shipments", shipmentJson("S-HTTP"), 201);
        assertThat(shipment.get("evidenceVersion").asLong()).isZero();

        // 2. 建索赔
        JsonNode claim = postJson("/api/claims", claimJson("EXT-HTTP", "S-HTTP", "EVT-1", "1000.00"), 200);
        long claimId = claim.get("id").asLong();

        // 索赔号幂等：重复 POST 返回同一笔
        JsonNode again = postJson("/api/claims", claimJson("EXT-HTTP", "S-HTTP", "EVT-1", "1000.00"), 200);
        assertThat(again.get("id").asLong()).isEqualTo(claimId);

        // 同运输单 + 同损失事件第二笔活动索赔 -> 409
        postJson("/api/claims", claimJson("EXT-HTTP-2", "S-HTTP", "EVT-1", "1000.00"), 409);

        // 参数校验：金额必须为正 -> 400
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("EXT-X", "S-HTTP", "EVT-2", "0")))
                .andExpect(status().isBadRequest());

        // 3. 创建责任决定草稿
        String decisionBody = """
                {
                  "approvedAmount": 1000.00,
                  "remark": "等额三方",
                  "allocations": [
                    {"segmentSeq": 1, "ratioWeight": 1},
                    {"segmentSeq": 2, "ratioWeight": 1},
                    {"segmentSeq": 3, "ratioWeight": 1}
                  ]
                }
                """;
        JsonNode draft = postJson("/api/decisions/for-claim/" + claimId, decisionBody, 201);
        long decisionId = draft.get("id").asLong();

        // 认可金额超过索赔金额 -> 400
        String tooMuch = decisionBody.replace("1000.00", "1000.01");
        postJson("/api/decisions/for-claim/" + claimId, tooMuch, 400);

        // 4. 新交接证据到达
        String handoverBody = """
                {"fromSegmentSeq": 1, "toSegmentSeq": 2, "evidenceRef": "HO-1", "summary": "南京交接"}
                """;
        JsonNode handover = postJson("/api/shipments/S-HTTP/handover-evidences", handoverBody, 201);
        assertThat(handover.get("seq").asInt()).isEqualTo(1);

        // 基于旧证据版本确认 -> 409
        postJson("/api/decisions/" + decisionId + "/confirm", "{}", 409);

        // 5. 重新拟定草稿（刷新依据版本），再确认成功；分摊 1000/3 严格凑整
        String refreshed = decisionBody.replace("等额三方", "重新拟定");
        mockMvc.perform(put("/api/decisions/" + decisionId)
                        .contentType(MediaType.APPLICATION_JSON).content(refreshed))
                .andExpect(status().isOk());
        JsonNode confirmed = postJson("/api/decisions/" + decisionId + "/confirm", "{}", 200);
        assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");

        List<BigDecimal> amounts = new java.util.ArrayList<>();
        confirmed.get("allocations").forEach(node ->
                amounts.add(new BigDecimal(node.get("allocatedAmount").asText())));
        BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("1000.00");

        // 重复确认 -> 409；确认后修改 -> 409
        postJson("/api/decisions/" + decisionId + "/confirm", "{}", 409);
        mockMvc.perform(put("/api/decisions/" + decisionId)
                        .contentType(MediaType.APPLICATION_JSON).content(refreshed))
                .andExpect(status().isConflict());

        // 6. 结算
        String settlementBody = """
                {
                  "settlementNo": "STL-HTTP-1",
                  "remark": "全额",
                  "lines": [
                    {"segmentSeq": 1, "amount": 333.34},
                    {"segmentSeq": 2, "amount": 333.33},
                    {"segmentSeq": 3, "amount": 333.33}
                  ]
                }
                """;
        postJson("/api/settlements/for-decision/" + decisionId, settlementBody, 201);

        // 超出认可分摊的结算 -> 400
        String overSettle = settlementBody.replace("STL-HTTP-1", "STL-HTTP-2")
                .replace("\"amount\": 333.34", "\"amount\": 0.01");
        postJson("/api/settlements/for-decision/" + decisionId, overSettle, 400);

        // 7. 冲回不得超过已结算
        String reversalOk = """
                {"segmentSeq": 1, "type": "REVERSAL", "amount": 333.34, "adjustmentRef": "REV-1"}
                """;
        postJson("/api/adjustments/for-decision/" + decisionId, reversalOk, 201);
        String reversalOver = """
                {"segmentSeq": 1, "type": "REVERSAL", "amount": 0.01}
                """;
        postJson("/api/adjustments/for-decision/" + decisionId, reversalOver, 400);

        // 8. 台账查询
        mockMvc.perform(get("/api/claims/" + claimId + "/evidences"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/decisions/for-claim/" + claimId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/settlements/ledger").param("claimId", String.valueOf(claimId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get("/api/adjustments/ledger").param("claimId", String.valueOf(claimId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        // 不存在资源 -> 404
        mockMvc.perform(get("/api/claims/999999")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/shipments/NO-SUCH")).andExpect(status().isNotFound());
    }
}
