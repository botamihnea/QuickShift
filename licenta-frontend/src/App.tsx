import { Navigate, Route, Routes } from 'react-router-dom'
import ProtectedRoute from './components/ProtectedRoute'
import { useAuth } from './auth/useAuth'
import CalendarPage from './pages/CalendarPage'
import EmployeesPage from './pages/EmployeesPage'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import MyShiftsPage from './pages/MyShiftsPage'
import NotificationsPage from './pages/NotificationsPage'
import RegisterPage from './pages/RegisterPage'
import RequestDemoPage from './pages/RequestDemoPage'
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
      <Route
        path="/demo"
        element={isAuthenticated ? <Navigate to="/schedule" replace /> : <RequestDemoPage />}
      />

      <Route element={<ProtectedRoute />}>
        <Route path="/schedule" element={<CalendarPage />} />
        <Route path="/admin/stores" element={<StoreManagementPage />} />
        <Route path="/employees" element={<EmployeesPage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route path="/my-shifts" element={<MyShiftsPage />} />
      </Route>

      <Route
        path="*"
        element={<Navigate to={isAuthenticated ? '/schedule' : '/'} replace />}
      />
    </Routes>
  )
}

export default App
