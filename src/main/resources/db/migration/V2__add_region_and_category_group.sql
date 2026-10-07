-- 화면용 파생 값. 원본(zip_cd, category)은 그대로 두고 계산 결과만 따로 저장한다.
-- 계산 규칙은 Java 의 Regions / Categories 와 같다. 이미 저장된 공고는 아래 UPDATE 로 채운다.
ALTER TABLE policies ADD COLUMN sido_codes     VARCHAR(100);           -- zipCd 앞 2자리, 콤마 목록
ALTER TABLE policies ADD COLUMN nationwide     BOOLEAN NOT NULL DEFAULT FALSE; -- 16개 시도를 모두 포함
ALTER TABLE policies ADD COLUMN category_group VARCHAR(100);           -- 공식 대분류 5개로 묶은 값, 콤마 목록

WITH sido AS (
    SELECT p.id, array_agg(DISTINCT substr(trim(z), 1, 2)) AS codes
    FROM policies p, unnest(string_to_array(p.zip_cd, ',')) AS z
    WHERE length(trim(z)) >= 2
    GROUP BY p.id
)
UPDATE policies p
SET sido_codes = (
        SELECT string_agg(c, ',' ORDER BY array_position(
            ARRAY['11','26','27','28','30','31','36','41','51','43','44','52','12','47','48','50'], c), c)
        FROM unnest(sido.codes) AS c),
    nationwide = sido.codes @> ARRAY['11','26','27','28','30','31','36','41','51','43','44','52','12','47','48','50']
FROM sido
WHERE p.id = sido.id;

WITH mapped AS (
    SELECT p.id,
           COALESCE(m.grp, trim(part.name)) AS grp,
           COALESCE(m.ord, 99) AS ord
    FROM policies p, unnest(string_to_array(p.category, ',')) AS part(name)
    LEFT JOIN (VALUES
        ('일자리', '일자리', 1),
        ('주거', '주거', 2),
        ('교육', '교육', 3),
        (U&'교육\FF65직업훈련', '교육', 3),
        ('복지문화', '복지문화', 4),
        (U&'금융\FF65복지\FF65문화', '복지문화', 4),
        ('참여권리', '참여권리', 5),
        (U&'참여\FF65기반', '참여권리', 5)
    ) AS m(raw, grp, ord) ON m.raw = trim(part.name)
    WHERE trim(part.name) <> ''
),
grouped AS (
    SELECT id, string_agg(grp, ',' ORDER BY ord, grp) AS grp
    FROM (SELECT DISTINCT id, grp, ord FROM mapped) d
    GROUP BY id
)
UPDATE policies p
SET category_group = grouped.grp
FROM grouped
WHERE p.id = grouped.id;

CREATE INDEX idx_policies_nationwide ON policies (nationwide);
