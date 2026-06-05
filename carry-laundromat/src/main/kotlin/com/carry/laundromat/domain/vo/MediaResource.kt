package com.carry.laundromat.domain.vo

import com.carry.common.exception.requireInput

data class MediaResource(
    val id: Long? = null,
    val url: String,
    val extension: String,
) {
    init {
        requireInput(url.isNotBlank()) { "미디어 URL은 비어있을 수 없습니다" }
        requireInput(extension.isNotBlank()) { "미디어 확장자는 비어있을 수 없습니다" }
    }
}
