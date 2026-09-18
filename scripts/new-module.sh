#!/usr/bin/env bash
# 헥사고날 도메인 모듈 골격 생성기.
# 사용법:  ./scripts/new-module.sh <module-name> <base-package>
#    예)   ./scripts/new-module.sh carry-foo com.carry.foo
set -euo pipefail

NAME="${1:?module name 필요 (예: carry-foo)}"
PKG="${2:?base package 필요 (예: com.carry.foo)}"

cd "$(dirname "$0")/.."
if [ -d "$NAME" ]; then
  echo "✗ 이미 존재하는 모듈: $NAME"
  exit 1
fi

PKG_PATH="${PKG//.//}"

# 헥사고날 3계층 + 포트 디렉토리.
for layer in \
  domain/model domain/vo domain/exception \
  application/port/inbound application/port/outbound application/service \
  adapter/inbound/rest adapter/outbound/persistence; do
  mkdir -p "$NAME/src/main/kotlin/$PKG_PATH/$layer"
done
mkdir -p "$NAME/src/test/kotlin/$PKG_PATH/architecture"

cat > "$NAME/build.gradle.kts" <<'KTS'
plugins {
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.1")
    }
}

dependencies {
    implementation(project(":carry-common"))
    implementation(project(":carry-infra-persistence"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.assertj:assertj-core:3.27.0")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
}
KTS

echo "✓ '$NAME' 모듈 골격 생성 완료 (base package: $PKG)."
echo "  다음 단계:"
echo "    1) settings.gradle.kts 에  include(\"$NAME\")  추가"
echo "    2) HexagonalArchitectureTest 를 기존 모듈에서 복사해 패키지만 교체"
echo "    3) 크로스모듈 결선이 필요하면 carry-app 에 어댑터/포트 구현 추가"
echo "    4) 이벤트 소비가 필요하면 carry-event·carry-infra-kafka 의존 추가"
