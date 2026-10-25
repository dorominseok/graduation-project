import { createBrowserRouter } from 'react-router-dom'
import { AppShell } from './AppShell'
import { FullScreenLayout } from './FullScreenLayout'
import { paths } from './paths'
import { RequireAuth } from '../auth'

import { LoginScreen } from '../screens/auth/LoginScreen'
import { SignupScreen } from '../screens/auth/SignupScreen'

import { HomeScreen } from '../screens/tabs/HomeScreen'
import { HistoryScreen } from '../screens/tabs/HistoryScreen'
import { AnalysisScreen } from '../screens/tabs/AnalysisScreen'
import { GroupScreen } from '../screens/tabs/GroupScreen'
import { ProfileScreen } from '../screens/tabs/ProfileScreen'

import { SessionScreen } from '../screens/full/SessionScreen'
import { RoutineScreen } from '../screens/routine/RoutineScreen'
import { ExerciseListScreen } from '../screens/full/ExerciseListScreen'
import { ExerciseDetailScreen } from '../screens/full/ExerciseDetailScreen'
import { ExerciseHistoryScreen } from '../screens/full/ExerciseHistoryScreen'
import { SettingsScreen } from '../screens/full/SettingsScreen'
import { AccountSettingsScreen } from '../screens/full/AccountSettingsScreen'
import { PersonalInfoScreen } from '../screens/full/PersonalInfoScreen'
import { FeedbackScreen } from '../screens/full/FeedbackScreen'
import { DevComponentsScreen } from '../screens/DevComponentsScreen'
import { PrivacyScreen } from '../screens/full/PrivacyScreen'
import { GroupDetailScreen } from '../screens/group/GroupDetailScreen'
import { MemberCalendarScreen } from '../screens/group/MemberCalendarScreen'
import { MemberDayScreen } from '../screens/group/MemberDayScreen'
import { JoinScreen } from '../screens/group/JoinScreen'

export const router = createBrowserRouter([
  // 탭 밖 — 인증. 로그인 전에 닿아야 하므로 가드 밖에 둔다.
  {
    element: <FullScreenLayout />,
    children: [
      { path: paths.login, element: <LoginScreen /> },
      { path: paths.signup, element: <SignupScreen /> },
      // 개인정보처리방침은 가입 전에도, 구글 동의 화면에서도 열린다
      { path: paths.privacy, element: <PrivacyScreen /> },
    ],
  },

  // 로그인이 필요한 화면 전부. RequireAuth가 Outlet으로 아래를 감싼다.
  {
    element: <RequireAuth />,
    children: [
      // 하단 탭이 보이는 화면 — 탭 5개와, 둘러보는 화면들
      //
      // 종목·설정은 탭이 아니지만 하단바를 남긴다. 구경하다 다른 탭으로 바로 갈 수
      // 있어야 하고, 떠나도 잃을 게 없다. 어느 탭에 불을 켤지는 TabBar가 경로로 정한다.
      {
        element: <AppShell />,
        children: [
          { path: paths.home, element: <HomeScreen /> },
          { path: paths.history, element: <HistoryScreen /> },
          { path: paths.analysis, element: <AnalysisScreen /> },
          { path: paths.group, element: <GroupScreen /> },
          { path: paths.profile, element: <ProfileScreen /> },
          { path: paths.exerciseList, element: <ExerciseListScreen /> },
          { path: paths.exerciseDetail(), element: <ExerciseDetailScreen /> },
          { path: paths.exerciseHistory(), element: <ExerciseHistoryScreen /> },
          { path: paths.settings, element: <SettingsScreen /> },
          { path: paths.accountSettings, element: <AccountSettingsScreen /> },
          { path: paths.personalInfo, element: <PersonalInfoScreen /> },
          { path: paths.groupDetail(), element: <GroupDetailScreen /> },
          { path: paths.groupMember(), element: <MemberCalendarScreen /> },
          { path: paths.groupMemberDay(), element: <MemberDayScreen /> },
          // 초대 링크는 로그인 가드 안에 둔다. 로그인 전이면 로그인(또는 가입) 뒤 여기로 돌아온다
          { path: paths.join(), element: <JoinScreen /> },
        ],
      },

      // 하단 탭을 숨기는 화면 — 하던 일이 있는 곳
      //
      // 기록 화면은 아직 체크하지 않은 세트를 화면 안에만 들고 있다. 하단바가 있으면
      // 탭 하나 잘못 눌러 적어둔 세트가 통째로 사라진다. 나가는 길은 ← 하나로 둔다.
      //
      // 루틴 화면도 여기다. 후보정(교체·세트·빼기)을 그 화면에서 하므로 따로 화면을 두지 않는다
      {
        element: <FullScreenLayout />,
        children: [
          { path: paths.session, element: <SessionScreen /> },
          { path: paths.sessionDetail(), element: <SessionScreen /> },
          { path: paths.routineDetail(), element: <RoutineScreen /> },
          { path: paths.feedback, element: <FeedbackScreen /> },
        ],
      },
    ],
  },

  // 공용 컴포넌트 확인용. 어디에서도 링크하지 않고 로그인도 요구하지 않는다.
  // import.meta.env.DEV는 프로덕션 빌드에서 false로 치환되므로,
  // 이 경로와 DevComponentsScreen은 배포 번들에서 통째로 빠진다.
  ...(import.meta.env.DEV
    ? [
        {
          element: <FullScreenLayout />,
          children: [{ path: '/dev/components', element: <DevComponentsScreen /> }],
        },
      ]
    : []),
])
