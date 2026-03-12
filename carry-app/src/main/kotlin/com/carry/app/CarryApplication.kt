package com.carry.app

import com.carry.security.jwt.JwtProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication(
    scanBasePackages = ["com.carry"]
)
@EnableConfigurationProperties(JwtProperties::class)
class CarryApplication

fun main(args: Array<String>) {
    runApplication<CarryApplication>(*args)
}
