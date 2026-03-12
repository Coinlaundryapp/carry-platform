package com.carry.operation.adapter.inbound.rest

import com.carry.operation.adapter.inbound.rest.dto.TermResponse
import com.carry.operation.application.port.inbound.TermQueryUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/terms")
class TermController(
    private val termQueryUseCase: TermQueryUseCase,
) {

    @GetMapping
    fun getActiveTerms(): ResponseEntity<List<TermResponse>> {
        val terms = termQueryUseCase.getActiveTerms()
        return ResponseEntity.ok(terms.map { TermResponse.from(it) })
    }

    @GetMapping("/required")
    fun getRequiredTerms(): ResponseEntity<List<TermResponse>> {
        val terms = termQueryUseCase.getRequiredTerms()
        return ResponseEntity.ok(terms.map { TermResponse.from(it) })
    }
}
