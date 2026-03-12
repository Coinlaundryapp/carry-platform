package com.carry.operation.adapter.inbound.rest

import com.carry.operation.adapter.inbound.rest.dto.CreateTermRequest
import com.carry.operation.adapter.inbound.rest.dto.TermResponse
import com.carry.operation.adapter.inbound.rest.dto.UpdateTermRequest
import com.carry.operation.application.port.inbound.CreateTermCommand
import com.carry.operation.application.port.inbound.TermCommandUseCase
import com.carry.operation.application.port.inbound.UpdateTermCommand
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/admin/terms")
class TermAdminController(
    private val termCommandUseCase: TermCommandUseCase,
) {

    @PostMapping
    fun createTerm(
        @RequestBody request: CreateTermRequest,
    ): ResponseEntity<TermResponse> {
        val term = termCommandUseCase.createTerm(
            CreateTermCommand(
                title = request.title,
                content = request.content,
                type = request.type,
                required = request.required,
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(TermResponse.from(term))
    }

    @PutMapping("/{termId}")
    fun updateTerm(
        @PathVariable termId: Long,
        @RequestBody request: UpdateTermRequest,
    ): ResponseEntity<TermResponse> {
        val term = termCommandUseCase.updateTerm(
            UpdateTermCommand(
                termId = termId,
                title = request.title,
                content = request.content,
                required = request.required,
            ),
        )
        return ResponseEntity.ok(TermResponse.from(term))
    }

    @DeleteMapping("/{termId}")
    fun deactivateTerm(
        @PathVariable termId: Long,
    ): ResponseEntity<Void> {
        termCommandUseCase.deactivateTerm(termId)
        return ResponseEntity.noContent().build()
    }
}
