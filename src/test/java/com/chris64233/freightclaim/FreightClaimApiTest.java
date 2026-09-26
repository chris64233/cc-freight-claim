package com.chris64233.freightclaim;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FreightClaimApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fullLifecycleThroughHttp() throws Exception {
        // 运输单 + 3 个承运段 + 交接证据
        mockMvc.perform(post("/api/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipmentNo":"S-WEB","origin":"上海","destination":"北京"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidenceVersion").value(0));
        for (String carrier : new String[]{"C1", "C2", "C3"}) {
            mockMvc.perform(post("/api/shipments/S-WEB/segments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"carrierCode\":\"" + carrier + "\"}"))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/shipments/S-WEB/evidences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"location":"上海仓","content":"签收单 POD-1"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidenceVersionAfter").value(1));

        // 登记索赔
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipmentNo":"S-WEB","externalClaimNo":"CL-WEB","lossEventRef":"EVT-1",
                                 "lossAmount":100.00,"lossType":"DAMAGE","evidence":"破损照片"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentVersion").value(1));

        // 幂等重试 -> 200
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipmentNo":"S-WEB","externalClaimNo":"CL-WEB","lossEventRef":"EVT-1",
                                 "lossAmount":100.00,"lossType":"DAMAGE","evidence":"破损照片"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalClaimNo").value("CL-WEB"));

        // 同事件第二笔活动索赔 -> 409
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shipmentNo":"S-WEB","externalClaimNo":"CL-WEB-2","lossEventRef":"EVT-1",
                                 "lossAmount":100.00,"lossType":"DAMAGE","evidence":"破损照片"}"""))
                .andExpect(status().isConflict());

        // 查询承运段取 ID
        String segmentsJson = mockMvc.perform(get("/api/shipments/S-WEB/segments"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long seg1 = segmentId(segmentsJson, 0);
        long seg2 = segmentId(segmentsJson, 1);
        long seg3 = segmentId(segmentsJson, 2);

        // 确认责任决定：100 按 1:1:1 分摊
        mockMvc.perform(post("/api/claims/CL-WEB/decision/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody("100.00",
                                "[%s,1],[%s,1],[%s,1]".formatted(seg1, seg2, seg3), 1, 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.entries[0].allocatedAmount").value(33.33))
                .andExpect(jsonPath("$.entries[2].allocatedAmount").value(33.34));

        // 认可金额超过索赔金额 -> 422
        mockMvc.perform(post("/api/claims/CL-WEB/decision/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody("150.00", "[%s,1]".formatted(seg1), 1, 1)))
                .andExpect(status().isUnprocessableEntity());

        // 新交接证据到达后直接结算 -> 409
        mockMvc.perform(post("/api/shipments/S-WEB/evidences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"location":"北京仓","content":"二次验货报告 POD-2"}"""))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/claims/CL-WEB/settlement")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());

        // 重开 → 基于新版本重做（分摊改为两段）→ 结算
        mockMvc.perform(post("/api/claims/CL-WEB/decision/reopen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        mockMvc.perform(post("/api/claims/CL-WEB/decision/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody("100.00", "[%s,1],[%s,1]".formatted(seg1, seg3), 2, 1)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/claims/CL-WEB/settlement")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"月底结算\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.settledAmount").value(100.00));

        // 结算后改决定 -> 409；追加追偿 -> 201
        mockMvc.perform(post("/api/claims/CL-WEB/decision/reopen"))
                .andExpect(status().isConflict());

        String decisionJson = mockMvc.perform(get("/api/claims/CL-WEB/decision"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long entry1 = entryId(decisionJson, 0);
        mockMvc.perform(post("/api/claims/CL-WEB/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entryId":%d,"type":"RECOVERY","amount":10.00,"reason":"代位追偿"}""".formatted(entry1)))
                .andExpect(status().isCreated());

        // 冲回超过该段责任（50.00）-> 409
        mockMvc.perform(post("/api/claims/CL-WEB/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entryId":%d,"type":"REVERSAL","amount":50.01,"reason":"超额"}""".formatted(entry1)))
                .andExpect(status().isConflict());
        // 足额冲回 50.00 -> 201
        mockMvc.perform(post("/api/claims/CL-WEB/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entryId":%d,"type":"REVERSAL","amount":50.00,"reason":"全冲"}""".formatted(entry1)))
                .andExpect(status().isCreated());

        // 台账查询
        mockMvc.perform(get("/api/claims/CL-WEB/adjustments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/claims/shipment/S-WEB/evidence-ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3)); // 2 条交接证据 + 1 条索赔证据

        // 不存在的资源 -> 404
        mockMvc.perform(get("/api/claims/NO-SUCH-CLAIM/decision"))
                .andExpect(status().isNotFound());
    }

    private static String decisionBody(String approved, String pairs, int evVer, int claimVer) {
        StringBuilder allocations = new StringBuilder("[");
        for (String pair : pairs.split("],\\[")) {
            String cleaned = pair.replace("[", "").replace("]", "");
            String[] kv = cleaned.split(",");
            if (allocations.length() > 1) {
                allocations.append(",");
            }
            allocations.append("{\"segmentId\":").append(kv[0].trim())
                    .append(",\"weight\":").append(kv[1].trim()).append("}");
        }
        allocations.append("]");
        return """
                {"approvedAmount":%s,"allocations":%s,
                 "expectedEvidenceVersion":%d,"expectedClaimVersion":%d}"""
                .formatted(approved, allocations, evVer, claimVer);
    }

    private static long segmentId(String json, int index) {
        return idFromJson(json, "\"id\"", index);
    }

    private static long entryId(String json, int index) {
        // 在 entries 数组内定位第 index 个 "id"（跳过决定自身与视图里的其他 id）。
        int entriesStart = json.indexOf("\"entries\"");
        int pos = entriesStart;
        for (int i = 0; i <= index; i++) {
            pos = json.indexOf("\"id\"", pos + 1);
        }
        int colon = json.indexOf(':', pos);
        int start = colon + 1;
        while (Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }

    private static long idFromJson(String json, String key, int index) {
        int pos = -1;
        for (int i = 0; i <= index; i++) {
            pos = json.indexOf(key, pos + 1);
        }
        int colon = json.indexOf(':', pos);
        int end = colon + 1;
        while (Character.isWhitespace(json.charAt(end))) {
            end++;
        }
        int start = end;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }
}
