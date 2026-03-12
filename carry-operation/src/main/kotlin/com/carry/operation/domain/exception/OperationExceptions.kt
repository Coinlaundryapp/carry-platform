package com.carry.operation.domain.exception

class TermNotFoundException(termId: Long) :
    RuntimeException("약관을 찾을 수 없습니다: $termId")
