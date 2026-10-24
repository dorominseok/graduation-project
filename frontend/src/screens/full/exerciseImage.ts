/**
 * 종목 동작 그림의 주소. 그림은 `frontend/public/exercises/`의 정적 파일이다(원본은
 * 퍼블릭 도메인인 free-exercise-db).
 *
 * <p>파일명은 `exercises.id`가 아니라 영문명 슬러그다. id는 시드 순서대로 매겨져 DB를 새로
 * 만들면 밀릴 수 있고, 그러면 엉뚱한 그림이 조용히 나온다. 슬러그는 어긋나면 그림이 안 뜰
 * 뿐이라 바로 보인다.
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
