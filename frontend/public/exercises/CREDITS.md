# 종목 동작 그림 출처

이 폴더의 그림은 두 곳에서 가져왔다. 받아오고 변환하는 것은
`scripts/fetch_exercise_images.py`가 한다.

## everkinetic (109종 중 95종)

- 출처: https://github.com/everkinetic/data
- 라이선스: **CC BY-SA 4.0** — https://creativecommons.org/licenses/by-sa/4.0/
- 변경: 폭 400px로 줄이고 webp로 바꿨다. 그림 자체는 고치지 않았다.

CC BY-SA는 **출처 표기와 동일조건 공유가 의무**다. 이 그림들과 이를 고친
결과물은 같은 CC BY-SA 4.0으로 공개한다. 이 파일을 지우면 라이선스 조건을
어기는 것이 되므로 그림과 함께 둔다.

## free-exercise-db (나머지 14종)

- 출처: https://github.com/yuhonas/free-exercise-db
- 라이선스: **Unlicense (퍼블릭 도메인)**
- 변경: 폭 400px로 줄이고 webp로 바꿨다.

everkinetic에 그 동작이 없는 종목이다. 복부 계열이 대부분이다 — 플랭크,
싯업, 러시안 트위스트, 레그 풀인, 팔로프 프레스, 랜드마인 180. 그 외에
머슬업, 머신 슈러그, 케이블 데드리프트, 리버스 하이퍼익스텐션, 글루트 햄
레이즈, 페이스풀, 바벨 핑거컬이 있다.

선화와 사진이 섞여 목록이 고르지 않다. 이 14종을 선화로 채우면 해소된다.
직접 만든 그림은 `scripts/import_exercise_images.py`로 넣는다.

## 파일 이름

`{영문명 슬러그}-{1,2}.webp` — 1은 시작 자세, 2는 끝 자세다.
슬러그 규칙은 `scripts/fetch_exercise_images.py`와 프론트
`src/screens/full/exerciseImage.ts` 두 곳에 같은 것이 있고, 한쪽만 바뀌면
그림이 통째로 안 뜬다.
