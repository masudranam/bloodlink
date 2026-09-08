import { useEffect } from 'react'
import { useAuth } from './auth/authContext'
import { useRouter } from './routing/routerContext'
import Layout from './components/Layout'
import LoginScreen from './screens/LoginScreen'
import RegisterScreen from './screens/RegisterScreen'
import FeedScreen from './screens/FeedScreen'
import NewRequestScreen from './screens/NewRequestScreen'
import MyRequestsScreen from './screens/MyRequestsScreen'
import RequestScreen from './screens/RequestScreen'
import MyPledgesScreen from './screens/MyPledgesScreen'
import ProfileScreen from './screens/ProfileScreen'
import RevealsScreen from './screens/RevealsScreen'
import NotFoundScreen from './screens/NotFoundScreen'
import WrongRoleScreen from './screens/WrongRoleScreen'

/**
 * The route guard, and the switch.
 *
 * Three decisions live here and nowhere else:
 *
 * <ul>
 *   <li>A protected route with no session goes to the login screen carrying
 *       `?next=`, so the person continues to what they asked for after signing
 *       in rather than landing on the feed.</li>
 *   <li>A route belonging to the other role renders a plain explanation instead
 *       of firing a request that could only come back 403.</li>
 *   <li>A signed-in person on the login screen is sent to the feed, so the back
 *       button after signing in does not show a login form.</li>
 * </ul>
 */
export default function App() {
  const { session } = useAuth()
  const { match, path, navigate } = useRouter()
  const isPublic = match.role === 'public'

  useEffect(() => {
    if (!session && !isPublic && match.screen !== 'notFound') {
      navigate(`/login?next=${encodeURIComponent(path)}`, { replace: true })
    }
    if (session && isPublic) {
      navigate('/', { replace: true })
    }
  }, [session, isPublic, match.screen, path, navigate])

  if (match.screen === 'notFound') {
    // Before the session check: a mistyped URL is a mistyped URL whether or not
    // anybody is signed in, and answering it with a login form would be a
    // confusing way to say "no such page".
    return (
      <Layout>
        <NotFoundScreen />
      </Layout>
    )
  }

  if (!session) {
    // Anything protected is a redirect in flight; showing the login form rather
    // than a flash of an empty screen is the friendlier wait.
    return <Layout>{match.screen === 'register' ? <RegisterScreen /> : <LoginScreen />}</Layout>
  }

  if (match.role && match.role !== 'public' && match.role !== session.role) {
    return (
      <Layout>
        <WrongRoleScreen needed={match.role} actual={session.role} />
      </Layout>
    )
  }

  return (
    <Layout>
      <Screen />
    </Layout>
  )
}

function Screen() {
  const { match } = useRouter()

  switch (match.screen) {
    case 'feed':
      return <FeedScreen />
    case 'newRequest':
      return <NewRequestScreen />
    case 'myRequests':
      return <MyRequestsScreen />
    case 'request': {
      const id = Number(match.params.id)
      return Number.isFinite(id) && id > 0 ? <RequestScreen id={id} /> : <NotFoundScreen />
    }
    case 'myPledges':
      return <MyPledgesScreen />
    case 'profile':
      return <ProfileScreen />
    case 'reveals':
      return <RevealsScreen />
    case 'login':
      return <LoginScreen />
    case 'register':
      return <RegisterScreen />
    default:
      return <NotFoundScreen />
  }
}
