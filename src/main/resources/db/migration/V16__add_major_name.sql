-- 전공 이름 직접 입력 (최종 디자인 01 "전공: 전기전자공학"). 판정은 온통청년 전공 계열 코드(plcy_major_cd)로 하므로 이름에서 계열을 정해 함께 저장한다.
ALTER TABLE user_profiles ADD COLUMN major_name VARCHAR(100);
