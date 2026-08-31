import { createBrowserRouter } from 'react-router-dom'
import { AppShell } from './AppShell'
import { FullScreenLayout } from './FullScreenLayout'
import { paths } from './paths'

import { LoginScreen } from '../screens/auth/LoginScreen'
import { SignupScreen } from '../screens/auth/SignupScreen'

import { HomeScreen } from '../screens/tabs/HomeScreen'
import { HistoryScreen } from '../screens/tabs/HistoryScreen'
import { AnalysisScreen } from '../screens/tabs/AnalysisScreen'
import { GroupScreen } from '../screens/tabs/GroupScreen'
import { ProfileScreen } from '../screens/tabs/ProfileScreen'

import { SessionScreen } from '../screens/full/SessionScreen'
import { RoutineDetailScreen } from '../screens/full/RoutineDetailScreen'
import { RoutineFallbackScreen } from '../screens/full/RoutineFallbackScreen'
import { ExerciseListScreen } from '../screens/full/ExerciseListScreen'
import { ExerciseDetailScreen } from '../screens/full/ExerciseDetailScreen'
import { SettingsScreen } from '../screens/full/SettingsScreen'
import { AccountSettingsScreen } from '../screens/full/AccountSettingsScreen'
import { PersonalInfoScreen } from '../screens/full/PersonalInfoScreen'
import { FeedbackScreen } from '../screens/full/FeedbackScreen'
import { DevComponentsScreen } from '../screens/DevComponentsScreen'

export const router = createBrowserRouter([
  // 탭 밖 — 인증
  {
    element: <FullScreenLayout />,
    children: [
      { path: paths.login, element: <LoginScreen /> },
      { path: paths.signup, element: <SignupScreen /> },
    ],
  },

  // 탭 안 — 하단 탭 5개
  {
    element: <AppShell />,
    children: [
      { path: paths.home, element: <HomeScreen /> },
      { path: paths.history, element: <HistoryScreen /> },
      { path: paths.analysis, element: <AnalysisScreen /> },
      { path: paths.group, element: <GroupScreen /> },
      { path: paths.profile, element: <ProfileScreen /> },
    ],
  },

  // 탭 밖 — 전체 화면
  //
  // react-router는 순서가 아니라 구체성으로 매칭하므로 '/routines/fallback'이
  // '/routines/:routineId'보다 항상 우선한다. 정적 경로를 위에 둔 건 읽기 편하라고.
  {
    element: <FullScreenLayout />,
    children: [
      { path: paths.session, element: <SessionScreen /> },
      { path: paths.sessionDetail(), element: <SessionScreen /> },
      { path: paths.routineFallback, element: <RoutineFallbackScreen /> },
      { path: paths.routineDetail(), element: <RoutineDetailScreen /> },
      { path: paths.exerciseList, element: <ExerciseListScreen /> },
      { path: paths.exerciseDetail(), element: <ExerciseDetailScreen /> },
      { path: paths.settings, element: <SettingsScreen /> },
      { path: paths.accountSettings, element: <AccountSettingsScreen /> },
      { path: paths.personalInfo, element: <PersonalInfoScreen /> },
      { path: paths.feedback, element: <FeedbackScreen /> },
      // 공용 컴포넌트 확인용. 어디에서도 링크하지 않는다.
      // import.meta.env.DEV는 프로덕션 빌드에서 false로 치환되므로,
      // 이 경로와 DevComponentsScreen은 배포 번들에서 통째로 빠진다.
      ...(import.meta.env.DEV
        ? [{ path: '/dev/components', element: <DevComponentsScreen /> }]
        : []),
    ],
  },
])
