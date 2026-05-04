import { Navigate, Route, Routes } from 'react-router-dom'
import ProtectedRoute from './components/ProtectedRoute'
import { useAuth } from './auth/useAuth'
import CalendarPage from './pages/CalendarPage'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import StoreManagementPage from './pages/StoreManagementPage'

function App() {
  const { isAuthenticated } = useAuth()

  return (
    <Routes>
      <Route
        path="/"
        element={isAuthenticated ? <Navigate to="/schedule" replace /> : <LandingPage />}
      />

      <Route
        path="/login"
        element={isAuthenticated ? <Navigate to="/schedule" replace /> : <LoginPage />}
      />
      <Route
        path="/register"
        element={isAuthenticated ? <Navigate to="/schedule" replace /> : <RegisterPage />}
      />

      <Route element={<ProtectedRoute />}>
        <Route path="/schedule" element={<CalendarPage />} />
        <Route path="/admin/stores" element={<StoreManagementPage />} />
      </Route>

      <Route
        path="*"
        element={<Navigate to={isAuthenticated ? '/schedule' : '/'} replace />}
      />
    </Routes>
  )
}

export default App
