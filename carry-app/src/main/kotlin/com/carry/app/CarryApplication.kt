package com.carry.app

import com.carry.geo.adapter.outbound.external.naver.NaverApiProperties
import com.carry.infra.kafka.KafkaTopicProperties
import com.carry.security.jwt.JwtProperties
import com.carry.user.adapter.outbound.auth.GoogleProperties
import com.carry.user.adapter.outbound.auth.KakaoProperties
import com.carry.user.adapter.outbound.auth.NaverProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication(
    scanBasePackages = ["com.carry"]
)
@EnableConfigurationProperties(
    JwtProperties::class,
    NaverApiProperties::class,
    KakaoProperties::class,
    NaverProperties::class,
    GoogleProperties::class,
    KafkaTopicProperties::class,
)
class CarryApplication

fun main(args: Array<String>) {
    runApplication<CarryApplication>(*args)
}
