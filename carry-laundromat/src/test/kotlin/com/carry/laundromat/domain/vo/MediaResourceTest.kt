package com.carry.laundromat.domain.vo

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class MediaResourceTest {

    @Test
    fun `유효한 미디어 리소스를 생성한다`() {
        val resource = MediaResource(url = "https://s3.amazonaws.com/image.jpg", extension = "jpg")
        assertThat(resource.url).isNotBlank()
        assertThat(resource.id).isNull()
    }

    @Test
    fun `빈 URL은 거부한다`() {
        assertThatThrownBy { MediaResource(url = "", extension = "jpg") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `빈 확장자는 거부한다`() {
        assertThatThrownBy { MediaResource(url = "https://example.com/img.jpg", extension = "") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
