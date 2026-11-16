package com.fitness.backend.notification.push;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * 푸시 주소로 받는 곳 — 브라우저 회사의 푸시 서버뿐이다.
 *
 * <p>푸시 주소는 화면이 보내 주는 값이고 서버는 알림 때마다 그 주소로 요청을 보낸다. 아무 주소나 받으면 로그인한
 * 누구나 서버가 내부망이나 남의 서버로 요청을 보내게 할 수 있고, 응답이 느린 주소를 여럿 등록해 예약 작업을
 * 붙잡을 수 있다. 그래서 등록할 때와 보낼 때 둘 다 여기서 확인한다.
 *
 * <p>목록에 없는 브라우저가 나오면 등록이 거절되고 그 호스트가 로그에 남는다 — 확인한 뒤 여기에 더한다.
 */
public final class PushEndpointPolicy {

    /** 크롬·삼성 인터넷 같은 크로미움 계열(FCM) */
    private static final String FCM = "fcm.googleapis.com";
    /** 파이어폭스 · 사파리 · 엣지(WNS). 앞에 지역·서버 이름이 붙는다 */
    private static final List<String> SUFFIXES =
            List.of(".push.services.mozilla.com", ".push.apple.com", ".notify.windows.com");
    private static final int HTTPS_PORT = 443;

    private PushEndpointPolicy() {
    }

    /** https이고, 계정 정보나 다른 포트가 붙지 않았고, 호스트가 목록에 있는 주소인가 */
    public static boolean allowed(String endpoint) {
        URI uri = parse(endpoint);
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null) {
            return false;
        }
        if (uri.getPort() != -1 && uri.getPort() != HTTPS_PORT) {
            return false;
        }
        String host = hostOf(endpoint);
        return host != null && (host.equals(FCM) || SUFFIXES.stream().anyMatch(host::endsWith));
    }

    /** 로그에 남길 호스트(소문자). 주소로 읽을 수 없으면 null */
    public static String hostOf(String endpoint) {
        URI uri = parse(endpoint);
        return uri == null || uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
    }

    private static URI parse(String endpoint) {
        try {
            return URI.create(endpoint);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
