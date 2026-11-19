package com.fitness.backend.importing.web;

import com.fitness.backend.auth.jwt.JwtProvider;
import com.fitness.backend.common.web.ApiV1Controller;
import com.fitness.backend.importing.service.ImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 다른 앱 기록 붙여넣기(「기록가져오기_설계서」). 붙여넣은 글을 미리보기로 읽고, 정한 대로 그날 기록에 넣는다.
 */
@ApiV1Controller
@RequestMapping("/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @Operation(summary = "기록 가져오기 미리보기",
            description = "바디캘린더 운동 일지 글을 날짜별로 읽어 우리 종목에 맞춘다. 저장하지 않는다. 유산소·미래 날짜는 뺀다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "날짜별 종목·세트와 맞춘 결과(MATCHED·SIMILAR·UNREGISTERED)"),
            @ApiResponse(responseCode = "400", description = "날짜 줄이 없음(IMPORT_FORMAT_UNKNOWN), 10만 자 초과, 31일 초과")
    })
    @PostMapping("/preview")
    public ImportDtos.PreviewResponse preview(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @Valid @RequestBody ImportDtos.PreviewRequest request) {
        return importService.preview(principal.userId(), request.text());
    }

    @Operation(summary = "기록 가져오기",
            description = "미리보기에서 정한 종목으로 그날 기록에 넣는다. 같은 글을 다시 넣으면 이미 있는 세트는 건너뛴다")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "날마다 기록 id와 넣은 세트 수·이미 있던 세트 수"),
            @ApiResponse(responseCode = "400", description = "기록 방식에 필요한 값 없음, 미래 날짜, 같은 날짜 두 번, 31일 초과"),
            @ApiResponse(responseCode = "404", description = "보이지 않는 종목(남의 내 종목·숨긴 내 종목)")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImportDtos.ImportResponse save(
            @AuthenticationPrincipal JwtProvider.AuthenticatedUser principal,
            @Valid @RequestBody ImportDtos.ImportRequest request) {
        return importService.save(principal.userId(), request);
    }
}
