package com.carry.app.performance

import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import javax.sql.DataSource

/**
 * 테스트 전용. 실제 DataSource를 datasource-proxy로 래핑해 실행 쿼리 수를 집계한다.
 * 프로덕션 DataSource 구성은 건드리지 않으며, 이 설정을 @Import 한 테스트에만 적용된다.
 */
@TestConfiguration
class QueryCountTestConfig {

    @Bean
    fun queryCountProxyPostProcessor(): BeanPostProcessor = object : BeanPostProcessor {
        override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
            if (bean is DataSource && bean !is net.ttddyy.dsproxy.support.ProxyDataSource) {
                return ProxyDataSourceBuilder.create(bean)
                    .name("perf-guard")
                    .countQuery() // DataSourceQueryCountListener 등록 → QueryCountHolder 집계
                    .build()
            }
            return bean
        }
    }
}
