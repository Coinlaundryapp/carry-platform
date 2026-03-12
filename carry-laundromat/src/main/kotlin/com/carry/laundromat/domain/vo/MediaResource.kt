package com.carry.laundromat.domain.vo

data class MediaResource(
    val id: Long? = null,
    val url: String,
    val extension: String,
) {
    init {
        require(url.isNotBlank()) { "미디어 URL은 비어있을 수 없습니다" }
        require(extension.isNotBlank()) { "미디어 확장자는 비어있을 수 없습니다" }
    }
}
