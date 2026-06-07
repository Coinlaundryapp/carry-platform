package com.carry.operation.domain.model

import com.carry.operation.domain.vo.TermType
import java.time.Instant

class Term private constructor(
    val id: Long?,
    private var _title: String,
    private var _content: String,
    val type: TermType,
    private var _required: Boolean,
    private var _version: Int,
    private var _active: Boolean,
    val createdAt: Instant,
    private var _updatedAt: Instant,
) {
    val title get() = _title
    val content get() = _content
    val required get() = _required
    val version get() = _version
    val active get() = _active
    val updatedAt get() = _updatedAt

    companion object {
        fun create(title: String, content: String, type: TermType, required: Boolean, now: Instant): Term {
            return Term(
                id = null,
                _title = title,
                _content = content,
                type = type,
                _required = required,
                _version = 1,
                _active = true,
                createdAt = now,
                _updatedAt = now,
            )
        }

        fun reconstitute(
            id: Long,
            title: String,
            content: String,
            type: TermType,
            required: Boolean,
            version: Int,
            active: Boolean,
            createdAt: Instant,
            updatedAt: Instant,
        ): Term = Term(
            id, title, content, type, required, version, active, createdAt, updatedAt,
        )
    }

    fun update(title: String, content: String, required: Boolean, now: Instant) {
        _title = title
        _content = content
        _required = required
        _version += 1
        _updatedAt = now
    }

    fun deactivate(now: Instant) {
        _active = false
        _updatedAt = now
    }

    fun activate(now: Instant) {
        _active = true
        _updatedAt = now
    }
}
