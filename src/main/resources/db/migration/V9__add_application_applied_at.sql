-- 신청 완료(APPLIED)로 표시한 날짜 (WF04 "10.15 완료 표시"). 다른 상태로 되돌리면 비운다.
ALTER TABLE applications ADD COLUMN applied_at TIMESTAMP;
