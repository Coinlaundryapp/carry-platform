-- processed_events 멱등 키를 (consumer_group, event_id) 복합키로 변경한다.
--
-- 단독 event_id 키는 하나의 이벤트가 여러 consumer 그룹으로 fan-out될 때(예: OrderCreatedEvent를
-- dispatch·notification·operation·payment 모듈이 각자 소비) 먼저 claim한 그룹이 나머지 그룹을
-- "중복"으로 굶기는 레이스를 유발했다(예: notification이 이기면 dispatch가 배차를 못 만듦).
-- consumer_group을 키에 포함해 그룹별 독립 멱등을 보장한다.

ALTER TABLE processed_events RENAME COLUMN id TO event_id;

-- 기존 행(그룹 정보 없음)은 'legacy'로 백필한다. 각 그룹의 컨슈머 오프셋은 이미 커밋돼 있어
-- 재배달되지 않으므로(at-least-once 윈도우 밖) 재처리 위험은 없다.
ALTER TABLE processed_events ADD COLUMN consumer_group VARCHAR(100) NOT NULL DEFAULT 'legacy';

ALTER TABLE processed_events DROP CONSTRAINT processed_events_pkey;
ALTER TABLE processed_events ADD CONSTRAINT processed_events_pkey PRIMARY KEY (consumer_group, event_id);

ALTER TABLE processed_events ALTER COLUMN consumer_group DROP DEFAULT;
