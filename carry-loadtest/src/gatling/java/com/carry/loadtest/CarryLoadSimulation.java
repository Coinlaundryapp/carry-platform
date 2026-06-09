package com.carry.loadtest;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class CarryLoadSimulation extends Simulation {

    HttpProtocolBuilder httpProtocol = http
        .baseUrl(System.getProperty("baseUrl", "http://localhost:8080"))
        .acceptHeader("application/json").contentTypeHeader("application/json");

    String customerToken = TokenFactory.accessToken(1L, "CUSTOMER");
    String carrierToken  = TokenFactory.accessToken(2L, "CARRIER");

    // 미래 시각, deliveryAt > pickupAt (도메인 불변식). 정적 StringBody는 placeholder를 못 채우므로
    // 시뮬레이션 인스턴스 생성 시점에 ISO-8601 문자열로 조립한다.
    String pickupAt   = Instant.now().plus(2, ChronoUnit.HOURS).toString();
    String deliveryAt = Instant.now().plus(24, ChronoUnit.HOURS).toString();
    String createBody = "{ \"shippingAddressId\":1, \"laundromatId\":1, \"laundryItemType\":\"NORMAL\","
        + " \"selectedOptions\":[{\"optionType\":\"WASH\",\"subOptionType\":\"COLD\"}],"
        + " \"desiredPickupAt\":\"" + pickupAt + "\", \"desiredDeliveryAt\":\"" + deliveryAt + "\" }";

    ScenarioBuilder listOrders = scenario("주문 목록 조회")
        .exec(http("GET /orders/my")
            .get("/api/v2/orders/my?size=20")
            .header("Authorization", "Bearer " + customerToken)
            .check(status().is(200)));

    ScenarioBuilder createOrder = scenario("주문 생성")
        .exec(http("POST /orders")
            .post("/api/v2/orders")
            .header("Authorization", "Bearer " + customerToken)
            .body(StringBody(createBody))
            .check(status().is(201)));

    // 배차 수락: available 목록에서 dispatchId를 추출 → claim. PENDING 풀이 유한하고 carrier 토큰이
    // 단일(userId=2)이라 동시 claim이 같은 행을 노릴 수 있다. 풀(시드 30) > 사용자(20)지만
    // 동일 행 경합으로 일부는 409(이미 선점)가 정상이므로 in(200,409)를 허용한다(README에 명시).
    ScenarioBuilder claimDispatch = scenario("배차 선점")
        .exec(http("GET /dispatches/available")
            .get("/api/v2/dispatches/available?size=1")
            .header("Authorization", "Bearer " + carrierToken)
            .check(status().is(200))
            .check(jsonPath("$.data[0].id").saveAs("dispatchId")))
        .exec(http("POST /dispatches/{id}/claim")
            .post("/api/v2/dispatches/#{dispatchId}/claim")
            .header("Authorization", "Bearer " + carrierToken)
            .check(status().in(200, 409)));

    {
        setUp(
            listOrders.injectOpen(rampUsers(50).during(30)),
            createOrder.injectOpen(rampUsers(30).during(30)),
            claimDispatch.injectOpen(rampUsers(20).during(30)) // ≤ 시드 PENDING 풀 크기(30)
        ).protocols(httpProtocol)
         .assertions(
            global().responseTime().percentile3().lt(BASELINE_P95_MS), // 최초 측정 후 기준선*1.5로 설정
            global().failedRequests().percent().lt(1.0)
         );
    }

    // 최초 측정 전에는 느슨하게(2000) 두고, Task 11 측정 후 기준선×1.5로 교체.
    static final int BASELINE_P95_MS = 2000;
}
