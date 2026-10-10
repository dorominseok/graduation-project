import { useLocation, useNavigate } from 'react-router-dom'
import { ScreenHeader } from '../../components'
import { paths } from '../../app/paths'
import styles from './privacy.module.css'

const CONTACT = 'hee0116as@gmail.com'

/**
 * 개인정보처리방침. 로그인 없이 열린다 — 가입 전에 읽을 수 있어야 하고, 구글 로그인 동의
 * 화면도 이 주소로 연결된다(LOG-37).
 *
 * 내용은 실제 저장하는 것과 맞아야 한다. 저장 항목·보관 기간이 바뀌면 여기도 고친다.
 */
export function PrivacyScreen() {
  const navigate = useNavigate()
  const location = useLocation()

  // 구글 동의 화면 같은 바깥 링크로 바로 들어오면 되돌아갈 앱 안 기록이 없다
  const back = () => (location.key === 'default' ? navigate(paths.home) : navigate(-1))

  return (
    <>
      <ScreenHeader title="개인정보처리방침" onBack={back} />

      <article className={styles.page}>
        <p className={styles.lead}>
          밸런스핏은 운동 기록을 분석해 부족한 부위를 알려 주는 졸업작품 서비스입니다. 서비스에
          꼭 필요한 정보만 받고, 탈퇴하면 지웁니다.
        </p>

        <section>
          <h2>1. 받는 정보</h2>
          <ul>
            <li>
              <b>이메일로 가입할 때</b>: 이메일, 비밀번호, 닉네임. 비밀번호는 되돌릴 수 없게 암호화해
              저장하므로 운영자도 알 수 없습니다.
            </li>
            <li>
              <b>구글로 가입할 때</b>: 구글 계정의 이메일과 이름(닉네임으로 씀), 구글 회원번호. 구글
              비밀번호는 받지 않습니다.
            </li>
            <li>
              <b>서비스를 쓰는 동안</b>: 직접 입력한 운동 기록(날짜, 시작·종료 시각, 종목, 세트별
              무게·횟수·시간, 메모)과 즐겨찾기한 종목.
            </li>
            <li>
              <b>로그인 유지</b>: 로그인 상태를 14일 동안 이어 주는 쿠키를 저장합니다. 이 쿠키는
              로그인 외의 용도로 쓰지 않습니다.
            </li>
          </ul>
        </section>

        <section>
          <h2>2. 쓰는 곳</h2>
          <ul>
            <li>회원 확인과 로그인</li>
            <li>운동 기록 저장, 부위별 분석, 운동 추천</li>
            <li>
              졸업작품 결과 정리. 개인을 알아볼 수 없는 통계(예: 참여자 평균 운동 횟수)로만 씁니다.
            </li>
          </ul>
          <p>광고나 마케팅에는 쓰지 않습니다.</p>
        </section>

        <section>
          <h2>3. 보관과 삭제</h2>
          <ul>
            <li>탈퇴하면 계정과 모든 운동 기록을 바로 지웁니다.</li>
            <li>장애에 대비한 백업은 7일 동안만 보관하고 지우므로, 탈퇴 후 7일이 지나면 백업에도 남지 않습니다.</li>
            <li>졸업작품이 끝나 서비스를 닫으면 남은 정보를 모두 지웁니다.</li>
          </ul>
        </section>

        <section>
          <h2>4. 다른 곳에 주는 정보</h2>
          <p>다른 회사나 사람에게 팔거나 넘기지 않습니다. 서비스를 돌리기 위해 아래 두 곳을 씁니다.</p>
          <ul>
            <li>Amazon Web Services(AWS) 서울 리전 — 서버와 데이터 저장</li>
            <li>Google — 구글 로그인을 쓸 때 본인 확인</li>
          </ul>
        </section>

        <section>
          <h2>5. 내 정보 관리</h2>
          <ul>
            <li>닉네임은 설정 › 개인정보에서 바꿀 수 있습니다.</li>
            <li>설정 › 계정 설정 › 회원 탈퇴로 언제든 모든 정보를 지울 수 있습니다.</li>
            <li>그 밖의 요청이나 문의는 아래 이메일로 보내 주세요.</li>
          </ul>
        </section>

        <section>
          <h2>6. 문의</h2>
          <p>
            밸런스핏 개발자 · <a href={`mailto:${CONTACT}`}>{CONTACT}</a>
          </p>
        </section>

        <p className={styles.date}>시행일: 2026년 10월 11일</p>
      </article>
    </>
  )
}
