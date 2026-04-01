//package com.example.portal
//
//import io.quarkus.test.junit.QuarkusTest
//import io.quarkus.test.security.TestSecurity
//import io.restassured.RestAssured.given
//import org.hamcrest.CoreMatchers.*
//import org.junit.jupiter.api.Test
//
//@QuarkusTest
//class SmokeTest {
//    @Test
//    @TestSecurity(user = "test-user", roles = ["portal-user"])
//    fun `metadata endpoint returns portal structure`() {
//        given().`when`().get("/api/portal/metadata").then()
//            .statusCode(200)
//            .body("portalTitle", notNullValue())
//            .body("modules.size()", `is`(1))
//    }
//}
//
