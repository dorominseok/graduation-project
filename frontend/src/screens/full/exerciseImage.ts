/**
 * 종목 동작 그림의 주소.
 *
 * <p>그림은 `frontend/public/exercises/`에 정적 파일로 두고 백엔드를 거치지 않는다.
 * 사용자가 올리는 게 아니라 제품에 딸려오는 고정 자산이라 코드와 같이 버전이
 * 매겨지는 편이 맞다. 원본은 free-exercise-db(퍼블릭 도메인)다.
 *
 * <p>파일명이 `exercises.id`가 아니라 영문명 슬러그인 것은, id가 BIGSERIAL이고
 * 시드에 번호가 박혀 있지 않아 CSV 순서를 바꾸고 DB를 새로 만들면 번호가 밀리기
 * 때문이다. 그러면 벤치프레스 자리에 스쿼트 그림이 조용히 나온다. 슬러그는
 * 어긋나면 그림이 안 뜰 뿐이라 바로 눈에 띈다.
 */

/** `scripts/fetch_exercise_images.py`의 같은 규칙과 반드시 일치해야 한다. */
function slug(nameEn: string): string {
  return nameEn
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

/** 종목당 시작 자세(1)와 끝 자세(2) 두 장이 있다. */
export type ShotIndex = 1 | 2

export function exerciseImage(nameEn: string | null | undefined, shot: ShotIndex = 1): string | null {
  if (!nameEn) return null
  return `/exercises/${slug(nameEn)}-${shot}.webp`
}

/**
 * 그림이 없을 때 자리를 비운다.
 *
 * <p>종목을 새로 넣고 그림을 안 받아두면 깨진 이미지 아이콘이 뜬다. 빈 칸이
 * 낫다 — 그림이 원래 없는 건지 파일명이 어긋난 건지는 개발 중에 콘솔로 본다.
 */
export function hideOnError(event: React.SyntheticEvent<HTMLImageElement>): void {
  event.currentTarget.style.visibility = 'hidden'
}
