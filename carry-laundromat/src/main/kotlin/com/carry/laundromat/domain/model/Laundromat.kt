package com.carry.laundromat.domain.model

import com.carry.laundromat.domain.vo.LaundromatAddress
import com.carry.laundromat.domain.vo.LaundromatOption
import com.carry.laundromat.domain.vo.Location
import com.carry.laundromat.domain.vo.MediaResource
import java.time.Instant

class Laundromat private constructor(
    val id: Long?,
    private var _name: String,
    private var _address: LaundromatAddress,
    private var _location: Location,
    private val _options: MutableSet<LaundromatOption>,
    private val _mediaResources: MutableList<MediaResource>,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) {
    val name: String get() = _name
    val address: LaundromatAddress get() = _address
    val location: Location get() = _location
    val options: Set<LaundromatOption> get() = _options.toSet()
    val mediaResources: List<MediaResource> get() = _mediaResources.toList()

    fun updateInfo(name: String, address: LaundromatAddress, location: Location) {
        require(name.isNotBlank()) { "세탁소 이름은 비어있을 수 없습니다" }
        _name = name
        _address = address
        _location = location
    }

    fun addOption(option: LaundromatOption): Boolean {
        return _options.add(option)
    }

    fun removeOption(option: LaundromatOption): Boolean {
        return _options.remove(option)
    }

    fun replaceOptions(options: Set<LaundromatOption>) {
        _options.clear()
        _options.addAll(options)
    }

    fun addMediaResource(url: String, extension: String): MediaResource {
        val resource = MediaResource(url = url, extension = extension)
        _mediaResources.add(resource)
        return resource
    }

    fun removeMediaResource(mediaResourceId: Long) {
        _mediaResources.removeIf { it.id == mediaResourceId }
    }

    companion object {
        fun create(
            name: String,
            address: LaundromatAddress,
            location: Location,
            options: Set<LaundromatOption> = emptySet(),
        ): Laundromat {
            require(name.isNotBlank()) { "세탁소 이름은 비어있을 수 없습니다" }
            return Laundromat(
                id = null,
                _name = name,
                _address = address,
                _location = location,
                _options = options.toMutableSet(),
                _mediaResources = mutableListOf(),
                createdAt = null,
                updatedAt = null,
            )
        }

        fun reconstitute(
            id: Long,
            name: String,
            address: LaundromatAddress,
            location: Location,
            options: Set<LaundromatOption>,
            mediaResources: List<MediaResource>,
            createdAt: Instant,
            updatedAt: Instant,
        ): Laundromat = Laundromat(
            id = id,
            _name = name,
            _address = address,
            _location = location,
            _options = options.toMutableSet(),
            _mediaResources = mediaResources.toMutableList(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
