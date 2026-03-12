package com.carry.media.domain.vo

enum class MediaStatus {
    UPLOADING,
    COMPLETED,
    FAILED;

    fun canTransitionTo(target: MediaStatus): Boolean = when (this) {
        UPLOADING -> target in setOf(COMPLETED, FAILED)
        COMPLETED -> false
        FAILED -> false
    }
}
