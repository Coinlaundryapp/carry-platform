rootProject.name = "carry-platform"

// ── Common ──
include("carry-common")
include("carry-event")
include("carry-audit")

// ── Infrastructure ──
include("carry-infra-persistence")
include("carry-infra-kafka")
include("carry-infra-redis")
include("carry-infra-s3")
include("carry-infra-observability")

// ── Security ──
include("carry-security")

// ── Domain Modules ──
include("carry-user")
include("carry-laundromat")
include("carry-price")
include("carry-geo")
include("carry-order")
include("carry-payment")
include("carry-dispatch")
include("carry-delivery")
include("carry-operation")
include("carry-review")
include("carry-notification")
include("carry-media")
include("carry-service-availability")

// ── Application ──
include("carry-app")
