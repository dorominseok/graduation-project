/**
 * 다른 앱 기록 가져오기(API 명세 13장, 「기록가져오기_설계서」). 첫 판은 바디캘린더 글만 읽는다.
 */

import { request } from './client'
import type { ImportPreview, ImportRequest, ImportResult } from './types'

/** 글을 날짜별로 읽고 우리 종목에 맞춘다. 저장하지 않는다. 날짜 줄이 없으면 400 IMPORT_FORMAT_UNKNOWN */
export function preview(text: string): Promise<ImportPreview> {
  return request<ImportPreview>('/imports/preview', { method: 'POST', body: { text } })
}

/** 미리보기에서 정한 대로 넣는다. 같은 글을 다시 넣으면 이미 있는 세트는 건너뛴다(added 0) */
export function save(body: ImportRequest): Promise<ImportResult> {
  return request<ImportResult>('/imports', { method: 'POST', body })
}
