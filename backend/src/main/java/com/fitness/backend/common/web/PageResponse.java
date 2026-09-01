package com.fitness.backend.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * 목록 조회의 공통 응답 envelope. API 명세서 1.4.
 *
 * <p>스프링 {@code Page}를 그대로 직렬화하지 않는다. 그 JSON은 내부 구현이 드러나고
 * ({@code pageable}, {@code sort}, {@code numberOfElements} …) 스프링 버전에 따라
 * 형태가 바뀌어, 명세가 고정한 계약을 지킬 수 없기 때문이다.
 *
 * @param content 항목 배열
 * @param page    페이지 메타
 */
public record PageResponse<T>(List<T> content, PageMeta page) {

    /**
     * @param number        0-기반 페이지 번호
     * @param size          페이지 크기
     * @param totalElements 전체 항목 수. 종목 목록에서는 부위별 종목 수로도 쓴다(명세 5.2)
     * @param totalPages    전체 페이지 수
     * @param first         첫 페이지 여부
     * @param last          마지막 페이지 여부
     */
    public record PageMeta(int number, int size, long totalElements, int totalPages,
                           boolean first, boolean last) {
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), meta(page));
    }

    /** 엔티티 페이지를 응답 DTO로 변환하며 감싼다. */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), meta(page));
    }

    private static PageMeta meta(Page<?> page) {
        return new PageMeta(page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
